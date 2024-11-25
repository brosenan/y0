(ns y0.to-html
  (:require [y0.term-utils :refer [postwalk-meta]]))

(defn- annotate-module-meta [{:keys [matches]} name id]
  (swap! matches assoc :html {:module name
                              :id (id)}))

(defn annotate-module-nodes [ms]
  (let [last-id (atom 0)
        id #(swap! last-id inc)]
    (doall (for [[_key {:keys [name statements]}] ms]
             (postwalk-meta #(annotate-module-meta % name id) statements)))))