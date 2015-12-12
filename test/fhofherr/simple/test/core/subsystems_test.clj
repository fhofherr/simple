(ns fhofherr.simple.test.core.subsystems-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core.subsystems :as subsystems]))

(deftest register-subystem
  (let [subsystem :subsystem
        core {}]

    (testing "register a subsystem without start or stop logic"
      (let [next-core (subsystems/register-subsystem core :subsystem-name subsystem)]
        (is (= subsystem (subsystems/get-subsystem next-core :subsystem-name)))))

    (testing "a subsystem may have a start function"
      (let [fn-called (atom false)
            start-fn (fn [c] (reset! fn-called true) c)
            next-core (subsystems/register-subsystem core
                                                     :subsystem-name
                                                     subsystem
                                                     :start start-fn)]
        (subsystems/start next-core)
        (is (true? @fn-called))))

    (testing "a subsystem may have a stop function"
      (let [fn-called (atom false)
            stop-fn (fn [c] (reset! fn-called true) c)
            next-core (subsystems/register-subsystem core
                                                     :subsystem-name
                                                     subsystem
                                                     :stop stop-fn)]
        (subsystems/stop next-core)
        (is (true? @fn-called))))))

(deftest subsystem-startup-and-shutdown
  (let [subsystem :subsystem
        core {}
        fn-calls (atom [])
        mk-fn (fn [kw]
                (fn [c]
                  (swap! fn-calls conj kw)
                  c))]

    (testing "subsystems are started in the order they were added"
      (reset! fn-calls [])
      (let [next-core (-> core
                          (subsystems/register-subsystem :first
                                                         subsystem
                                                         :start
                                                         (mk-fn :first))
                          (subsystems/register-subsystem :second
                                                         subsystem
                                                         :start
                                                         (mk-fn :second)))]
        (subsystems/start next-core)
        (is (= [:first :second] @fn-calls))))

    (testing "subsystems are stopped in the reverse order they were added"
      (reset! fn-calls [])
      (let [next-core (-> core
                          (subsystems/register-subsystem :first
                                                         subsystem
                                                         :stop
                                                         (mk-fn :first))
                          (subsystems/register-subsystem :second
                                                         subsystem
                                                         :stop
                                                         (mk-fn :second)))]
        (subsystems/stop next-core)
        (is (= [:second :first] @fn-calls))))))
