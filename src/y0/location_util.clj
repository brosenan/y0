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
