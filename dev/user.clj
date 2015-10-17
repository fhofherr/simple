(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.test :as t]))

(defn- do-run-unit-tests
  []
  (t/run-all-tests #"fhofherr.simple\.test(\..+)-test"))

(defn- do-run-integration-tests
  []
  (t/run-all-tests #"fhofherr.simple\.integration-test(\..+)-test"))

(defn run-unit-tests
  []
  (repl/refresh :after 'user/do-run-unit-tests))

(defn run-integration-tests
  []
  (repl/refresh :after 'user/do-run-integration-tests))
