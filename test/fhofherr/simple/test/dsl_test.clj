(ns fhofherr.simple.test.dsl-test
  (:require [clojure.test :refer :all]
            [fhofherr.clj-io.files :as files]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine]))

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
          new-ctx ((dsl/execute script-path) (engine/initial-context path))]
      (is (not (engine/failed? new-ctx))))

    (let [script-path (copy-script path "failing-shell-script.sh")
          new-ctx ((dsl/execute script-path) (engine/initial-context path))]
      (is (engine/failed? new-ctx)))))

(dsl/defjob unit-test-job
  :test (fn [job-context] (assoc job-context :unit-test-job-executed true)))

(deftest define-a-ci-job 

  (testing "defines a Simple CI job"
    (is (engine/simple-ci-job? unit-test-job))))
