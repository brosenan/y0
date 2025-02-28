(ns y0.config
  (:require
   [clojure.string :as str]
   [y0.edn-parser :refer [edn-parser root-module-symbols]]
   [y0.explanation :refer [explanation-expr-to-str extract-expr-text]]
   [y0.instaparser :refer [instaparser]]
   [y0.resolvers :refer [path-prefixes-from-env prefix-list-resolver
                         qname-to-rel-path-resolver y0-resolver]]
   [y0.status :refer [unwrap-status]]
   [y0.core :refer [y0-symbols]]))

(def ^:dynamic *y0-langdef* nil)
(def ^:dynamic *y0-path* nil)

(defn cwd []
  (-> "." clojure.java.io/file .getAbsolutePath))

(defn- get-or-throw
  ([m key name optional? optional-ret]
   (if (contains? m key)
     (get m key)
     (if optional?
       optional-ret
       (throw (Exception. (str "Key " key " is not found in " name))))))
  ([m key name]
   (get-or-throw m key name false nil)))

(defn resolve-config-val [spec config key]
  (if (contains? spec key)
    (let [keyspec (get spec key)
          option-key (get-or-throw config key "the config"
                                   (:default keyspec) :default)
          {:keys [func args]} (get-or-throw keyspec option-key
                                            (str "the spec for " key))
          arg-vals (for [arg args]
                     (resolve-config-val spec config arg))]
      (apply func arg-vals))
    (get-or-throw config key "the config")))

(def lang-config-spec
  {:parser {:edn {:func edn-parser
                  :args [:root-refer-map :lang :extra-modules]}
            :insta {:func instaparser
                    :args [:grammar :identifier-kws
                           :dependency-kw :extra-modules]}}
   :resolver {:prefix-list {:func prefix-list-resolver
                            :args [:path-prefixes
                                   :relative-path-resolution]}}
   :reader {:slurp {:func (constantly slurp)
                    :args []}}
   :extra-modules {:default {:func (fn [y0-modules]
                                     (if (seq *y0-langdef*)
                                       (let [{:keys [resolve]} *y0-langdef*]
                                         (map #(-> % resolve unwrap-status str) y0-modules))
                                       y0-modules))
                             :args [:y0-modules]}}
   :root-refer-map {:root-symbols {:func root-module-symbols
                                   :args [:root-symbols :root-namespace]}}
   :relative-path-resolution {:dots {:func qname-to-rel-path-resolver
                                     :args [:file-ext]}}
   :path-prefixes {:default {:func (fn []
                                     (fn []
                                       [(cwd)]))
                              :args []}
                   :from-env {:func path-prefixes-from-env
                              :args [:path-prefixes-env]}}
   :expr-stringifier {:default {:func (constantly #(explanation-expr-to-str % 3))
                                :args []}
                      :extract-text {:func (constantly extract-expr-text)
                                     :args []}}
   :decorate {:default {:func (constantly false)
                        :args []}
              :true {:func (constantly true)
                     :args []}}
   :matcher {:default {:func (fn [file-ext]
                               #(str/ends-with? % (str "." file-ext)))
                       :args [:file-ext]}}})

(defn- lang-from-config [conf lang spec keys-map]
  (let [conf (assoc conf :lang lang)]
    [lang (->> (for [[key source] keys-map]
                 [key (resolve-config-val spec conf source)])
               (into {}))]))

(def keys-map {:parse :parser
               :read :reader
               :resolve :resolver
               :stringify-expr :expr-stringifier
               :decorate :decorate
               :match :matcher})

(defn language-map-from-config
  ([config]
   (language-map-from-config config lang-config-spec keys-map))
  ([config spec keys-map]
   (let [y0-def {:parse (edn-parser
                         (root-module-symbols y0-symbols "y0.core")
                         "y0"
                         [])
                 :read slurp
                 :resolve (y0-resolver *y0-path*)
                 :match #(str/ends-with? % ".y0")}]
     (binding [*y0-langdef* y0-def]
       (->> (for [[lang conf] config]
              (lang-from-config conf lang spec keys-map))
            (into {"y0" y0-def}))))))