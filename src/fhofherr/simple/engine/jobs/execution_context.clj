(ns fhofherr.simple.engine.jobs.execution-context
  (:require [fhofherr.simple.engine.status-model :as sm]))

(defrecord JobExecutionContext [project-dir status]

  sm/StatusModel
  (created? [this] (= :created (:status this)))
  (queued? [this] (= :queued (:status this)))
  (executing? [this] (= :executing (:status this)))
  (successful? [this] (= :successful (:status this)))
  (failed? [this] (= :failed (:status this)))

  sm/ChangeableStatusModel
  (mark-created [this] (assoc this :status :created))
  (mark-queued [this] (assoc this :status :queued))
  (mark-executing [this] (assoc this :status :executing))
  (mark-successful [this] (assoc this :status :successful))
  (mark-failed [this] (assoc this :status :failed)))

;; TODO Rename to make-job-execution-context
(defn initial-context
  "Initial context of a job execution."
  [project-dir]
  (-> {:project-dir project-dir}
      (map->JobExecutionContext)
      (sm/mark-created)))

(defn failed?
  [^JobExecutionContext ex-ctx]
  (sm/failed? ex-ctx))

(defn mark-failed
  [^JobExecutionContext ex-ctx]
  (sm/mark-failed ex-ctx))
