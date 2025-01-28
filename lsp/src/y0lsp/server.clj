(ns y0lsp.server
  (:require [lsp4clj.server :as server]
            [lsp4clj.coercer :as coercer]
            [clojure.spec.alpha :as s]))

(defn- check-conformance [spec val err-atom code msg]
  (let [explanation (s/explain-data spec val)]
    (if (nil? explanation)
      val
      (let [err {:code code
                 :message msg
                 :data explanation}]
        (when err-atom
          (reset! err-atom err))
        {:error err}))))

(defn handle-request [{:keys [req-handlers err-atom] :as ctx} key req response-spec]
  (let [handler (get req-handlers key)
        result (handler ctx req)]
    (check-conformance response-spec result err-atom 1000
                       "Request output does not conform to the spec")))

(defmacro register-req [req key response-spec]
  (let [ctx-var (gensym "ctx")
        req-var (gensym "req")
        dontcare-var (gensym "_")]
    `(defmethod server/receive-request ~req [~dontcare-var ~ctx-var ~req-var]
       (handle-request ~ctx-var ~key ~req-var ~response-spec))))

(defmethod server/receive-request "initialize"
  [_ 
   {:keys [server-capabilities client-capabilities]} 
   req]
  (reset! client-capabilities (:client-capabilities req))
  {:server-capabilities server-capabilities
   :server-info {:name "y0lsp"}})

(defn handle-notification [{:keys [notification-handlers] :as ctx} key notif]
  (doseq [f (get notification-handlers key)]
    (f ctx notif)))

(defmacro register-notification [name key]
  (let [ctx-var (gensym "ctx")
        notif-var (gensym "notif")
        dontcare-var (gensym "_")]
    `(defmethod server/receive-notification ~name [~dontcare-var ~ctx-var ~notif-var]
       (handle-notification ~ctx-var ~key ~notif-var))))

(register-req "textDocument/declaration" :text-doc-declaration
              (s/or :location ::coercer/location
                    :locations ::coercer/locations))

