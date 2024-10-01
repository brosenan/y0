(ns y0.location-util-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.location-util :refer :all]))

;; # Location Utilities

;; Code location is represented as a map with the following keyes:
;; * `:path`: The file path.
;; * `:start`: The start location within the file, and
;; * `:end`: The end location within the file.

;; `:start` and `:end` are given as integers, computed as:
;; `row * 1000000 + col`.

;; ## Encoding and Decoding File Positions

;; A file position is a single integer representing a row and a column within a
;; file.

;; `encode-file-pos` takes row and column numbers and encodes them as a single
;; integer.
(fact
 (encode-file-pos 12 34) => 12000034)

;; `decode-file-pos` takes a file position (single integer) and returns a
;; `[row col]` pair.
(fact
 (decode-file-pos 12000034) => [12 34])

;; ## File Position Arithmetic

;; The function `pos-offset` takes a file position and a position offset,
;; structured as a file position, and returns a file position which is the
;; result of applying the offset on the original position.

;; If the offset has zero rows, the offset column number is added to the
;; position column number and the row number remains unchanged.
(fact
 (-> (encode-file-pos 12 34)
     (pos-offset (encode-file-pos 0 3))
     decode-file-pos) => [12 37])

;; If the number of rows in the offset is not zero, the number of rows is added
;; to the position row number and the column number remain unchanged, regardless
;; of the number of columns in the offset.
(fact
 (-> (encode-file-pos 12 34)
     (pos-offset (encode-file-pos 2 3))
     decode-file-pos) => [14 34])
