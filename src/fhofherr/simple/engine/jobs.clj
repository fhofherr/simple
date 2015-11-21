(ns fhofherr.simple.engine.jobs
  (:require [clojure.tools.logging :as log]
            [fhofherr.simple.engine.status-model :as sm]
            [fhofherr.simple.engine.jobs.execution-context :refer :all]
            [fhofherr.simple.engine.jobs.job-execution :as job-ex]))

(defn simple-ci-job?
  "Check if the given object is a Simple CI job."
  [obj]
  (boolean (:ci-job? (meta obj))))

;; TODO inline?
(defn- apply-step
  [f ignore-failure? ctx]
  (if (and f
           (or ignore-failure? (not (sm/failed? ctx))))
    (f ctx)
    ctx))

(defn make-job
  "Create a Simple CI job from a job definition. A job definition is a map
  that contains one or more of the following keys:

  * `:test`: the actual test to execute. The value for `:test` has to be a
    function of one argument, which is used as the current test context. The
    test function has to return a possibly modified test context.

  * `:before`: set up code to execute before the `:test`. The same rules
    as for the `:test` function apply. If `:before` fails `:test` and `:after`
    will not be executed.

  * `:after`: tear down code to execute after the `:test`. The same rules
    as for the `:test` function apply. The `:after` function will be executed
    even if `:before` or `:test` fail.

  Simple CI jobs themselves are functions of one argument. They expect
  the initial job context as their only argument and pass it down to the
  functions used in the job definition. They return the job context modified
  by the job's functions."
  [{:keys [test before after]}]
  (letfn [(job [ctx]
            (->> ctx
                 (apply-step before false)
                 (apply-step test false)
                 (apply-step after true)))]
    (with-meta job {:ci-job? true})))

(defrecord JobDescriptor [job-var job-fn executions executor])

(defn make-job-descriptor
  "Create a new job descriptor for the `job-var`. The returned job descriptor
  has the following keys:

  * `:job-var`: the `job-var` passed into the function.
  * `:job-fn`: the function the `job-var` points to.
  * `:executions`: a ref containing a vector of all known job executions.
    The oldest execution comes first in the vector. The youngest execution
    comes last. See [[make-job-execution]] for details about job executions.
    Initially empty.
  * `:executor`: an agent that asynchronously executes the job. The agent's
    value is the id of the last executed job execution and can be used to
    retrieve the execution from the `:executions` vector. See
    [[schedule-job-execution]] for details about executing jobs. Initially set
    to -1.

  Using an agent as `:executor` ensures that at each point in time only one
  instance of the job represented by this job descriptor can be executed."
  [job-var]
  {:pre [(simple-ci-job? (var-get job-var))]}
  (-> {:job-var job-var
       :job-fn (var-get job-var)
       :executions (ref [] :validator vector?)
       :executor (agent -1)}
      (map->JobDescriptor)))

;; TODO: rename to add-job-execution! and make private
(defn make-job-execution!
  "Creates a new execution for the job represented by `job-desc` and
  appends it to the job descriptors `:executions` vector. Uses `ctx` as the
  job execution's initial context.

  Returns a tuple `[exec-id exec]` where `exec-id` is the id of the execution
  with respect to the job descriptor's `:executions` vector and `exec` is
  the execution itself.

  The newly created job execution will have its status set to created."
  [job-desc ctx]
  (let [exec (job-ex/make-job-execution ctx)
        exec-id (dosync
                  (as-> (:executions job-desc) $
                    (alter $ conj exec)
                    (count $)
                    (- $ 1)))]
    [exec-id exec]))

