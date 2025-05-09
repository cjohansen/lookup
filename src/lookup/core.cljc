(ns lookup.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn ^:no-doc add-result [res k v]
  (if (#{:class :pseudo-class :attrs :res :fns} k)
    (update res k #(conj (set %) v))
    (when (not-empty v)
      (assoc res k v))))

(defn ^:no-doc parse-attr-selector [syms]
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

(defn ^:no-doc parse-fn [f syms]
  (let [add-s #(cond
                 (= [\+] %2) (conj %1 '+)
                 (= [\>] %2) (conj %1 '>)
                 (seq %2) (conj %1 (str/join %2))
                 :else %1)
        conj? #(cond-> %1 %2 (conj %2))
        [selectors syms]
        (loop [[sym & syms] syms
               res []
               selector []
               s []]
          (cond
            (= \) sym)
            [(conj res (add-s selector s)) syms]

            (= \, sym)
            (recur syms (conj? res (add-s selector s)) [] [])

            (= \space sym)
            (recur syms res (add-s selector s) [])

            :else
            (recur syms res selector (conj s sym))))]
    [{:f f
      :selectors selectors}
     syms]))

(defn parse-tag [selector]
  (if (keyword? selector)
    [(namespace selector) (name selector)]
    (let [selector (str selector)
          slash (.indexOf selector "/")]
      (if (<= 0 slash)
        [(.substring selector 0 slash)
         (.substring selector (inc slash))]
        [nil selector]))))

(defn ^:no-doc parse-selector [selector]
  (let [[ns tag] (parse-tag selector)]
    (loop [sym (seq tag)
           k :tag-name
           s (if ns (vec (str ns "/")) [])
           res {}]
      (if-let [char (first sym)]
        (cond
          (= \. char)
          (recur (next sym) :class [] (add-result res k (str/join s)))

          (= \# char)
          (recur (next sym) :id [] (add-result res k (str/join s)))

          (= \: char)
          (recur (next sym) :pseudo-class [] (cond-> res
                                               (seq s) (add-result k (str/join s))))

          (= \[ char)
          (let [[v syms] (parse-attr-selector (next sym))]
            (recur syms k s (add-result res :attrs v)))

          (= \( char)
          (let [[v syms] (parse-fn (str/join s) (next sym))]
            (recur syms k [] (add-result res :fns v)))

          :else
          (recur (next sym) k (conj s char) res))
        (cond-> res
          (not-empty s) (add-result k (str/join s)))))))

(defn ^:no-doc parse-classes [class]
  (cond
    (coll? class) (map name class)
    (string? class) (str/split class #" +")
    (keyword? class) [(name class)]))

(defn ^:no-doc get-hiccup-headers [hiccup]
  (let [headers (parse-selector (first hiccup))
        attrs (second hiccup)
        children (if (map? attrs) (drop 2 hiccup) (next hiccup))]
    (cond-> (if (map? attrs)
              (-> headers
                  (into (dissoc attrs :class))
                  (update :class #(into (set %) (parse-classes (:class attrs)))))
              headers)
      (:path (meta hiccup))
      (assoc ::path (:path (meta hiccup)))

      (seq children)
      (assoc :children children))))

(defn ^:no-doc setify [x]
  (if (string? x)
    (set (str/split x #" +"))
    (set x)))

(defn ^:no-doc stringify [x]
  (if (coll? x)
    (str/join " " x)
    (str x)))

(defn ^:no-doc attr-match? [hiccup-headers {:keys [attr f val]}]
  (let [actual (get hiccup-headers attr)]
    (case f
      "=" (= (stringify actual) val)
      "~=" (contains? (setify actual) val)
      "|=" (or (= actual val) (re-find (re-pattern (str "(^|\\s)" val "-")) (stringify actual)))
      "^=" (str/starts-with? (stringify actual) val)
      "$=" (str/ends-with? (stringify actual) val)
      "*=" (str/includes? (stringify actual) val)
      (get hiccup-headers attr))))

(defn ^:no-doc pseudo-class-match? [index {::keys [path]} pc]
  (case pc
    "first-child" (= 0 (last path))
    "last-child" (= path (-> (get index (butlast path)) last meta :path))))

(declare select*)

(defn fn-match? [index hiccup-headers {:keys [f selectors]}]
  (case f
    "has" (every? #(seq (select* index % (:children hiccup-headers))) selectors)))

(defn ^:no-doc matches-1? [index hiccup-headers k v]
  (case k
    :class (set/subset? v (k hiccup-headers))
    :attrs (every? #(attr-match? hiccup-headers %) v)
    :pseudo-class (every? #(pseudo-class-match? index hiccup-headers %) v)
    :fns (every? #(fn-match? index hiccup-headers %) v)
    :tag-name (or (= "*" (str v)) (= (k hiccup-headers) v))
    (= (k hiccup-headers) v)))

(defn ^:no-doc matches?
  ([selector hiccup]
   (matches? nil selector hiccup))
  ([index selector hiccup]
   (let [headers (get-hiccup-headers hiccup)]
     (every? (fn [[k v]] (matches-1? index headers k v)) selector))))

(defn ^:no-doc subtree? [x]
  (and (sequential? x) (not (map-entry? x))))

(defn hiccup?
  "Returns `true` when `x` is a hiccup form"
  [x]
  (and (vector? x) (not (map-entry? x)) (keyword? (first x))))

(defn ^:no-doc flatten-seqs* [xs coll]
  (reduce
   (fn [coll x]
     (if (seq? x)
       (flatten-seqs* x coll)
       (conj! coll x)))
   coll xs))

(defn ^:no-doc flatten-seqs [xs]
  (let [coll (transient [])]
    (persistent! (flatten-seqs* xs coll))))

(defn ^:no-doc normalize-attrs [headers]
  (cond-> (dissoc headers :tag-name :children ::path)
    (empty? (:class headers)) (dissoc :class)))

(defn ^:export normalize-hiccup
  "Normalizes hiccup by removing nil children, flattening children, parsing out id
  and classes from the selector. Optionally elides empty attribute maps when
  `:strip-empty-attrs?` is `true`."
  [hiccup & [{:keys [strip-empty-attrs?] :as opt}]]
  (if (hiccup? hiccup)
    (let [headers (get-hiccup-headers hiccup)
          attrs (normalize-attrs headers)]
      (cond-> [(keyword (:tag-name headers))]
        (or (not strip-empty-attrs?)
            (not-empty attrs)) (conj attrs)
        :always (into (->> (flatten-seqs (:children headers))
                           (remove nil?)
                           (map #(normalize-hiccup % opt))))))
    hiccup))

(defn ^:no-doc normalize-tree [hiccup path]
  (if (hiccup? hiccup)
    (let [headers (get-hiccup-headers hiccup)]
      (-> [(keyword (:tag-name headers))
           (normalize-attrs headers)]
          (into (->> (flatten-seqs (:children headers))
                     (remove nil?)
                     (map-indexed #(normalize-tree %2 (conj path %1)))))
          (with-meta {:path path})))
    (with-meta
      {:kind :text-node
       :text hiccup}
      {:path path})))

(defn ^:no-doc index-tree [normalized-hiccup]
  (->> normalized-hiccup
       (tree-seq subtree? identity)
       (filter hiccup?)
       (map (juxt (comp :path meta) identity))
       (into {})))

(defn ^:no-doc get-nodes [node]
  (->> node
       (tree-seq subtree? identity)
       (filter hiccup?)))

(defn ^:no-doc raw-children [node]
  (drop (if (map? (second node)) 2 1) node))

(defn ^:no-doc get-descendants [node]
  (->> (raw-children node)
       get-nodes))

(defn ^:no-doc get-next-sibling [index node]
  (let [path (-> node meta :path)]
    (get index (update path (dec (count path)) inc))))

(defn ^:no-doc get-subsequent-siblings [index node]
  (let [path (-> node meta :path)]
    (drop (+ 3 (last path)) (get index (or (butlast path) [])))))

(defn ^:no-doc strip-attrs [hiccup]
  (into [(first hiccup)] (rest (rest hiccup))))

(defn ^:no-doc resolve-combinator [index parsed-selector nodes]
  (cond
    (= '> (first parsed-selector))
    [(next parsed-selector) (mapcat raw-children nodes)]

    (= '+ (first parsed-selector))
    [(next parsed-selector) (map #(get-next-sibling index %) nodes)]

    (= "~" (str (first parsed-selector)))
    [(next parsed-selector) (mapcat #(get-subsequent-siblings index %) nodes)]

    (empty? parsed-selector)
    [parsed-selector nodes]

    :else
    [parsed-selector (mapcat get-descendants nodes)]))

(defn ^:no-doc select* [index selector nodes]
  (loop [path selector
         nodes nodes]
    (if (empty? path)
      (walk/postwalk
       #(cond-> %
          (= :text-node (:kind %)) :text
          (and (hiccup? %)
               (or (nil? (second %)) (map? (second %)))
               (empty? (second %))) strip-attrs)
       nodes)
      (let [[path matches] (->> nodes
                                (filter (partial matches? index (parse-selector (first path))))
                                (resolve-combinator index (next path)))]
        (recur path matches)))))

(defn ^{:indent 1} select
  "Select nodes in `hiccup` matching the CSS `selector`"
  [selector hiccup]
  (if (and (not (hiccup? hiccup)) (coll? hiccup))
    (mapcat #(select selector %) hiccup)
    (let [normalized (normalize-tree hiccup [])]
      (->> normalized
           get-nodes
           (select* (index-tree normalized) (if (coll? selector) selector [selector]))))))

(defn ^{:indent 1 :export true} select-one [selector hiccup]
  (first (select selector hiccup)))

(defn ^:export attrs
  "Returns the hiccup node's attributes"
  [hiccup]
  (when (map? (second hiccup))
    (second hiccup)))

(defn ^:export children
  "Returns the hiccup node's children"
  [hiccup]
  (->> (if (map? (second hiccup))
         (drop 2 hiccup)
         (drop 1 hiccup))
       (map #(normalize-hiccup % {:strip-empty-attrs? true}))))

(defn ^:export first-child
  "Returns the hiccup node's first child"
  [node]
  (first (children node)))

(defn ^:export last-child
  "Returns the hiccup node's last child"
  [node]
  (last (children node)))

(defn text
  "Return only text from the hiccup structure; remove
   all tags and attributes"
  [hiccup]
  (cond
    (hiccup? hiccup)
    (str/join
     " "
     (->> (for [child (->> (drop (if (map? (second hiccup)) 2 1) hiccup)
                           flatten-seqs
                           (remove nil?))]
            (if (hiccup? child)
              (text child)
              (str child)))
          (remove empty?)))

    (coll? hiccup)
    (str/join " " (keep text hiccup))))

;; For backwards compatibility
(def ^:export get-text text)
