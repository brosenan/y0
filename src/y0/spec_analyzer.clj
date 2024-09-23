(ns y0.spec-analyzer)

(defn find-transition [transes line]
  (loop [transes transes]
    (if (empty? transes)
      {}
      (let [[trans & transes] transes]
        (cond
          (not (contains? trans :pattern)) trans
          (re-matches (:pattern trans) line) (dissoc trans :pattern)
          :else (recur transes))))))

(defn apply-line [sm s v line]
  (let [state-spec (get sm s)
        trans (find-transition state-spec line)
        s (if (contains? trans :transition)
            (:transition trans)
            s)
        v (if (contains? trans :update-fn)
            ((:update-fn trans) v line)
            v)]
    [s v]))

(defn process-lines [sm s v lines]
  (if (empty? lines)
    v
    (let [[line & lines] lines
          [s v] (apply-line sm s v line)]
      (recur sm s v lines))))