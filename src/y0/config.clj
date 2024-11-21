(ns y0.config
  (:require [y0.edn-parser :refer [edn-parser root-module-symbols]]
            [y0.explanation :refer [explanation-expr-to-str extract-expr-text]]
            [y0.instaparser :refer [instaparser]]
            [y0.resolvers :refer [path-prefixes-from-env prefix-list-resolver
                                  qname-to-rel-path-resolver]]))

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
                    :args [:lang :grammar :identifier-kws
                           :dependency-kw :extra-modules]}}
   :resolver {:prefix-list {:func prefix-list-resolver
                            :args [:path-prefixes
                                   :relative-path-resolution]}}
   :reader {:slurp {:func (constantly slurp)
                    :args []}}
   :root-refer-map {:root-symbols {:func root-module-symbols
                                   :args [:root-symbols :root-namespace]}}
   :relative-path-resolution {:dots {:func qname-to-rel-path-resolver
                                     :args [:file-ext]}}
   :path-prefixes {:from-env {:func path-prefixes-from-env
                              :args [:path-prefixes-env]}}
   :expr-stringifier {:default {:func (constantly #(explanation-expr-to-str % 3))
                                :args []}
                      :extract-text {:func (constantly extract-expr-text)
                                     :args []}}
   :decorate {:default {:func (constantly false)
                        :args []}
              :true {:func (constantly true)
                     :args []}}})

(defn language-map-from-config [config]
  (->> (for [[lang conf] config]
         (let [conf (assoc conf :lang lang)]
           [lang {:parse (resolve-config-val lang-config-spec conf :parser)
                  :read (resolve-config-val lang-config-spec conf :reader)
                  :resolve (resolve-config-val lang-config-spec conf :resolver)
                  :stringify-expr (resolve-config-val lang-config-spec conf :expr-stringifier)
                  :decorate (resolve-config-val lang-config-spec conf :decorate)}]))
       (into {})))