(ns fhofherr.simple.engine.status-model)

(defprotocol StatusModel
  "Query the different status available within Simple CI."
  (created? [o] "Check if the object `o` is in status created.")
  (queued? [o] "Check if the object `o` is in status queued.")
  (executing? [o] "Check if the object `o` is in status executing.")
  (successful? [o] "Check if the object `o` is in status successful.")
  (failed? [o] "Check if the object `o` is in status failed."))

(defprotocol ChangeableStatusModel
  "Update the different status available within Simple CI."
  (mark-created [o] "Mark the object `o` as created.")
  (mark-queued [o] "Mark the object `o` as queued.")
  (mark-executing [o] "Mark the object `o` as executing.")
  (mark-successful [o] "Mark the object `o` as successful.")
  (mark-failed [o] "Mark the object `o` as failed."))
