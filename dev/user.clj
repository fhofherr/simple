(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as t]
            [fhofherr.simple.engine :as engine]))

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

(def simple-engine nil)

(defn start-simple-ci
  []
  (when-not simple-engine
    (alter-var-root #'simple-engine (constantly (engine/load-engine "." "simple.clj")))))

(defn stop-simple-ci
  []
  (when simple-engine
    (alter-var-root #'simple-engine (constantly nil))))

(defn run-simple-test-job
  []
  (if simple-engine
    (engine/start-job! simple-engine 'test-simple-ci)
    (println "Call 'start-simple-ci' first!")))
