(ns y0.config
  (:require [y0.edn-parser :refer [edn-parser root-module-symbols]]
            [y0.resolvers :refer [qname-to-rel-path-resolver prefix-list-resolver]]))

(defn- get-or-throw [m key name]
  (if (contains? m key)
    (get m key)
    (throw (Exception. (str "Key " key " is not found in " name)))))

(defn resolve-config-val [spec config key]
  (if (contains? spec key)
    (let [keyspec (get spec key)
          option-key (get-or-throw config key "the config")
          {:keys [func args]} (get-or-throw keyspec option-key (str "the spec for " key))
          arg-vals (for [arg args]
                     (resolve-config-val spec config arg))]
      (apply func arg-vals))
    (get-or-throw config key "the config")))

(def lang-spec {:parser {:edn {:func edn-parser :args [:root-refer-map]}}
                :resolver {:prefix-list {:func prefix-list-resolver 
                                         :args [:path-prefixes
                                                :relative-path-resolution]}}
                :root-refer-map {:root-symbols {:func root-module-symbols
                                                :args [:root-symbols :root-namespace]}}
                :relative-path-resolution {:dots {:func qname-to-rel-path-resolver
                                                  :args [:file-ext]}}
                :path-prefixes {:from-env {:func (constantly ["."]) :args []}}})

(defn language-map-from-config [config]
  (->> (for [[lang conf] config]
         [lang {:parse (resolve-config-val lang-spec conf :parser)
                :resolve (resolve-config-val lang-spec conf :resolver)}])
       (into {})))