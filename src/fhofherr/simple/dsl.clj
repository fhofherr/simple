(ns fhofherr.simple.dsl
  "Functions and macros required to define a Simple CI project in a `simple.clj`
  project definition file. All public functions in this namespace are available
  from within the `simple.clj` project definition file."
  (:require [clojure.java.shell :refer [sh]]))

(defmacro defjob
  "Define a Simple CI job. In its most basic form a Simple CI job has a name
  and a test command, e.g.:

  ```clojure
  (defjob some-job-name
    :test test-command)
  ```

  Test commands are functions that expect a `job-context` map as their only
  argument.

  Just as test commands Simple CI jobs themselfes are functions. They too expect
  a `job-context` as their only argument which they pass on to their test
  command.

  Simple CI jobs have the key `:ci-job?` with the value `true` in their meta
  data.

  TODO: currently the job commands return value is left unspecified. It might
        be wise to expect job commands to return a (possibly altered)
        job-context."
  [job-name & {:keys [test]}]
  `(defn ~(vary-meta job-name assoc :ci-job? true)
     [job-context#]
     (when ~test
       (~test job-context#))))

(defn execute
  "Create a test command that executes the given executable using
  `clojure.java.shell/sh`.

  Returns whatever `clojure.java.shell/sh` returns."
  [executable]
  (fn [job-context]
    (sh (str (:project-dir job-context) "/" executable))))
