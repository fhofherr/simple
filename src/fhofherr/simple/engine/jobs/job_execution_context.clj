(ns fhofherr.simple.engine.jobs.job-execution-context
  (:require [fhofherr.simple.engine.status-model :as sm]))

(def ^:private state-transitions {::created #{::executing}
                                  ::executing #{::failed ::successful}
                                  ::successful #{}
                                  ::failed #{}})

(def ^:private available-states (-> state-transitions
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
      (contains? $ next-state))))

(defn job-execution-context?
  "Check if the given object `obj` is a job execution context."
  [obj]
  (instance? JobExecutionContext obj))

(defn make-job-execution-context
  "Initial context of a job execution."
  [project-dir]
  (map->JobExecutionContext {:project-dir project-dir
                             :state ::created}))

(defn created?
  [^JobExecutionContext ex-ctx]
  (= ::created (sm/current-state ex-ctx)))

(defn mark-executing
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx ::executing))

(defn executing?
  [^JobExecutionContext ex-ctx]
  (= ::executing (sm/current-state ex-ctx)))

(defn mark-successful
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx ::successful))

(defn successful?
  [^JobExecutionContext ex-ctx]
  (= ::successful (sm/current-state ex-ctx)))

(defn failed?
  [^JobExecutionContext ex-ctx]
  (= ::failed (sm/current-state ex-ctx)))

(defn mark-failed
  [^JobExecutionContext ex-ctx]
  (sm/transition-to-state ex-ctx ::failed))
