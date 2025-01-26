(ns y0lsp.server
  (:require [lsp4clj.server :as server]
            [lsp4clj.coercer :as coercer]
            [clojure.spec.alpha :as s]))

(defn- handle-request [{:keys [req-handlers err-atom] :as ctx} key req response-spec]
  (let [handler (get req-handlers key)
        result (handler ctx req)]
    (let [explanation (s/explain-data response-spec result)]
      (if (nil? explanation)
        result
        (let [err {:code 1000
                   :message "Request output does not conform to the spec"
                   :data explanation}]
          (when err-atom
            (reset! err-atom err))
          {:error err})))))

(defmacro register-req [req key response-spec]
  (let [ctx-var (gensym "ctx")
        req-var (gensym "req")
        dontcare-var (gensym "_")]
    `(defmethod server/receive-request ~req [~dontcare-var ~ctx-var ~req-var]
       (handle-request ~ctx-var ~key ~req-var ~response-spec))))

(register-req "textDocument/declaration" :text-doc-declaration
              (s/or :location ::coercer/location
                    :locations ::coercer/locations))
