(ns fhofherr.simple.test.engine-test
  (:require [clojure.test :refer :all]
            [fhofherr.simple.dsl :as dsl]
            [fhofherr.simple.engine :as engine])
  (:import [java.util.concurrent CountDownLatch]))

(deftest execution-context

  (testing "the initial context contains the project directory"
    (let [project-dir "path/to/some/directory"]
      (is (= project-dir (:project-dir (engine/initial-context project-dir))))))

  (testing "the initial context is never failed"
    (is (not (engine/failed? (engine/initial-context ".")))))

  (testing "the context can be marked as failed"
    (is (engine/failed? (engine/mark-failed (engine/initial-context "."))))))

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
                                          #(engine/mark-failed %))
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
                                          #(engine/mark-failed %))
                                :test (register-execution :test)
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :after] (get-in new-ctx [:payload :executions]))))

    (let [job (engine/make-job {:before (register-execution :before)
                                :test (comp
                                        (register-execution :test)
                                        #(engine/mark-failed %))
                                :after (register-execution :after)})
          new-ctx (job (engine/initial-context "."))]
      (is (= [:before :test :after] (get-in new-ctx [:payload :executions]))))))

(def first-job (engine/make-job {:test identity}))

(def second-job (engine/make-job {:test identity}))

(def waiting-job-latch (atom (CountDownLatch. 1)))
(def waiting-job (engine/make-job {:test (fn [ctx]
                                           {:pre [@waiting-job-latch]}
                                           (.await @waiting-job-latch)
                                           (assoc ctx :status :successful))}))

(deftest find-ci-jobs

  (testing "finds ci jobs in the given namespace"
    (let [found-jobs (engine/find-ci-jobs (find-ns 'fhofherr.simple.test.engine-test))]
      (is (= #{#'first-job #'second-job #'waiting-job} (set found-jobs))))))

(deftest make-job-descriptor

  (testing "creates a job descriptor for a job var"
    (let [job-desc (engine/make-job-descriptor #'first-job)]
      (is (= #'first-job (:job-var job-desc)))
      (is (= first-job (:job-fn job-desc)))
      (is (= [] @(:executions job-desc)))
      (is (= -1 @(:executor job-desc)))
      (is (engine/created? job-desc))
      (is (not (engine/queued? job-desc)))
      (is (not (engine/executing? job-desc)))
      (is (not (engine/successful? job-desc)))
      (is (not (engine/failed? job-desc))))))

(deftest make-job-execution!

  (testing "create a new job descriptor"
    (let [prj-dir "./path/to/non-existent/dir"
          job-desc (engine/make-job-descriptor #'first-job)
          [exec-id exec] (engine/make-job-execution! job-desc
                                              (engine/initial-context prj-dir))]
      (is (engine/created? exec)))))

(deftest schedule-job-execution!
  (let [prj-dir "./path/to/non-existent/dir"
        job-desc (engine/make-job-descriptor #'waiting-job)]

    (testing "set a queued and an executing job's status"
      (let [[exec-id-1 _] (engine/make-job-execution! job-desc
                                                      (engine/initial-context prj-dir))
            [exec-id-2 _] (engine/make-job-execution! job-desc
                                                      (engine/initial-context prj-dir))]
        (reset! waiting-job-latch (CountDownLatch. 1))
        (engine/schedule-job-execution! job-desc exec-id-1)
        (engine/schedule-job-execution! job-desc exec-id-2)

        (Thread/sleep 100)
        (is (engine/executing? (engine/get-job-execution job-desc exec-id-1)))
        (is (engine/executing? job-desc))
        (is (engine/queued? (engine/get-job-execution job-desc exec-id-2)))
        (is (engine/queued? job-desc))

        (.countDown @waiting-job-latch)
        (Thread/sleep 100)
        (is (engine/successful? job-desc))))))
