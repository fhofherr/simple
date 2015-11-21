(ns fhofherr.simple.test.engine.jobs.execution-context-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine.jobs.execution-context :as ex-ctx]))

(deftest execution-context

  (testing "the initial context contains the project directory"
    (let [project-dir "path/to/some/directory"]
      (is (= project-dir (:project-dir (ex-ctx/initial-context project-dir))))))

  (testing "the initial context is never failed"
    (is (not (ex-ctx/failed? (ex-ctx/initial-context ".")))))

  (testing "the context can be marked as failed"
    (is (ex-ctx/failed? (ex-ctx/mark-failed (ex-ctx/initial-context "."))))))
