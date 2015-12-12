(ns fhofherr.simple.test.core.triggers-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.core [triggers :as triggers]
             [timer-service :as timer-service]]))

(deftest timer-triggers
  (let [core {:timer-service (timer-service/make-timer-service)}
        trigger-cfg {:type :timer
                     :name "trigger-name"
                     :args [:every 5 :minutes]}
        f #(println "Hello World")]

    (testing "trigger registration"
      (let [core* (triggers/register-trigger core trigger-cfg f)]
        (is (= core core*))
        (is (true? (timer-service/has-task? (:timer-service core*)
                                           (:name trigger-cfg))))))))
