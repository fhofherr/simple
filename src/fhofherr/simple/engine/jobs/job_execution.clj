(ns fhofherr.simple.engine.jobs.job-execution
  (:require [fhofherr.simple.engine.status-model :as sm]
            [fhofherr.simple.engine.jobs.job-execution-context :as ex-ctx]))

(def ^:private possible-state-transitions {::created #{::queued}
                                           ::queued #{::executing}
                                           ::executing #{::finished}})

(def ^:private available-states (-> possible-state-transitions
                                    (keys)
                                    (set)))

(defrecord JobExecution [state context]

  sm/Stateful
  (current-state [^JobExecution this] (:state this))

  (state-valid? [^JobExecution this s] (contains? available-states s))

  (force-state [^JobExecution this s]
    {:pre [(sm/state-valid? this s)]}
    (assoc this :state s))

  (transition-possible? [^JobExecution this s]
    {:pre [(sm/state-valid? this s)]}
    (as-> this $
      (sm/current-state $)
      (get possible-state-transitions $)
      (contains? $ s))))

(defn job-execution?
  "Check if `obj` is a job execution"
  [obj]
  (instance? JobExecution obj))

(defn make-job-execution
  [context]
  (map->JobExecution {:context context
                      :state ::created}))
(defn created?
  [^JobExecution job-exec]
  (= ::created (sm/current-state job-exec)))

(defn mark-queued
  [^JobExecution job-exec]
  (sm/transition-to-state job-exec ::queued))

(defn queued?
  [^JobExecution job-exec]
  (= ::queued (sm/current-state job-exec)))

(defn mark-executing
  [^JobExecution job-exec]
  (as-> job-exec $
    (update-in $ [:context] ex-ctx/mark-executing)
    (sm/transition-to-state $ ::executing)))

(defn executing?
  [^JobExecution job-exec]
  (= ::executing (sm/current-state job-exec)))

(defn mark-finished
  [^JobExecution job-exec]
  (letfn [(update-ctx [ctx] (if (ex-ctx/failed? ctx)
                              ctx
                              (ex-ctx/mark-successful ctx)))]
    (as-> job-exec $
     (update-in $ [:context] update-ctx)
     (sm/transition-to-state $ ::finished))))

(defn finished?
  [^JobExecution job-exec]
  (= ::finished (sm/current-state job-exec)))
