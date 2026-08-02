(ns d0.main
  (:require [clojure.term.colors :refer [blue green red]]
            [d0.d0-spec-analyzer :refer [process-d0-spec-file wrap-callback
                                         d0-test]]
            [y0.explanation :refer [all-unique-locations code-location
                                    explanation-to-str]])
  (:gen-class))

(defn render-location [location]
  (if (nil? location)
    ""
    (let [start (:start location)
          row (quot start 1000000)]
      (str (:path location) ":" row ": "))))

(defn- print-error [err]
  (let [message (explanation-to-str err)
        location (code-location err)]
    (binding [*out* *err*]
      (println (str (render-location location) (red "Error") ":") message)
      (doseq [[term loc] (all-unique-locations err)]
        (println (str (render-location loc) (blue "Note") ":") (pr-str term))))))

(defn main
  "Process the given d0 spec `files` in sequence, running every translation
  example through [[d0-test]]. Reports the total number of successful examples,
  or, if any example failed, the errors (with locations) followed by a summary."
  [files]
  (let [callback (wrap-callback d0-test)
        states (mapv #(process-d0-spec-file callback %) files)
        success (reduce + 0 (map #(:success % 0) states))
        errors (mapcat :errors states)]
    (cond
      (seq errors) (do
                     (doseq [err errors]
                       (print-error err))
                     (println (count errors) (red "Failed")
                              (if (pos? success)
                                (str "but " success " succeeded")
                                ""))
                     (System/exit 1))
      (pos? success) (println success (green "Succeeded"))
      :else (println (blue "No tests ran")))))

(defn -main [& files]
  (main files))
