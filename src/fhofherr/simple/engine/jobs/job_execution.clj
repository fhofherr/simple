(ns fhofherr.simple.engine.jobs.job-execution
  (:require [fhofherr.simple.engine.status-model :as sm]))

(defrecord JobExecution [context]

  sm/StatusModel
  (created? [this] (sm/created? (:context this)))
  (queued? [this] (sm/queued? (:context this)))
  (executing? [this] (sm/executing? (:context this)))
  (successful? [this] (sm/successful? (:context this)))
  (failed? [this] (sm/failed? (:context this)))

  ;; TODO this is convenient, but should it exist?
  sm/ChangeableStatusModel
  (mark-created [this] (update-in this [:context] sm/mark-created))
  (mark-queued [this] (update-in this [:context] sm/mark-queued))
  (mark-executing [this] (update-in this [:context] sm/mark-executing))
  (mark-successful [this] (update-in this [:context] sm/mark-successful))
  (mark-failed [this] (update-in this [:context] sm/mark-failed)))
