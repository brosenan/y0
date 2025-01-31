(ns y0lsp.initializer 
  (:require
   [y0.config :refer [keys-map language-map-from-config]]
   [y0.polyglot-loader :refer [load-module]]
   [y0.status :refer [unwrap-status]]
   [y0lsp.language-stylesheet :refer [compile-stylesheet]]))

(defn to-language-map [conf-spec lang-config]
  (language-map-from-config lang-config
                            (-> conf-spec
                                (assoc :lss {:default {:func #(compile-stylesheet %)
                                                        :args [:stylesheet]}}))
                            (-> keys-map
                                (assoc :lss :lss))))

(defn module-loader [lang-map]
  (fn [m]
    (unwrap-status (load-module m lang-map))))