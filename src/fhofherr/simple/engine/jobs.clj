(ns fhofherr.simple.engine.jobs
  (:require [clojure.tools.logging :as log]
            [fhofherr.simple.engine.jobs [job-fn :as job-fn]
                                         [job-execution-context :as ex-ctx]
                                         [job-execution :as job-ex]]))

(defrecord JobDescriptor [job-var job-fn executions executor])

;; TODO pass job-fn instead of job-var (and maybe a job name)
(defn make-job-descriptor
  "Create a new job descriptor for the `job-var`. The returned job descriptor
  has the following keys:

  * `:job-var`: the `job-var` passed into the function.
  * `:job-fn`: the function the `job-var` points to.
  * `:executions`: a ref containing a vector of all known job executions.
    The oldest execution comes first in the vector. The youngest execution
    comes last. See [[add-job-execution!]] for details about job executions.
    Initially empty.
  * `:executor`: an agent that asynchronously executes the job. The agent's
    value is the id of the last executed job execution and can be used to
    retrieve the execution from the `:executions` vector. See
    [[schedule-job-execution]] for details about executing jobs. Initially set
    to -1.

  Using an agent as `:executor` ensures that at each point in time only one
  instance of the job represented by this job descriptor can be executed."
  [job-var]
  {:pre [(job-fn/job-fn? (var-get job-var))]}
  (-> {:job-var job-var
       :job-fn (var-get job-var)
       :executions (ref [] :validator vector?)
       :executor (agent -1)}
      (map->JobDescriptor)))

(defn add-job-execution!
  "Appends the job execution `exec` to the job descriptor `job-desc`'s
  `:executions` vector in a transaction.

  Returns the id of the execution with respect to the job descriptor's
  `:executions` vector."
  [job-desc exec]
  (let [exec-id (dosync
                  (as-> (:executions job-desc) $
                    (alter $ conj exec)
                    (count $)
                    (- $ 1)))]
    exec-id))

(defn get-job-execution
  "Obtain the job execution with id `exec-id` from the job descriptor
  `job-desc`. Return `nil` if no execution with `exec-id` exists."
  [job-desc exec-id]
  (get @(:executions job-desc) exec-id))

(defn alter-job-execution!
  "Apply the function `f` to the execution with id `exec-id` in the job
  descriptor's vector of executions. Replace the old value by the job
  execution returned by `f`. `f` is executed *outside* of a transaction
  and may have side-effects."
  [job-desc exec-id f & args]
  (let [apply-f (fn [exec]
                  {:post [(job-ex/job-execution? %)]}
                  (apply f exec args))
        new-exec (-> job-desc
                     (get-job-execution exec-id)
                     (apply-f))]
    (dosync
      (-> job-desc
          (:executions)
          (alter assoc exec-id new-exec))))
  job-desc)

(defn execute-job!
  "Synchronously execute the job execution with id `exec-id`. See
  [[schedule-job!]] for asynchronous job execution. "
  [job-desc exec-id]
  (let [job-fn #(io! ((:job-fn job-desc) %))]
    (log/info "Starting execution" exec-id "of job" (:job-var job-desc))
    (-> job-desc
      (alter-job-execution! exec-id job-ex/mark-executing)
      (alter-job-execution! exec-id job-ex/update-context job-fn)
      (alter-job-execution! exec-id job-ex/mark-finished))
    (log/info "Finished execution" exec-id "of job" (:job-var job-desc))))

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
    (alter-job-execution! job-desc exec-id job-ex/mark-queued)
    ;; Jobs catch any Throwable thrown by the job steps and do not
    ;; rethrow it. The executor should thus never fail under normal
    ;; conditions.
    (send-off (:executor job-desc) do-execute)
    job-desc))

;; TODO pass job execution instead of initial context
(defn schedule-job!
  "Create a new job execution using [[add-job-execution!]] and immediately
  schedule it using [[schedule-job-execution!]]. Return the job execution's
  id."
  [job-desc initial-ctx]
  (let [exec-id (add-job-execution! job-desc
                                    (job-ex/make-job-execution initial-ctx))]
    (schedule-job-execution! job-desc exec-id)
    exec-id))

(defn get-last-executed-execution
  "Obtain the last executed job execution from `job-desc`. Return `nil`
  if no job has been executed yet."
  [job-desc]
  (get-job-execution job-desc @(:executor job-desc)))

(defn- query-last-executed-execution
  [job-desc f & [else]]
  (if-let [last-exec (get-last-executed-execution job-desc)]
    (f last-exec)
    else))

(defn failed?
  "Check if the last executed job execution of `job-desc` failed."
  [job-desc]
  (query-last-executed-execution job-desc
                                 #(-> % (:context) (ex-ctx/failed?))))

(defn successful?
  "Check if the last executed job execution of `job-desc` was successful."
  [job-desc]
  (query-last-executed-execution job-desc #(-> % (:context) (ex-ctx/successful?))))
