(ns fhofherr.simple.test.engine.jobs.job-fn-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine.jobs [execution-context :as ex-ctx]
                                         [job-fn :as job-fn]]))

(defn- register-execution
  [marker]
  (fn [ctx]
    (update-in ctx [:payload :executions] #(conj (or % []) marker))))

(deftest make-job

  (testing "ci jobs have :ci-job? in their meta data"
    (let [job (job-fn/make-job {:test identity})]
      (is (:ci-job? (meta job)))
      (is (job-fn/job-fn? job))))

  (testing "ci jobs are functions of a job context"
    (let [job (job-fn/make-job {:test (register-execution :job)})
          new-ctx (job (ex-ctx/make-job-execution-context "."))]
      (is (= [:job] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:before` function before the `:test`"
    (let [job (job-fn/make-job {:before (register-execution :before)
                                :test (register-execution :test)})
          new-ctx (job (ex-ctx/make-job-execution-context "."))]
      (is (= [:before :test] (get-in new-ctx [:payload :executions])))))

  (testing "the `:test` is not executed if `:before` fails the job"
    (let [job (job-fn/make-job {:before (comp
                                        (register-execution :before)
                                        ex-ctx/mark-failed)
                              :test (register-execution :test)})
          init-ctx (-> "."
                       (ex-ctx/make-job-execution-context)
                       (ex-ctx/mark-executing))
          new-ctx (job init-ctx)]
      (is (= [:before] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:after` function after the `:test`"
    (let [job (job-fn/make-job {:test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (ex-ctx/make-job-execution-context "."))]
      (is (= [:test :after] (get-in new-ctx [:payload :executions])))))

  (testing "`:after` is executed even upon failure"
    (let [job (job-fn/make-job {:before (comp
                                          (register-execution :before)
                                          ex-ctx/mark-failed)
                                :test (register-execution :test)
                                :after (register-execution :after)})
          init-ctx (-> "."
                       (ex-ctx/make-job-execution-context)
                       (ex-ctx/mark-executing))
          new-ctx (job init-ctx)]
      (is (= [:before :after] (get-in new-ctx [:payload :executions]))))

    (let [job (job-fn/make-job {:before (register-execution :before)
                                :test (comp
                                        (register-execution :test)
                                        ex-ctx/mark-failed)
                                :after (register-execution :after)})

          init-ctx (-> "."
                       (ex-ctx/make-job-execution-context)
                       (ex-ctx/mark-executing))
          new-ctx (job init-ctx)]
      (is (= [:before :test :after] (get-in new-ctx [:payload :executions]))))))
