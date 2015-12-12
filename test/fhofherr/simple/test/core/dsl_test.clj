(ns fhofherr.simple.test.core.dsl-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files]
            [fhofherr.simple.core.dsl :as dsl]
            [fhofherr.simple.core [job-execution-context :as ex-ctx]
             [job-descriptor :as job-desc]]))

(defn- copy-script
  [path script-name]
  (-> (format "tests/%s" script-name)
      (files/copy-resource (.resolve path script-name))
      (files/chmod "rwx")
      (.getFileName)
      (str)))

(deftest execute-a-shell-script
  (files/with-tmp-dir
    [path]
    (let [script-path (copy-script path "successful-shell-script.sh")
          init-ctx (-> path
                       (ex-ctx/make-job-execution-context)
                       (ex-ctx/mark-executing))
          new-ctx ((dsl/execute script-path) init-ctx)]
      (is (not (ex-ctx/failed? new-ctx))))

    (let [script-path (copy-script path "failing-shell-script.sh")
          init-ctx (-> path
                       (ex-ctx/make-job-execution-context)
                       (ex-ctx/mark-executing))
          new-ctx ((dsl/execute script-path) init-ctx)]
      (is (ex-ctx/failed? new-ctx)))))

(dsl/defjob unit-test-job
  :test (fn [job-context] (assoc job-context :unit-test-job-executed true)))

(deftest define-a-ci-job

  (testing "defines a Simple CI job"
    (is (job-desc/job-descriptor? unit-test-job))))
