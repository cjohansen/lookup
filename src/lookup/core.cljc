(ns lookup.core
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(defn stringify-selector [selector]
  (if (keyword? selector)
    (str (when-let [ns (namespace selector)]
           (str ns "/")) (name selector))
    (str selector)))

(defn add-result [res k v]
  (case k
    :class (update res k #(conj (set %) v))
    :pseudo-class (update res k #(conj (set %) v))
    :attrs (update res k #(conj (set %) v))
    (when (not-empty v)
      (assoc res k v))))

(defn parse-attr-selector [syms]
  (loop [syms syms
         attr []
         f []
         val []
         val? false]
    (if-let [char (first syms)]
      (cond
        (#{\= \~ \| \^ \$ \*} char)
        (recur (next syms) attr (conj f char) val true)

        (= \] char)
        [(cond-> {:attr (keyword (str/join attr))}
           (not-empty f) (assoc :f (str/join f))
           (not-empty val) (assoc :val (str/join val)))
         (next syms)]

        val?
        (recur (next syms) attr f (conj val char) val?)

        :else
        (recur (next syms) (conj attr char) f val val?))
      (throw (ex-info "Unexpected end of attribute selector: missing ]"
                      {:attr (str/join attr)
                       :f (str/join f)
                       :val (str/join val)})))))

(defn parse-selector [selector]
  (loop [sym (seq (stringify-selector selector))
         k :tag-name
         s []
         res {}]
    (if-let [char (first sym)]
      (cond
        (= \. char)
        (recur (next sym) :class [] (add-result res k (str/join s)))

        (= \# char)
        (recur (next sym) :id [] (add-result res k (str/join s)))

        (= \: char)
        (recur (next sym) :pseudo-class [] (add-result res k (str/join s)))

        (= \[ char)
        (let [[v syms] (parse-attr-selector (next sym))]
          (recur syms k s (add-result res :attrs v)))

        :else
        (recur (next sym) k (conj s char) res))
      (cond-> res
        (not-empty s) (add-result k (str/join s))))))

(defn parse-classes [class]
  (cond
    (coll? class) class
    (string? class) (str/split class #" +")))

(defn get-hiccup-headers [hiccup]
  (let [headers (parse-selector (first hiccup))
        attrs (second hiccup)]
    (if (map? attrs)
      (-> headers
          (into (dissoc attrs :class))
          (update :class #(into (set %) (parse-classes (:class attrs)))))
      headers)))

(defn setify [x]
  (if (string? x)
    (set (str/split x #" +"))
    (set x)))

(defn stringify [x]
  (if (coll? x)
    (str/join " " x)
    (str x)))

(defn attr-match? [hiccup-headers {:keys [attr f val]}]
  (let [actual (get hiccup-headers attr)]
    (case f
      "=" (= (stringify actual) val)
      "~=" (contains? (setify actual) val)
      "|=" (or (= actual val) (re-find (re-pattern (str "(^|\\s)" val "-")) (stringify actual)))
      "^=" (str/starts-with? (stringify actual) val)
      "$=" (str/ends-with? (stringify actual) val)
      "*=" (str/includes? (stringify actual) val)
      (contains? hiccup-headers attr))))

(defn matches-1? [hiccup-headers k v]
  (case k
    :class (set/subset? v (k hiccup-headers))
    :attrs (every? #(attr-match? hiccup-headers %) v)
    (= (k hiccup-headers) v)))

(defn matches? [selector hiccup]
  (let [headers (get-hiccup-headers hiccup)]
    (every? (fn [[k v]] (matches-1? headers k v)) selector)))
