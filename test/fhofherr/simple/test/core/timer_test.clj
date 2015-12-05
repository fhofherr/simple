(ns fhofherr.simple.test.core.timer-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core.timer :as timer])
  (:import [java.util.concurrent TimeUnit]))

(deftest task-registration-and-deregistration
  (let [timer-service (timer/make-timer-service)
        task-name :task-name
        task-action (fn [])]

    (testing "send a task to the timer service"
      (timer/add-task timer-service task-name task-action :every 1 :second)
      (await-for 200 timer-service)
      (is (true? (timer/has-task? timer-service task-name))))

    (testing "remove a task from the timer service"
      (timer/remove-task timer-service task-name)
      (await-for 200 timer-service)
      (is (false? (timer/has-task? timer-service task-name))))))

(deftest execute-task-periodically
  (let [timer-service (timer/make-timer-service)
        time-measurements (atom [(System/currentTimeMillis)])
        task-name :task-name
        task-action #(swap! time-measurements conj (System/currentTimeMillis))]
    (timer/add-task timer-service task-name task-action :every 1 :second)
    (timer/start-timer-service timer-service)
    (.sleep TimeUnit/SECONDS 1)
    (timer/stop-timer-service timer-service)
    (await-for 200 timer-service)
    (is (< 1000 (->> @time-measurements
                     (take 2)
                     (reverse)
                     (apply -))))))

(deftest dont-fail-timer-service-if-action-throws-exception
  (let [timer-service (timer/make-timer-service)
        task-name :task-name
        task-action #(throw (Throwable. "Bang!"))]
    (timer/add-task timer-service task-name task-action :every 1 :second)
    (timer/start-timer-service timer-service)
    (.sleep TimeUnit/SECONDS 1)
    (timer/stop-timer-service timer-service)
    (await-for 200 timer-service)
    (is (nil? (agent-error timer-service)))))

(deftest calculate-execution-time-increment
  (testing "seconds"
    (is (= 1000 (timer/convert-to-milliseconds 1 :second)))
    (is (= 1000 (timer/convert-to-milliseconds 1 :seconds)))
    (is (= 1000 (timer/convert-to-milliseconds 1 TimeUnit/SECONDS)))
    (is (= 2000 (timer/convert-to-milliseconds 2 :second)))
    (is (= 2000 (timer/convert-to-milliseconds 2 :seconds)))
    (is (= 2000 (timer/convert-to-milliseconds 2 TimeUnit/SECONDS))))

  (testing "minutes"
    (is (= (* 60 1000) (timer/convert-to-milliseconds 1 :minute)))
    (is (= (* 60 1000) (timer/convert-to-milliseconds 1 :minutes)))
    (is (= (* 60 1000) (timer/convert-to-milliseconds 1 TimeUnit/MINUTES)))
    (is (= (* 60 2000) (timer/convert-to-milliseconds 2 :minute)))
    (is (= (* 60 2000) (timer/convert-to-milliseconds 2 :minutes)))
    (is (= (* 60 2000) (timer/convert-to-milliseconds 2 TimeUnit/MINUTES))))

  (testing "hours"
    (is (= (* 60 60 1000) (timer/convert-to-milliseconds 1 :hour)))
    (is (= (* 60 60 1000) (timer/convert-to-milliseconds 1 :hours)))
    (is (= (* 60 60 1000) (timer/convert-to-milliseconds 1 TimeUnit/HOURS)))
    (is (= (* 60 60 2000) (timer/convert-to-milliseconds 2 :hour)))
    (is (= (* 60 60 2000) (timer/convert-to-milliseconds 2 :hours)))
    (is (= (* 60 60 2000) (timer/convert-to-milliseconds 2 TimeUnit/HOURS))))

  (testing "other keywords as units"
    (is (thrown? IllegalArgumentException
                 (timer/convert-to-milliseconds 2 :something)))))
