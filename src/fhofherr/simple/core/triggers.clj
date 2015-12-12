(ns fhofherr.simple.core.triggers
  (:require [fhofherr.simple.core [timer-service :as timer-service]]))

(defmulti register-trigger
  "Register a trigger with the `core`.

  The trigger is configured via the `trigger-cfg` map. The contents of the
  `trigger-cfg` depend mostly on the type of trigger. However, it is required
  that `trigger-cfg` contains a key `:type` which is used as the dispatch value
  for `register-trigger`.

  `f` is a function of no arguments which is executed as the trigger's task."
  {:arglists '([core trigger-cfg f])}
  (fn [_ trigger-cfg _] (:type trigger-cfg)))

(defmethod register-trigger :timer
  [core trigger-cfg f]
  {:pre [(:timer-service core)
         (:name trigger-cfg)
         (:args trigger-cfg)
         (fn? f)]}
  (update-in core
             [:timer-service]
             #(apply timer-service/add-task
                     %
                     (:name trigger-cfg)
                     f
                     (:args trigger-cfg))))
