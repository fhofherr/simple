(ns fhofherr.simple.dsl
  "Functions and macros required to define a Simple CI project in a `simple.clj`
  project definition file. All public functions in this namespace are available
  from within the `simple.clj` project definition file."
  (:require [clojure.java.shell :refer [sh]]
            [fhofherr.simple.engine :as engine]))

(defn- references-to-map
  [references]
  (reduce (fn [m vs] (assoc m (first vs) (rest vs))) {} references))

(defn- map-to-references
  [ref-map]
  (map (fn [[k v]] (cons k v)) ref-map))

(defn- require-dsl
  [ref-map]
  (let [orig-require (:require ref-map [])
        imports-dsl? (some #(= 'fhofherr.simple.dsl (first %)) orig-require)
        new-require (if imports-dsl?
                      orig-require
                      (conj orig-require ['fhofherr.simple.dsl :refer :all]))]
    (assoc ref-map :require new-require)))

(defmacro defci
  "Configure your Simple CI environment. Defines a namespace whose name is set
  to [[engine/config-ns-name]]. You can use `:require`, `:import`, and
  `:refer-clojure` just as you would in a normal namespace."
  [& references]
  (let [new-refs (-> references
                     (references-to-map)
                     (require-dsl)
                     (map-to-references))]
    `(ns ~engine/config-ns-name
       ~@new-refs)))

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
        (engine/fail ctx)
        ctx))))
