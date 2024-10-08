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

;; `pos-span` takes two positions and computes the size of the span between
;; them. If they are on the same row, the span is the difference in column
;; number minus one (because it is exlusive).
(fact
 (-> (pos-span (encode-file-pos 12 34) (encode-file-pos 12 37))
     decode-file-pos) => [0 4])

;; If they are not on the same row, the span contains the row difference
;; between them and the end column.
(fact
 (-> (pos-span (encode-file-pos 12 34) (encode-file-pos 14 37))
     decode-file-pos) => [2 37])

;; `pos-span` can take a code location as input, providing the span of the code
;; segment.
(fact
 (-> (pos-span {:start (encode-file-pos 12 34)
                :end (encode-file-pos 14 37)
                :path "foo/bar"})
     decode-file-pos) => [2 37])

;; ## Taking and Dropping up to a Code Position

;; Given a source file represented as a sequence of lines and a source position,
;; `drop-up-to` will return the original sequence of lines with everything up to
;; the given position removed.
(fact
 (drop-up-to ["123456789 - 1"
              "123456789 - 2"
              "123456789 - 3"
              "123456789 - 4"
              "123456789 - 5"
              "123456789 - 6"] (encode-file-pos 3 5)) =>
 ["56789 - 3"
  "123456789 - 4"
  "123456789 - 5"
  "123456789 - 6"])

;; To complement this, given a sequence of lines and a span, `take-span` returns
;; the contents of the lines up to the size of the span.
(fact
 (take-span ["123456789 - 1"
             "123456789 - 2"
             "123456789 - 3"
             "123456789 - 4"
             "123456789 - 5"
             "123456789 - 6"] (encode-file-pos 3 5)) =>
 ["123456789 - 1"
  "123456789 - 2"
  "123456789 - 3"
  "1234"])

;; ## Extracting based on Code Location

;; Given a source file represented as a sequence of lines and a code location,
;; `extract-location` returns a sequence of lines between `:start` and `:end`.

;; This is useful for extracting the text underlying a parse-tree node.
(fact
 (extract-location ["123456789 - 1"
                    "123456789 - 2"
                    "123456789 - 3"
                    "123456789 - 4"
                    "123456789 - 5"
                    "123456789 - 6"]
                   {:start (encode-file-pos 3 5)
                    :end (encode-file-pos 5 2)
                    :path "foo/bar"}) =>
 ["56789 - 3"
  "123456789 - 4"
  "1"])