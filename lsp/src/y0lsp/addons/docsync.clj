(ns y0lsp.addons.docsync
  (:require [y0lsp.addon-utils :refer [register-addon merge-server-capabilities
                                       swap-ws! add-notification-handler]]
            [y0lsp.server :refer [register-notification]]
            [y0lsp.workspace :refer [add-module eval-with-deps update-module]]
            [y0lsp.location-utils :refer [uri-to-path]]))

(register-notification "textDocument/didOpen")
(register-notification "textDocument/didChange")
(register-addon "docsync"
                (merge-server-capabilities
                 {:text-document-sync {:change 1 :open-close true}})
                (->> (fn [ctx {:keys [text-document]}]
                       (let [path (uri-to-path (:uri text-document))]
                         (swap-ws! ctx add-module {:path path
                                                   :text (:text text-document)
                                                   :is-open true})
                         (swap-ws! ctx eval-with-deps path)))
                     (add-notification-handler "textDocument/didOpen"))
                (->> (fn [ctx {:keys [text-document]}]
                       (let [path (uri-to-path (:uri text-document))]
                         (swap-ws! ctx update-module {:path path
                                                   :text (:text text-document)
                                                   :is-open true})))
                     (add-notification-handler "textDocument/didChange")))
