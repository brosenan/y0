(ns y0lsp.addon-utils 
  (:require
   [y0lsp.location-utils :refer [node-at-text-doc-pos]]))

(def addons (atom {}))

(defn register-addon [name & funcs]
  (swap! addons assoc name (apply comp funcs)))

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

(defn lss-for-node [{:keys [lang-map] :as ctx} node]
  (let [path (-> node meta :path)
        {:keys [lang]} (get-module ctx path)
        lss (get-in lang-map [lang :lss])]
    (fn [key]
      (lss node key))))

(defn add-node-and-lss-to-doc-pos [handler]
  (fn [ctx req]
    (let [node (node-at-text-doc-pos ctx req)
          lss (lss-for-node ctx node)
          req (-> req
                  (assoc :node node)
                  (assoc :lss lss))]
      (handler ctx req))))