(defn- update-job-execution!
  [job-desc exec-id f & args]
  (dosync
    (as-> (:executions job-desc) execs
      (alter execs update-in [exec-id] #(apply f % args))))
  job-desc)

(defn- update-context!
  [job-desc exec-id f & args]
  (update-job-execution! job-desc exec-id #(as-> (:context %) $
                                             (apply f $ args))))

(defn get-job-execution
  "Obtain the job execution with id `exec-id` from the job descriptor
  `job-desc`. Return `nil` if no execution with `exec-id` exists."
  [job-desc exec-id]
  (get @(:executions job-desc) exec-id))

(defn get-last-execution
  "Obtain the last executed job execution from `job-desc`. Return `nil`
  if no job has been executed yet."
  [job-desc]
  (get-job-execution job-desc @(:executor job-desc)))

(defn execute-job!
  "Synchronously execute the job execution with id `exec-id`. See
  [[schedule-job!]] for asynchronous job execution. "
  [job-desc exec-id]
  (letfn [(apply-job-fn [job-fn exec] (io! (job-fn (:context exec))))]
    (log/info "Starting execution" exec-id "of job" (:job-var job-desc))
    (update-job-execution! job-desc exec-id sm/mark-executing)
    (try
      (let [exec (get-job-execution job-desc exec-id)
            ;; Do not apply the job fn within a transaction (e.g. by using
            ;; update-context!). This would re-execute the tests if commiting
            ;; the transaction fails.
            new-ctx (apply-job-fn (:job-fn job-desc) exec)]
        ;; Ignore the old context and return the new-ctx.
        (update-context! job-desc exec-id (fn [_] new-ctx)))
      (log/info "Finished execution" exec-id "of job" (:job-var job-desc))
      (catch Throwable t
        (log/warn t
                  "Exception occured during execution"
                  exec-id
                  "of job"
                  (:job-var job-desc)
                  "! Marking job as failed.")
        (update-job-execution! job-desc exec-id sm/mark-failed)))))

(defn schedule-job-execution!
  "Schedules the job execution identified by `exec-id` by sending it
  to the job descriptor's `:executor` using `send-off`.

  Returns the otherwise unchaged `job-desc` passed into the function."
  [job-desc exec-id]
  (letfn [(do-execute [last-exec-id]
            {:pre [(< last-exec-id exec-id)]}
            (execute-job! job-desc exec-id)
            exec-id)]
    (log/info "Queueing execution" exec-id "of job" (:job-var job-desc))
    (dosync
      (update-job-execution! job-desc exec-id sm/mark-queued)
      ;; execute-job! catches any Throwable thrown by the job and does not
      ;; rethrow it. The executor should thus never fail under normal
      ;; conditions.
      (send-off (:executor job-desc) do-execute))
    job-desc))

(defn schedule-job!
  "Create a new job execution using [[make-job-execution!]] and immediately
  schedule it using [[schedule-job-execution!]]. Return the job execution's
  id."
  [job-desc initial-ctx]
  (let [[exec-id _] (make-job-execution! job-desc initial-ctx)]
    (schedule-job-execution! job-desc exec-id)
    exec-id))

(defn- apply-to-last-execution
  "Apply the function `f` to the last job execution of `job-desc`. Return
  whatever `f` returns. If `job-desc` has no executions and `else` is given
  return `else`. If `else` is not given and there are no job executions for
  `job-desc` return `nil`."
  [job-desc f & [else]]
  (if-let [last-exec (get-last-execution job-desc)]
    (f last-exec)
    else))

(defn- apply-to-new-executions
  "Apply the function `f` to a vector of all of `job-desc`'s job executions
  whose id is higher than the id of the last executed job execution. The vector
  passed to `f` may be empty."
  [job-desc f]
  (let [execs @(:executions job-desc)
        start-id @(:executor job-desc)]
    (as-> execs $
      (subvec $ (if (< start-id 0) 0 start-id))
      (f $))))

(extend-type JobDescriptor
  sm/StatusModel
  (created? [this] (and (empty? @(:executions this))
                        (< @(:executor this) 0)))
  (queued? [this] (apply-to-new-executions this #(some sm/queued? %)))
  (executing? [this] (apply-to-new-executions this #(some sm/executing? %)))
  (successful? [this] (apply-to-last-execution this sm/successful?))
  (failed? [this] (apply-to-last-execution this sm/failed?)))
