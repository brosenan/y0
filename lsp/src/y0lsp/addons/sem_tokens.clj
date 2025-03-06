(ns y0lsp.addons.sem-tokens
  (:require
   [y0lsp.addon-utils :refer [register-addon add-req-handler get-module]]
   [y0lsp.location-utils :refer [to-lsp-pos uri-to-path]]
   [y0lsp.server :refer [register-req]]))

(defn token-type-encoder [strs]
  (let [idx (->> strs
                 (map-indexed (fn [i s] [s i]))
                 (into {}))]
    (fn [s]
      (get idx s -1))))

(defn pos-diff [a b]
  (if (= (:line a) (:line b))
    {:line 0
     :character (- (:character a)
                   (:character b))}
    {:line (- (:line a)
              (:line b))
     :character (:character a)}))

(defn rel-pos-encoder []
  (let [last (atom {:line 0 :character 0})]
    (fn [start len]
      (let [curr (to-lsp-pos start)
            diff (pos-diff curr @last)
            {:keys [line character]} diff]
        (reset! last curr)
        [line character len]))))

(defn symbol-len [sym]
  (-> sym name count))

(defn symbol-start [sym]
  (let [{:keys [end]} (meta sym)]
    (- end (symbol-len sym))))

(defn all-symbols [tree]
  (cond
    (symbol? tree) [tree]
    (sequential? tree) (mapcat all-symbols tree)
    :else []))

(defn encode-symbols [tree sym-class]
  (let [f (rel-pos-encoder)]
    (->> tree
         all-symbols
         (mapcat (fn [sym]
                   (let [cls (sym-class sym)]
                     (if (nil? cls)
                       []
                       (let [start (symbol-start sym)
                             len (symbol-len sym)]
                         (concat (f start len) cls)))))))))

(defn- sem-tokens [{:keys [lang-map client-capabilities] :as ctx}
                   {:keys [text-document]}]
  (let [path (uri-to-path (:uri text-document))
        {:keys [lang statements]} (get-module ctx path)
        {:keys [lss]} (get lang-map lang)
        tt-enc (token-type-encoder (-> @client-capabilities :text-document
                                       :semantic-tokens :token-types))
        classify (fn [sym]
                   (let [type (lss sym :token-type)]
                     (if (nil? type)
                       nil
                       (let [n (tt-enc type)]
                         (if (>= n 0)
                           [n 0]
                           nil)))))]
    {:data (encode-symbols statements classify)}))

(register-req "textDocument/semanticTokens/full" any?)
(register-addon "sem-tokens"
                #(update % :capability-providers conj
                         (fn [{:keys [text-document]}]
                           (let [{:keys [semantic-tokens]} text-document]
                             {:semantic-tokens-provider 
                              {:legend {:token-types
                                        (:token-types semantic-tokens)
                                        :token-modifiers []}
                               :full true}})))
                (->> sem-tokens
                     (add-req-handler "textDocument/semanticTokens/full")))