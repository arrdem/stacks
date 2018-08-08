(ns stacks.tools.prepl
  "A slightly hacked copy of clojure.core.server's prepl"
  (:require [clojure.core.server :refer :all :exclude [prepl]]
            [clojure.main :as m]))

(defn prepl
  "a REPL with structured output (for programs)
  reads forms to eval from in-reader (a LineNumberingPushbackReader)
  Closing the input or passing the form :repl/quit will cause it to return

  Calls out-fn with data, one of:
  {:type ::ret
   :val val ;;eval result
   :ns ns-name-string
   :nanos long ;; eval time in ns
   :form string} ;;iff successfully read
  {:type ::out
   :val string} ;chars from during-eval *out*
  {:type ::err
   :val string} ;chars from during-eval *err*
  {:type ::tap
   :val val} ;values from tap>

  You might get more than one `:out` or `:err` per eval, but exactly
  one `:ret` tap output can happen at any time (i.e. between evals)

  If during eval an attempt is made to read `*in*` it will read from
  in-reader unless `:stdin` is supplied

  Unlike `#'clojure.core.server/prepl`, accepts `:ns`, being a symbol
  naming the namespace in which evaluation should occur. By default,
  `user` is used."
  [in-reader out-fn &
   {:keys [stdin ns bindings capture-bindings]
    :or {ns 'user}}]
  (let [EOF (Object.)
        tapfn #(out-fn {:type ::tap
                        :val %1})
        form-id (volatile! 0)
        bindings-to-capture (or capture-bindings
                                (keys bindings))]
    (m/with-bindings
      (with-bindings (merge {#'*ns* (do (try (require ns)
                                             (catch Throwable t
                                               nil))
                                        (clojure.lang.Namespace/findOrCreate ns))
                             #'*in* (or stdin in-reader)}
                            bindings)
        (try
          (add-tap tapfn)
          (loop []
            (let [current-form-id @form-id]
              (when (try
                      (let [[form s] (read+string in-reader false EOF)]
                        (try
                          (if-not (identical? form EOF)
                            (let [start (System/nanoTime)
                                  ret (binding [*out* (PrintWriter-on #(out-fn {:type ::stream
                                                                                :stream ::out
                                                                                :val %1
                                                                                :form-id current-form-id})
                                                                      nil)
                                                *err* (PrintWriter-on #(out-fn {:type ::stream
                                                                                :stream ::err
                                                                                :val %1
                                                                                :form-id current-form-id})
                                                                      nil)]
                                        (eval form))
                                  ns (- (System/nanoTime) start)]
                              (when-not (= :repl/quit ret)
                                (set! *3 *2)
                                (set! *2 *1)
                                (set! *1 ret)
                                (out-fn {:type ::ret
                                         :val (if (instance? Throwable ret)
                                                (Throwable->map ret)
                                                ret)
                                         :ns (str (.name *ns*))
                                         :nanos ns
                                         :form s
                                         :form-id current-form-id})
                                true))
                            (when (not-empty s)
                              (out-fn {:type ::incomplete
                                       :ns (str (.name *ns*))
                                       :form s
                                       :form-id current-form-id})
                              false))
                          (catch Throwable ex
                            (set! *e ex)
                            (out-fn {:type ::ret
                                     :val (Throwable->map ex)
                                     :ns (str (.name *ns*))
                                     :form s
                                     :form-id current-form-id})
                            true)))
                      (catch Throwable ex
                        (set! *e ex)
                        (out-fn {:type ::ret
                                 :val (Throwable->map ex)
                                 :ns (str (.name *ns*))
                                 :form-id current-form-id})
                        true)
                      (finally
                        (out-fn
                         {:type ::bindings
                          :form-id current-form-id
                          :bindings (select-keys (clojure.lang.Var/getThreadBindings)
                                                 bindings-to-capture)})))
                (vswap! form-id inc)
                (recur))))
          (finally
            (remove-tap tapfn)))))))

(defn resolve-fn
  "This is private in `clojure.core.server` for no clear reason."
  [valf]
  (if (symbol? valf)
    (or (resolve valf)
        (when-let [nsname (namespace valf)]
          (require (symbol nsname))
          (resolve valf))
        (throw (Exception. (str "can't resolve: " valf))))
    valf))
