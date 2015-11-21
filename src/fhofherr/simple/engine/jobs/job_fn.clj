(ns fhofherr.simple.engine.jobs.job-fn
  (:require [fhofherr.simple.engine.jobs [execution-context :as ex-ctx]]))

;; TODO rename to job-fn?
(defn job-fn?
  "Check if the given object is a Simple CI job."
  [obj]
  (boolean (::job-fn? (meta obj))))

(defn- apply-step
  [f ignore-failure? ctx]
  (if (and f
           (or ignore-failure? (not (ex-ctx/failed? ctx))))
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
                 ;; TODO execute in finally
                 (apply-step after true)))]
    (with-meta job {::job-fn? true})))
