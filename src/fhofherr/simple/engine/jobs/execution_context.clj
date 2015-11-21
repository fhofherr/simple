(ns fhofherr.simple.engine.jobs.execution-context
  (:require [fhofherr.simple.engine.status-model :as sm]))

(def ^:private state-transitions {:created #{:executing}
                                  :executing #{:failed :successful}
                                  :successful #{}
                                  :failed #{}})

(def available-states (-> state-transitions
                          (keys)
                          (set)))

(defrecord JobExecutionContext [project-dir state]

  sm/Stateful
  (current-state [^JobExecutionContext this] (:state this))
  (state-valid? [^JobExecutionContext this s] (contains? available-states s))
  (force-state [^JobExecutionContext this s] (assoc this :state s))
  (transition-possible? [this next-state]
    {:pre [(sm/state-valid? this next-state)]}
    (as-> (sm/current-state this) $
      (get state-transitions $)
      (contains? $ next-state)))

  sm/StatusModel
  (created? [this] (= :created (:state this)))
  (queued? [this] (= :queued (:state this)))
  (executing? [this] (= :executing (:state this)))
  (successful? [this] (= :successful (:state this)))
  (failed? [this] (= :failed (:state this)))

  sm/ChangeableStatusModel
  (mark-created [this] (assoc this :state :created))
  (mark-queued [this] (assoc this :state :queued))
  (mark-executing [this] (assoc this :state :executing))
  (mark-successful [this] (assoc this :state :successful))
  (mark-failed [this] (assoc this :state :failed)))

(defn make-job-execution-context
  "Initial context of a job execution."
  [project-dir]
  (map->JobExecutionContext {:project-dir project-dir
                             :state :created}))

(defn mark-executing
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx :executing))

(defn mark-successful
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx :successful))

(defn successful?
  [^JobExecutionContext ex-ctx]
  (= :successful (sm/current-state ex-ctx)))

(defn failed?
  [^JobExecutionContext ex-ctx]
  (= :failed (sm/current-state ex-ctx)))

(defn mark-failed
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx :failed))
