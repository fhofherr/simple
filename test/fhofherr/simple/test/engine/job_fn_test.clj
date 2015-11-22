(ns fhofherr.simple.test.engine.job-fn-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.engine [job-execution-context :as ex-ctx]
             [job-fn :as job-fn]]))

(defn- register-execution
  [marker]
  (fn [ctx]
    (update-in ctx [:payload :executions] #(conj (or % []) marker))))

(def initial-ctx (-> "."
                     (ex-ctx/make-job-execution-context)
                     (ex-ctx/mark-executing)))

(deftest make-job-step-fn

  (let [ex-log (atom nil)
        update-ex-log (fn [d ctx] (reset! ex-log {:desc d :ctx ctx}))]

    (let [desc "some description"
          step-fn (job-fn/make-job-step-fn desc (register-execution :step-fn))]

      (testing "a job step function satisfies the job-step-fn? predicate"
        (is (job-fn/job-step-fn? step-fn))
        (is (not (job-fn/job-step-fn? identity))))

      (testing "its description can be obtained using job-step-description"
        (is (= desc (job-fn/job-step-description step-fn))))

      (testing "step functions log their execution context after being executed"
        (reset! ex-log nil)
        (binding [job-fn/*log-step-execution* update-ex-log]
          (step-fn initial-ctx)
          (is (= desc (:desc @ex-log)))
          (is (= :step-fn (-> @ex-log
                              (get-in [:ctx :payload :executions])
                              (first))))))) (let [throwing-step-fn (job-fn/make-job-step-fn "throws exception"
                                                                                            (fn [_]
                                                                                              (throw (Throwable. "Kaboom, Baby!"))))]
                                              (testing "step functions mark the context failed if an exception occurs"
                                                (let [new-ctx (throwing-step-fn initial-ctx)]
                                                  (is (ex-ctx/failed? new-ctx))))

                                              (testing "they log their execution context if an execption occurs"
                                                (reset! ex-log nil)
                                                (binding [job-fn/*log-step-execution* update-ex-log]
                                                  (let [new-ctx (throwing-step-fn initial-ctx)]
                                                    (is (= new-ctx (:ctx @ex-log)))))))))

(deftest make-job-fn
  (testing "job functions require their steps to be job step functions"
    (is (thrown? IllegalArgumentException
                 (job-fn/make-job-fn {:test identity})))

    (is (thrown? IllegalArgumentException
                 (job-fn/make-job-fn {:before identity})))

    (is (thrown? IllegalArgumentException
                 (job-fn/make-job-fn {:after identity}))))

  (let [job (job-fn/make-job-fn {:test (job-fn/make-job-step-fn
                                        "some job"
                                        (register-execution :job))})
        not-a-job identity]

    (testing "job functions are marked as such"
      (is (job-fn/job-fn? job))
      (is (false? (job-fn/job-fn? not-a-job))))

    (testing "ci jobs are functions of a job context"
      (let [new-ctx (job initial-ctx)]
        (is (= [:job] (get-in new-ctx [:payload :executions]))))))

  (testing "job functions execute their `:before` step before the `:test`"
    (let [job (job-fn/make-job-fn
               {:before (job-fn/make-job-step-fn "before"
                                                 (register-execution :before))
                :test (job-fn/make-job-step-fn "test"
                                               (register-execution :test))})
          new-ctx (job initial-ctx)]
      (is (= [:before :test] (get-in new-ctx [:payload :executions])))))

  (testing "the `:test` is not executed if `:before` marks the context failed"
    (let [job (job-fn/make-job-fn
               {:before (job-fn/make-job-step-fn
                         "failing before"
                         (comp (register-execution :before)
                               ex-ctx/mark-failed))
                :test (job-fn/make-job-step-fn
                       "never executed"
                       (register-execution :test))})
          new-ctx (job initial-ctx)]
      (is (= [:before] (get-in new-ctx [:payload :executions])))))

  (testing "ci jobs execute their `:after` step after the `:test` step"
    (let [job (job-fn/make-job-fn
               {:test (job-fn/make-job-step-fn
                       "test step"
                       (register-execution :test))
                :after (job-fn/make-job-step-fn
                        "after step"
                        (register-execution :after))})
          new-ctx (job initial-ctx)]
      (is (= [:test :after] (get-in new-ctx [:payload :executions])))))

  (testing "`:after` is executed even upon failure"
    (let [job (job-fn/make-job-fn
               {:before (job-fn/make-job-step-fn
                         "fails the test"
                         (comp
                          (register-execution :before)
                          ex-ctx/mark-failed))
                :test (job-fn/make-job-step-fn
                       "never executed"
                       (register-execution :test))
                :after (job-fn/make-job-step-fn
                        "executed despite failure"
                        (register-execution :after))})
          new-ctx (job initial-ctx)]
      (is (= [:before :after]
             (get-in new-ctx [:payload :executions]))))

    (let [job (job-fn/make-job-fn
               {:before (job-fn/make-job-step-fn
                         "before step"
                         (register-execution :before))
                :test (job-fn/make-job-step-fn
                       "fails the test"
                       (comp
                        (register-execution :test)
                        ex-ctx/mark-failed))
                :after (job-fn/make-job-step-fn
                        "executed despite failure"
                        (register-execution :after))})

          new-ctx (job initial-ctx)]
      (is (= [:before :test :after]
             (get-in new-ctx [:payload :executions]))))))
