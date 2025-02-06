(ns y0lsp.addon-utils)

(defn add-req-handler [name handler]
  #(update % :req-handlers assoc name handler))

(defn add-notification-handler [name handler]
  #(update % :notification-handlers
           update name
           conj handler))