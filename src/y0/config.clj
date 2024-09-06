(ns y0.config)

(defn- get-or-throw [m key name]
  (if (contains? m key)
    (get m key)
    (throw (Exception. (str "Key " key " is not found in " name)))))

(defn resolve-config-val [spec config key]
  (if (contains? spec key)
    (let [keyspec (get spec key)
          option-key (get-or-throw config key "the config")
          {:keys [func args]} (get-or-throw keyspec option-key (str "the spec for " key))
          arg-vals (for [arg args]
                     (resolve-config-val spec config arg))]
      (apply func arg-vals))
    (get-or-throw config key "the config")))
