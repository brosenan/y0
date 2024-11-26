(ns y0.to-html
  (:require [y0.term-utils :refer [postwalk-meta]]
            [clojure.string :as str]))

(defn- annotate-module-meta [{:keys [matches]} name id]
  (when (some? matches)
    (swap! matches assoc :html {:module name
                              :id (id)})))

(defn annotate-module-nodes [ms]
  (let [last-id (atom 0)
        id #(swap! last-id inc)]
    (doall (for [[_key {:keys [name statements]}] ms]
             (postwalk-meta #(annotate-module-meta % name id) statements)))))

(defn module-name-to-html-file-name [name]
  (-> name
      (str/replace "/" "-")
      (str ".html")))

(defn node-uri [node]
  (let [{:keys [matches]} (meta node)]
    (if (nil? matches)
      nil
      (let [{:keys [module id]} (:html @matches)]
        (str (module-name-to-html-file-name module) "#" id)))))