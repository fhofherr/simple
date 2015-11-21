(ns fhofherr.simple.engine.status-model)

(defprotocol Stateful
  "Define different states a object can have."

  (current-state
    [o]
    "Get the object `o`'s current state.")

  (state-valid?
    [o s]
    "Check if the state `s` is valid within the context of `o`.")

  (force-state
    [o s]
    "Force `o` into the state `s` even if a transition to `s` is not normally
    possible. Used by [[transition-to-state]]. Usually there is no need to call
    this function directly.")

  (transition-possible?
    [o s]
    "Check if `o` can transition from its current state to `s`."))

(defn ^:dynamic *cant-transition-to-state*
  "Signals that the transition from `cur-state` to `next-state` is not possible.
  The default implementation throws an `clojure.lang.ExceptionInfo`. Can be
  rebound to something else. It should not be assumed that the return value
  of a rebound implementation is used."
  [cur-state next-state]
  (throw (ex-info (format "Can't transition from state %s to %s"
                          cur-state
                          next-state)
                  {:cause :invalid-state-transition
                   :current-state cur-state
                   :invalid-next-state next-state})))

(defn transition-to-state
  "Make `o` transition to its new state `s`."
  [this next-state]
  (if (transition-possible? this next-state)
    (force-state this next-state)
    (*cant-transition-to-state* (current-state this) next-state)))
