(ns fhofherr.simple.integration-test.main-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.main :as main]))

(deftest ^:integration execute-shell-scripts

  (testing "Requires project directory"
    (is (thrown? AssertionError (main/run nil)))
    (is (thrown? AssertionError (main/run "")))
    (is (thrown? AssertionError (main/run "examples/the-missing-test"))))

  (testing "Successful shell scripts"
    (let [out (with-out-str
                (main/run "examples/successful-shell-script-test"))]
      (is (= "Tests successful!\n" out))))

  (testing "Failing shell scripts"
    (let [out (with-out-str
                (main/run "examples/failing-shell-script-test"))]
      (is (= "Tests failed!\n" out)))))
