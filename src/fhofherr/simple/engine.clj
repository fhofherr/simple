(ns fhofherr.simple.engine
  "FIXME: Add documentation.")

(defn find-ci-jobs
  "Find Simple CI jobs in the `projectdef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns all found Simple CI jobs as a set."
  [projectdef-ns]
  (->> projectdef-ns
       (ns-publics)
       (map second)
       (filter #(:ci-job? (meta %)))
       (set)))
