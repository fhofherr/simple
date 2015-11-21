(ns fhofherr.simple.test.engine.jobs.execution-context-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine.status-model :as sm]
            [fhofherr.simple.engine.jobs.execution-context :as ex-ctx]))

(deftest execution-context

  (let [project-dir "path/to/some/directory"
        context (ex-ctx/make-job-execution-context project-dir)]

    (testing "the initial context contains the project directory"
      (is (= project-dir (:project-dir context))))

    (testing "the initial context has a state of created"
      (is (= :created (sm/current-state context))))

    (testing "a newly created context can be marked as :executing"
      (is (= :executing (-> context
                            (ex-ctx/mark-executing)
                            (sm/current-state))))

      (doseq [s (disj ex-ctx/available-states :executing)]
        (is (thrown? clojure.lang.ExceptionInfo
                     (sm/transition-to-state context s)))))

    (testing "a executing context can transition to :successful or :failed"
      (let [executing-context (ex-ctx/mark-executing context)]
        (is (ex-ctx/successful? (ex-ctx/mark-successful executing-context)))
        (is (ex-ctx/failed? (ex-ctx/mark-failed executing-context)))

        (doseq [s (disj ex-ctx/available-states :successful :failed)]
          (is (thrown? clojure.lang.ExceptionInfo
                       (sm/transition-to-state executing-context s))))))

    (testing "the failed state is terminal"
      (let [failed-context (-> context
                               (ex-ctx/mark-executing)
                               (ex-ctx/mark-failed))]

        (doseq [s (disj ex-ctx/available-states :failed)]
          (is (thrown? clojure.lang.ExceptionInfo
                       (sm/transition-to-state failed-context s))))))

    (testing "the successful state is terminal"
      (let [successful-context (-> context
                                   (ex-ctx/mark-executing)
                                   (ex-ctx/mark-successful))]

        (doseq [s (disj ex-ctx/available-states :successful)]
          (is (thrown? clojure.lang.ExceptionInfo
                       (sm/transition-to-state successful-context s))))))))
