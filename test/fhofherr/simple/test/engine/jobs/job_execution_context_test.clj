(ns fhofherr.simple.test.engine.jobs.job-execution-context-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine.status-model :as sm]
            [fhofherr.simple.engine.jobs.job-execution-context :as ex-ctx]))

(deftest job-execution-context

  (let [project-dir "path/to/some/directory"
        context (ex-ctx/make-job-execution-context project-dir)]

    (testing "the initial context contains the project directory"
      (is (= project-dir (:project-dir context))))

    (testing "the initial context has a state of created"
      (is (ex-ctx/created? context)))

    (testing "a newly created context can be marked as :executing"
      (is (ex-ctx/executing? (-> context
                                 (ex-ctx/mark-executing))))

      (is (thrown? clojure.lang.ExceptionInfo
                   (ex-ctx/mark-failed context)))

      (is (thrown? clojure.lang.ExceptionInfo
                   (ex-ctx/mark-successful context))))

    (testing "a executing context can transition to :successful or :failed"
      (let [executing-context (ex-ctx/mark-executing context)]
        (is (ex-ctx/successful? (ex-ctx/mark-successful executing-context)))
        (is (ex-ctx/failed? (ex-ctx/mark-failed executing-context)))))

    (testing "the failed state is terminal"
      (let [failed-context (-> context
                               (ex-ctx/mark-executing)
                               (ex-ctx/mark-failed))]

        (is (thrown? clojure.lang.ExceptionInfo
                     (ex-ctx/mark-successful failed-context)))

        (is (thrown? clojure.lang.ExceptionInfo
                     (ex-ctx/mark-executing failed-context)))))

    (testing "the successful state is terminal"
      (let [successful-context (-> context
                                   (ex-ctx/mark-executing)
                                   (ex-ctx/mark-successful))]

        (is (thrown? clojure.lang.ExceptionInfo
                     (ex-ctx/mark-failed successful-context)))

        (is (thrown? clojure.lang.ExceptionInfo
                     (ex-ctx/mark-executing successful-context)))))))
