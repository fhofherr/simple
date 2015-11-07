(ns fhofherr.simple.engine
  "FIXME: Add documentation.")

(def config-ns-name 'fhofherr.simple.projectdef)

;; TODO split into sub-namespaces

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Context
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initial-context
  "Initial context of a job execution."
  [project-dir]
  {:project-dir project-dir})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Status model
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn failed?
  "Check if the given context `ctx` is marked as failed. Return the reason
  for failure, or `nil` if the context is not failed."
  [ctx]
  (= :failed (:status ctx)))

(defn fail
  "Mark the context `ctx` as failed."
  [ctx]
  (assoc ctx :status :failed))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Jobs
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn simple-ci-job?
  "Check if the given object is a Simple CI job."
  [obj]
  (boolean (:ci-job? (meta obj))))

(defn- apply-step
  [f ignore-failure? ctx]
  (if (and f
           (or ignore-failure? (not (failed? ctx))))
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

(defn load-config
  "Load the Simple CI configuration file located under the given `path`.
  Returns the namespace into which the configration was loaded."
  [path]
  (when (find-ns config-ns-name)
    (remove-ns config-ns-name))
  (binding [*ns* (find-ns 'fhofherr.simple.dsl)]
    (load-file path))
  (find-ns config-ns-name))

; TODO: rename to `find-job-vars`?
(defn find-ci-jobs
  "Find Simple CI jobs in the `cidef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns the private vars of all found Simple CI jobs as a
  lazy sequence."
  [cidef-ns]
  (->> cidef-ns
       (ns-publics)
       (map second)
       (filter #(:ci-job? (meta (var-get %))))))

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
  {:job-var job-var
   :job-fn (var-get job-var)
   :executions (ref [] :validator vector?)
   :executor (agent -1)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Scheduling
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-job-execution!
  "Creates a new execution for the job represented by `job-desc` and
  appends it to the job descriptors `:executions` vector. Uses `ctx` as the
  job execution's initial context.

  Returns a tuple `[exec-id exec]` where `exec-id` is the id of the execution
  with respect to the job descriptor's `:executions` vector and `exec` is
  the execution itself.

  The newly created job execution will have its `:context`'s `:status` set to
  `:created`."
  [job-desc ctx]
  (let [exec {:context (assoc ctx :status :created)}
        exec-id (dosync
                  (as-> (:executions job-desc) $
                    (alter $ conj exec)
                    (count $)
                    (- $ 1)))]
    [exec-id exec]))

(defn get-job-execution
  [job-desc exec-id]
  (nth @(:executions job-desc) exec-id))

(defn get-last-executed
  [job-desc]
  (get-job-execution job-desc @(:executor job-desc)))

(defn update-job-execution!
  [job-desc exec-id f & args]
  (dosync
    (as-> (:executions job-desc) execs
      (alter execs update-in [exec-id] #(apply f % args))))
  job-desc)

(defn job-execution-status
  [exec]
  (get-in exec [:context :status]))

(defn execute-job!
  [job-desc exec-id]
  (letfn [(mark-failed [exec] (update-in exec [:context] fail))
          (apply-job-fn [job-fn exec] (job-fn (:context exec)))]
    (update-job-execution! job-desc exec-id assoc-in [:context :status] :executing)
    (try
      (let [exec (get-job-execution job-desc exec-id)
            new-ctx (apply-job-fn (:job-fn job-desc) exec)]
        (update-job-execution! job-desc exec-id #(assoc % :context new-ctx)))
      (catch Exception e
        (update-job-execution! job-desc exec-id mark-failed)))))

(defn schedule-job-execution!
  "Schedules the job execution identified by `exec-id` by sending it
  to the job descriptor's `:executor` using `send-off`.

  Returns the otherwise unchaged `job-desc` passed into the function."
  [job-desc exec-id]
  (letfn [(mark-queued [exec]
            (assoc-in exec [:context :status] :queued))
          (do-execute [last-exec-id]
            {:pre [(< last-exec-id exec-id)]}
            (execute-job! job-desc exec-id)
            exec-id)]
    (dosync
      (update-job-execution! job-desc exec-id mark-queued)
      ;; TODO failed agents
      (send-off (:executor job-desc) do-execute))
    job-desc))
