(ns y0.location-util)

(def SEP 1000000)

(defn encode-file-pos [row col]
  (+ col (* row SEP)))

(defn decode-file-pos [pos]
  [(quot pos SEP) (mod pos SEP)])

(defn pos-offset [pos offs]
  (let [[row-offs col-offs] (decode-file-pos offs) 
        [row col] (decode-file-pos pos)]
    (if (= row-offs 0)
      (encode-file-pos row (+ col col-offs))
      (encode-file-pos (+ row row-offs) col))))

(defn pos-span
  ([start end]
   (let [[start-row start-col] (decode-file-pos start)
         [end-row end-col] (decode-file-pos end)]
     (if (= start-row end-row)
       (encode-file-pos 0 (- end-col start-col -1))
       (encode-file-pos (- end-row start-row) end-col))))
  ([{:keys [start end]}]
   (pos-span start end)))

(defn drop-up-to [lines pos]
  (let [[row col] (decode-file-pos pos)
        lines (drop (dec row) lines)
        [line & lines] lines
        line (drop (dec col) line)]
    (cons (apply str line) lines)))

(defn take-span [lines span]
  (let [[row col] (decode-file-pos span)
        lines (take (inc row) lines)
        line (last lines)
        lines (take row lines)
        line (take (dec col) line)]
    (concat lines [(apply str line)])))

(defn extract-location [lines {:keys [start end]}]
  (let [span (pos-span start end)]
    (-> lines
        (drop-up-to start)
        (take-span span))))