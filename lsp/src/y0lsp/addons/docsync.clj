(ns y0lsp.addons.docsync
  (:require [y0lsp.addon-utils :refer [register-addon merge-server-capabilities
                                       swap-ws! add-notification-handler]]
            [y0lsp.server :refer [register-notification]]
            [y0lsp.workspace :refer [add-module eval-with-deps update-module]]
            [y0lsp.location-utils :refer [uri-to-path]]
            [lsp4clj.server :as server]))

(register-notification "textDocument/didOpen")
(register-notification "textDocument/didClose")
(register-notification "textDocument/didChange")
(register-notification "y0lsp/moduleEvaluated")

(defn- handle-did-open [ctx {:keys [text-document]}]
  (let [{:keys [uri]} text-document
        path (uri-to-path uri)]
    (swap-ws! ctx add-module {:path path
                              :text (:text text-document)
                              :is-open true})
    (swap-ws! ctx eval-with-deps path)
    (server/receive-notification
     "y0lsp/moduleEvaluated" ctx {:uri uri})))

(defn- handle-did-change [ctx {:keys [text-document content-changes]}]
  (let [{:keys [uri]} text-document
        path (uri-to-path uri)]
    (swap-ws! ctx update-module {:path path
                                 :text (:text content-changes)
                                 :is-open true})
    (server/receive-notification
     "y0lsp/moduleEvaluated" ctx {:uri uri})))
(register-addon "docsync"
                (merge-server-capabilities
                 {:text-document-sync {:change 1 :open-close true}})
                (->> handle-did-open
                     (add-notification-handler "textDocument/didOpen"))
                (->> handle-did-change
                     (add-notification-handler "textDocument/didChange")))
