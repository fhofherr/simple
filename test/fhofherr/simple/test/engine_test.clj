(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine]))

(deftest execution-context

  (testing "the initial context contains the project directory"
    (let [project-dir "path/to/some/directory"]
      (is (= project-dir (:project-dir (engine/initial-context project-dir))))))

  (testing "the initial context is never failed"
    (is (not (engine/failed? (engine/initial-context ".")))))

  (testing "the context can be marked as failed"
    (is (engine/failed? (engine/fail (engine/initial-context ".") "Failed")))))

(defn- register-execution
  [marker]
  (fn [ctx]
    (update-in ctx [:payload :executions] #(conj (or % []) marker))))

(deftest make-job

  (testing "ci jobs have :ci-job? in their meta data"
    (let [job (engine/make-job {:test identity})]
      (is (:ci-job? (meta job)))
      (is (engine/simple-ci-job? job))))

  (testing "ci jobs are functions of a job context"
    (let [job (engine/make-job {:test (register-execution :job)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:job] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:before` function before the `:test`"
    (let [job (engine/make-job {:before (register-execution :before)
                                :test (register-execution :test)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :test] (get-in new-ctx [:payload :executions])))))

  (testing "the `:test` is not executed if `:before` fails the job"
    (let [job (engine/make-job {:before (comp
                                          (register-execution :before)
                                          #(engine/fail % "Before fails the job"))
                                :test (register-execution :test)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:after` function after the `:test`"
    (let [job (engine/make-job {:test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:test :after] (get-in new-ctx [:payload :executions])))))

  (testing "`:after` is executed even upon failure"
    (let [job (engine/make-job {:before (comp
                                          (register-execution :before)
                                          #(engine/fail % "Before fails the job"))
                                :test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :after] (get-in new-ctx [:payload :executions]))))

    (let [job (engine/make-job {:before (register-execution :before)
                                :test (comp
                                        (register-execution :test)
                                        #(engine/fail % "Test fails the job"))
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :test :after] (get-in new-ctx [:payload :executions]))))))

(def first-job (engine/make-job {:test identity}))

(def second-job (engine/make-job {:test identity}))

(deftest find-ci-jobs

  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (engine/find-ci-jobs (find-ns 'fhofherr.simple.test.engine-test))]
      (is (= #{#'first-job #'second-job} (set found-jobs))))))
