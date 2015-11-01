(ns fhofherr.simple.engine
  "FIXME: Add documentation.")

(def config-ns-name 'fhofherr.simple.projectdef)

(defn initial-context
  "Initial context of a job execution."
  [project-dir]
  {:project-dir project-dir})

(defn failed?
  "Check if the given context `ctx` is marked as failed. Return the reason
  for failure, or `nil` if the context is not failed."
  [ctx]
  (::fail ctx))

(defn fail
  "Mark the context `ctx` as failed. And set `reason` as the reason for
  failure."
  [ctx reason]
  {:pre [(string? reason)]}
  (assoc ctx ::fail reason))

(defn simple-ci-job?
  "Check if the given object is a Simple CI job."
  [obj]
  (boolean (:ci-job? (meta obj))))

(defn- apply-step
  [f ignore-failure? ctx]
  (if (and f
           (or ignore-failure? (not (failed? ctx))))
    (f ctx)
    ctx))

(defn make-job
  "Create a Simple CI job from a job definition. A job definition is a map
  that contains one or more of the following keys:

  * `:test`: the actual test to execute. The value for `:test` has to be a
    function of one argument, which is used as the current test context. The
    test function has to return a possibly modified test context.

  * `:before`: set up code to execute before the `:test`. The same rules
    as for the `:test` function apply. If `:before` fails `:test` and `:after`
    will not be executed.

  * `:after`: tear down code to execute after the `:test`. The same rules
    as for the `:test` function apply. The `:after` function will be executed
    even if `:before` or `:test` fail.

  Simple CI jobs themselves are functions of one argument. They expect
  the initial job context as their only argument and pass it down to the
  functions used in the job definition. They return the job context modified
  by the job's functions."
  [{:keys [test before after]}]
  (letfn [(job [ctx]
            (->> ctx
                 (apply-step before false)
                 (apply-step test false)
                 (apply-step after true)))]
    (with-meta job {:ci-job? true})))


(defn load-config
  "Load the Simple CI configuration file located under the given `path`.
  Returns the namespace into which the configration was loaded."
  [path]
  (when (find-ns config-ns-name)
    (remove-ns config-ns-name))
  (binding [*ns* (find-ns 'fhofherr.simple.dsl)]
    (load-file path))
  (find-ns config-ns-name))

(defn find-ci-jobs
  "Find Simple CI jobs in the `cidef-ns` namespace. All mappings with
  a truthy value for the key `:ci-job?` in their meta data are treated as
  Simple CI jobs. Returns the private vars of all found Simple CI jobs as a
  lazy sequence."
  [cidef-ns]
  (->> cidef-ns
       (ns-publics)
       (map second)
       (filter #(:ci-job? (meta (var-get %))))))
