(ns fhofherr.simple.engine
  "FIXME: Add documentation.")

(defn simple-ci-job?
  "Check if the given object is a Simple CI job."
  [obj]
  (boolean (:ci-job? (meta obj))))

(defn find-ci-jobs
  "Find Simple CI jobs in the `projectdef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns all found Simple CI jobs as a set."
  [projectdef-ns]
  (->> projectdef-ns
       (ns-publics)
       (map second)
       (filter #(:ci-job? (meta (var-get %))))
       (set)))

(defn make-job
  "Create a Simple CI job from a job definition. A job definition is a map
  that contains one or more of the following keys:

  * `:test`: the actual test to execute. The value for `:test` has to be a
    function of one argument, which is used as the current test context. The
    test function has to return a possibly modified test context.

  Simple CI jobs themselves are functions of one argument. They expect
  the initial job context as their only argument and pass it down to the
  functions used in the job definition. They return the job context modified
  by the job's functions.

  Simple CI jobs have the key `:ci-job?` with the value `true` in their meta
  data."
  [{:keys [test]}]
  (letfn [(do-test [ctx] (when test (test ctx)))
          (job [job-context] (do-test job-context))]
    (with-meta job {:ci-job? true})))
