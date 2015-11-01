(ns fhofherr.simple.dsl
  "Functions and macros required to define a Simple CI project in a `simple.clj`
  project definition file. All public functions in this namespace are available
  from within the `simple.clj` project definition file."
  (:require [clojure.java.shell :refer [sh]]
            [fhofherr.simple.engine :as engine]))

(defmacro defjob
  "Define a Simple CI job. In its most basic form a Simple CI job has a name
  and a test command, e.g.:

  ```clojure
  (defjob some-job-name
    :test test-command)
  ```

  See [[engine/make-job]] for further details about Simple CI jobs."
  [job-name & {:as jobdef}]
  (let [jobdef# jobdef]
    `(def ~job-name (engine/make-job ~jobdef#))))

(defn execute
  "Create a test command that executes the given executable using
  `clojure.java.shell/sh`.

  Returns whatever `clojure.java.shell/sh` returns."
  [executable]
  (fn [job-context]
    (sh (str (:project-dir job-context) "/" executable))))
