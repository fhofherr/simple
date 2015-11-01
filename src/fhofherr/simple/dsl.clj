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
  `clojure.java.shell/sh`. The job is marked as failed if the executable
  returns an exit code that is different from 0."
  [executable]
  (fn [ctx]
    (let [result (sh (str (:project-dir ctx) "/" executable))
          exit-code (:exit result)]
      (if (< 0 exit-code)
        (engine/fail ctx
                     (format "Executable '%s' returned with exit code %s"
                             executable
                             exit-code))
        ctx))))
