(ns y0.spec-analyzer)

(defn find-transition [transes line]
  (loop [transes transes]
    (if (empty? transes)
      [{} nil]
      (let [[trans & transes] transes]
        (if (contains? trans :pattern)
          (let [matches (re-matches (:pattern trans) line)]
            (if (nil? matches)
              (recur transes)
              [(dissoc trans :pattern) matches]))
          [trans [line]])))))

(defn apply-line [sm v line]
  (let [s (:state v)
        state-spec (get sm s)
        [trans matches] (find-transition state-spec line)
        s (if (contains? trans :transition)
            (:transition trans)
            s)
        v (assoc v :state s)
        v (if (contains? trans :update-fn)
            ((:update-fn trans) v matches)
            v)]
    v))

(defn process-lines
  ([sm v lines]
   (if (empty? lines)
     v
     (let [[line & lines] lines
           v (apply-line sm v line)]
       (recur sm v lines)))))

(def lang-spec-sm
  {:init [{:pattern #"[Ll]anguage: +`(.*)`"
           :update-fn (fn [v [_line lang]]
                        (-> v
                            (assoc :lang lang)))}]})

(defn process-lang-spec [v lines]
  (process-lines lang-spec-sm v lines))