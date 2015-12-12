(ns fhofherr.simple.core.subsystems
  (:require [clojure.tools.logging :as log]))

(defn ^:dynamic *log-lifecycle-message*
  [msg]
  (log/info msg))

(defn- wrap-lifecycle-fn
  [msg f]
  (fn [core]
    {:pre [core]}
    (*log-lifecycle-message* msg)
    (f core)))

(defn register-subsystem
  [core subsystem-name subsystem & {:keys [start stop]
                                    :or {start identity stop identity}}]
  (-> core
      (assoc subsystem-name subsystem)
      (update-in [::start] conj (wrap-lifecycle-fn
                                 (format "Starting subsystem %s" subsystem-name)
                                 start))
      (update-in [::stop] conj (wrap-lifecycle-fn
                                (format "Stopping subsystem %s" subsystem-name)
                                stop))))

(defn get-subsystem
  [core subsystem-name]
  (get core subsystem-name))

(defn start
  [core]
  (let [start-fn (->> core
                      (::start)
                      (apply comp))]
    (start-fn core)))

(defn stop
  [core]
  (let [stop-fn (->> core
                     (::stop)
                     (reverse)
                     (apply comp))]
    (stop-fn core)))
