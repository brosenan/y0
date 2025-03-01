(ns y0lsp.addons.sem-tokens
  (:require
   [y0.location-util :refer [encode-file-pos]]
   [y0lsp.addon-utils :refer [register-addon]]
   [y0lsp.location-utils :refer [to-lsp-pos]]))

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

(register-addon "sem-tokens"
                )