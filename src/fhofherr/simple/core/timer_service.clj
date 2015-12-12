(ns fhofherr.simple.core.timer-service
  (:require [clojure.tools.logging :as log])
  (:import [java.util.concurrent TimeUnit]))

(def ^:private time-units {:second TimeUnit/SECONDS
                           :seconds TimeUnit/SECONDS
                           :minute TimeUnit/MINUTES
                           :minutes TimeUnit/MINUTES
                           :hour TimeUnit/HOURS
                           :hours TimeUnit/HOURS})

(defn convert-to-milliseconds
  [amount time-unit]
  {:pre [(or (keyword? time-unit)
             (instance? TimeUnit time-unit))]}
  (when-not (or (instance? TimeUnit time-unit)
                (contains? time-units time-unit))
    (throw (IllegalArgumentException. (str "Unsupported time unit: "
                                           time-unit))))
  (as-> time-unit $
        (get time-units $ $)
        (.toMillis $ amount)))

(defn make-timer-service
  []
  (agent {::stopped true}))

(defn timer-service?
  [obj]
  (and (instance? clojure.lang.Agent obj)
       (contains? @obj ::stopped)))

(defn add-task
  [timer-service task-name task-action & [rep-type amount time-unit]]
  {:pre [(timer-service? timer-service) (= :every rep-type) amount time-unit]}
  (let [increment (convert-to-milliseconds amount time-unit)
        task {::last-execution -1
              ::next-execution (+ (System/currentTimeMillis)
                                  increment)
              ::increment increment
              ::action task-action}]
    (send timer-service assoc-in [::tasks task-name] task)))

(defn has-task?
  [timer-service task-name]
  {:pre [(timer-service? timer-service)]}
  (contains? (::tasks @timer-service) task-name))

(defn remove-task
  [timer-service task-name]
  {:pre [(timer-service? timer-service)]}
  (send timer-service update-in [::tasks] #(dissoc % task-name)))

(defn- task-due?
  [task current-time-ms]
  (< (::next-execution task) current-time-ms))

(defn- execute-if-due
  [task-name task current-time-ms]
  (if (task-due? task current-time-ms)
    (do
      (try
        (log/infof "Executing due task %s" task-name)
        ((::action task))
        (catch Throwable t
          (log/warnf t "Exception during execution of task '%s'" task-name)))
      (-> task
          (assoc ::last-execution current-time-ms)
          (assoc ::next-execution (+ current-time-ms (::increment task)))))
    task))

(defn- execute-due-tasks
  [tasks current-time-ms]
  (as-> tasks $
        (map (fn [[task-name task]]
               [task-name (execute-if-due task-name task current-time-ms)])
             $)
        (into {} $)))

(defn- send-off-repeatedly
  [timer-service f & args]
  (letfn [(execute-and-reschedule [service-state]
            (if-not (::stopped service-state)
              (do
                (.sleep TimeUnit/MILLISECONDS 500)
                (send-off timer-service execute-and-reschedule)
                (apply f service-state args))
              service-state))]
    (send-off timer-service execute-and-reschedule)))

(defn start-timer-service
  [timer-service]
  (-> timer-service
      (send assoc ::stopped false)
      (send-off-repeatedly update-in
                           [::tasks]
                           #(execute-due-tasks % (System/currentTimeMillis)))))

(defn stop-timer-service
  [timer-service]
  (send timer-service assoc ::stopped true))
