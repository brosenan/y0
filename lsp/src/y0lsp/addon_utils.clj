(ns y0lsp.addon-utils)

(defn add-req-handler [name handler]
  #(update % :req-handlers assoc name handler))

(defn add-notification-handler [name handler]
  #(update % :notification-handlers
           update name
           conj handler))

(defn- deep-merge [m1 m2]
  (if (and (map? m1)
           (map? m2))
    (merge-with deep-merge m1 m2)
    m2))

(defn merge-server-capabilities [cap]
  #(update % :server-capabilities deep-merge cap))

(defn swap-ws! [ctx f & args]
  (apply swap! (:ws ctx) f args))

(defn get-module [ctx path]
  (-> ctx :ws deref :ms (get path)))