(ns stacks.tools.namespace
  "A completely nutty exercise in mapping the ns form to a datastructure."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [detritus.update :refer [map-vals take-when group-by-p]]))

(defn- libspec?
  "Returns true if x is a libspec"
  [x]
  (or (symbol? x)
      (and (vector? x)
           (or
            (nil? (second x))
            (keyword? (second x))))))

(defn- prependss
  "Prepends a symbol or a seq to coll"
  [x coll]
  (if (symbol? x)
    (cons x coll)
    (concat x coll)))

(def ^:dynamic *loading-options*
  [:exclude :only :rename :refer :reload :reload-all :verbose :use])

(defn parse-require*
  "Adapted from `#'clojure.core/load-lib`.

  Sadly the only way to be sure I've got this right is to cargo cult
  from the original implementation."
  [prefix use? lib & options]
  (let [lib                   (if prefix (symbol (str prefix \. lib)) lib)
        {:keys [as] :as opts} (apply hash-map options)
        filter-opts           (select-keys opts *loading-options*)]
    (merge {:type      ::require
            :namespace lib
            :as        #{(or as lib)}
            :rename    (map-vals (:rename opts) #(set [%]))
            :use?      use?}
           (dissoc filter-opts :rename))))

(defmacro defmerge [name bindings xform-mappings]
  `(defn ~name ~bindings
     (as-> {} ~'%
       ~@(for [[k xform] xform-mappings]
           `(assoc ~'% ~k (~xform ~'%
                                  ~@(for [b bindings]
                                      `(~k ~b))))))))

(def s*
  (fn [xform]
    (fn [_ %2 %3]
      (xform %2 %3))))

(def s-union
  (s* #(into (into #{} %1) %2)))

(def s-or
  (s* #(or %1 %2)))

(defmerge merge-require [l r]
  {:type       s-or
   :namespace  s-or
   :as         s-union
   :refer      s-union
   :exclude    s-union
   :only       s-union
   :rename     (s* (fn [x y] (merge-with #(into %1 %2) x y)))
   :reload     s-or
   :reload-all s-or
   :verbose    s-or
   :use?       s-or})

(defn add-require [require-map {:keys [namespace] :as record}]
  (if-not (contains? require-map namespace)
    (assoc require-map namespace record)
    (update require-map namespace merge-require record)))

(defn parse-require
  "Adapted from `#'clojure.core/load-libs`.

  Sadly the only way to be sure I've got this right is to cargo cult
  from the original implementation."

  ([ns-record args]
   (parse-require ns-record args false))

  ([ns-record args use?]
   (let [flags (filter keyword? args)
         opts  (interleave flags (repeat true))
         args  (filter (complement keyword?) args)]
     (->> (mapcat (fn [arg]
                    (if (libspec? arg)
                      [(apply parse-require* nil use? (prependss arg opts))]
                      (let [[prefix & args] arg]
                        (map #(apply parse-require* prefix use? (prependss % opts)) args))))
                  args)
          (reduce #(update %1 :require add-require %2) ns-record)))))

(defn parse-use [ns-record specs]
  ;; This is literally how use is implemented. ( ಠ_ಠ)
  (parse-require ns-record specs true))

(def class-pattern
  #"(?<module>[_a-z\.$]+)\.(?<class>[A-Z][a-zA-Z_\.]*)")

(defn add-import [ns-record import-name]
  (let [full-class           (name import-name)
        [match module class] (re-find class-pattern full-class)]
    (assoc-in ns-record [:imports (symbol class)] import-name)))

(defn normalize-prefix-list [prefix? list-or-name]
  (if (symbol? list-or-name)
    [(symbol (str (when prefix? (str prefix? ".")) list-or-name))]
    (mapcat (partial normalize-prefix-list
                     (str
                      ;; This isn't actually supported by Clojure but..
                      (when prefix? (str prefix? "."))
                      (first list-or-name)))
            (rest list-or-name))))

(defn normalize-prefix-lists [lists]
  (mapcat (partial normalize-prefix-list nil) lists))

(defn parse-import [ns-record specs]
  (reduce add-import ns-record (normalize-prefix-lists specs)))

(defn parse-gen [ns-record specs]
  ns-record)

(defn ns-add-statement [ns-record [form & tail :as expr]]
  (case form
    (:use)       (parse-use ns-record tail)
    (:require)   (parse-require ns-record tail)
    (:import)    (parse-import ns-record tail)
    (:gen-class) (parse-gen ns-record tail)))

(defn parse-ns-form [form]
  (let [[_ns_sym form]    (take-when #(= 'ns %) nil form)
        [ns-name form]    (take-when symbol? nil form)
        ns-name           (with-meta ns-name {})
        symbol-meta       (meta ns-name)
        [docstring? form] (take-when string? nil form)
        [more-meta? form] (take-when map? nil form)
        ns-meta           (merge {}
                                 symbol-meta
                                 (when docstring? {:doc docstring?})
                                 (when more-meta? more-meta?))]
    (as->  {:tag ::namespace :name ns-name :metadata ns-meta} %
      (reduce ns-add-statement % form))))
