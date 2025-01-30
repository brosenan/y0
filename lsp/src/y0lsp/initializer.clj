(ns y0lsp.initializer 
  (:require
   [y0.config :refer [language-map-from-config keys-map]]
   [y0lsp.language-stylesheet :refer [compile-stylesheet]]))

(defn to-language-map [conf-spec lang-config]
  (language-map-from-config lang-config
                            (-> conf-spec
                                (assoc :lss {:default {:func #(compile-stylesheet %)
                                                        :args [:stylesheet]}}))
                            (-> keys-map
                                (assoc :lss :lss))))