(ns fhofherr.simple.engine)

(defn find-ci-jobs
  [projectdef-ns]
  (->> projectdef-ns
       (ns-publics)
       (map second)
       (filter #(:ci-job? (meta %)))
       (set)))
