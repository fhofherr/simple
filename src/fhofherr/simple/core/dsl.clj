(ns fhofherr.simple.core.dsl
  "Functions and macros required to configure Simple CI in a `simple.clj` file.
  All public functions in this namespace are available from within the
  `simple.clj` file."
  (:require [clojure.java.shell :refer [sh]]
            [fhofherr.simple.core [config :as config]
             [job-descriptor :as jobs]
             [job-execution-context :as ex-ctx]
             [job-fn :as job-fn]]))

(defn- references-to-map
  [references]
  (reduce (fn [m vs] (assoc m (first vs) (rest vs))) {} references))

(defn- map-to-references
  [ref-map]
  (map (fn [[k v]] (cons k v)) ref-map))

(defn- require-dsl
  [ref-map]
  (let [orig-require (:require ref-map [])
        imports-dsl? (some #(= 'fhofherr.simple.core.dsl (first %)) orig-require)
        new-require (if imports-dsl?
                      orig-require
                      (conj orig-require ['fhofherr.simple.core.dsl :refer :all]))]
    (assoc ref-map :require new-require)))

(defmacro defci
  "Configure your Simple CI environment. Defines a namespace whose name is set
  to [[config/config-ns-name]]. You can use `:require`, `:import`, and
  `:refer-clojure` just as you would in a normal namespace."
  [& references]
  (let [new-refs (-> references
                     (references-to-map)
                     (require-dsl)
                     (map-to-references))]
    `(ns ~config/config-ns-name
       ~@new-refs)))

;; TODO try to make the macro smaller
(defmacro defjob
  "Define a Simple CI job.

  In its most basic form a Simple CI job has a name and a test command, e.g.:

  ```clojure
  (defjob some-job-name
    :test test-command)
  ```

  All keys available to [[job-fn/make-job]] can be used during the defnition
  of the job.

  The result of the macro is a job descriptor bound to the `job-name`."
  [job-name & {:keys [before test after]}]
  (let [b (and before
               `(job-fn/make-job-step-fn '~before ~before))
        t (and test
               `(job-fn/make-job-step-fn '~test ~test))
        a (and after
               `(job-fn/make-job-step-fn '~after ~after))
        j `(job-fn/make-job-fn {:before ~b
                                :test ~t
                                :after ~a})]
    `(def ~job-name (jobs/make-job-descriptor (name '~job-name) ~j))))

(defn execute
  "Create a test command that executes the given executable using
  `clojure.java.shell/sh`. The job is marked as failed if the executable
  returns an exit code that is different from 0."
  [executable]
  (fn [ctx]
    (let [result (sh (str (:project-dir ctx) "/" executable))
          exit-code (:exit result)]
      (if (< 0 exit-code)
        (ex-ctx/mark-failed ctx)
        ctx))))
