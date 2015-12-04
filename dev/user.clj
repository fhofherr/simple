(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as t]
            [fhofherr.simple.core :as core]))

(defn- do-run-unit-tests
  []
  (t/run-all-tests #"fhofherr.(simple|clj-io)\.test(\..+)-test"))

(defn- do-run-integration-tests
  []
  (t/run-all-tests #"fhofherr.simple\.integration-test(\..+)-test"))

(defn run-unit-tests
  []
  (repl/refresh :after 'user/do-run-unit-tests))

(defn run-integration-tests
  []
  (repl/refresh :after 'user/do-run-integration-tests))

(def simple-core nil)

(defn start-simple-ci
  []
  (when-not simple-core
    (alter-var-root #'simple-core (constantly (core/load-core "." "simple.clj")))))

(defn stop-simple-ci
  []
  (when simple-core
    (alter-var-root #'simple-core (constantly nil))))

(defn run-simple-test-job
  []
  (if simple-core
    (core/start-job! simple-core 'test-simple-ci)
    (println "Call 'start-simple-ci' first!")))
