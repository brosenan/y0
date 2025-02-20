(ns y0lsp.addons.diag
  (:require
   [y0.explanation :refer [code-location explanation-to-str]]
   [y0lsp.location-utils :refer [to-lsp-location]]))

(defn explanation-to-diagnostic [expl]
  (let [{:keys [range]} (-> expl code-location to-lsp-location)]
    {:range range
     :severity 1
     :source "y0lsp"
     :message (explanation-to-str expl)}))