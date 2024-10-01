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

(defn pos-span [start end]
  (let [[start-row start-col] (decode-file-pos start)
        [end-row end-col] (decode-file-pos end)]
    (if (= start-row end-row)
      (encode-file-pos 0 (- end-col start-col))
      (encode-file-pos (- end-row start-row) end-col))))