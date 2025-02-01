(ns y0lsp.location-utils-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.location-utils :refer :all]))

;; Location Utilities

;; [Locations](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#location)
;; and
;; [positions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#position)
;; are used in many LSP messages. This module provides utilities to convert
;; between the LSP representations of locations and positions and the
;; [ones](../..//doc/location_util.md) used by the $y_0$ implementation.

;; The key differences between the two representations are:

;; 1. The LSP uses zero-based numbering for rows and columns while the $y_0$
;;    implementation uses one-based numbering.
;; 2. LSP uses maps for positions while the $y_0$ implementation uses integers.
;; 3. File paths are represented as URIs in the LSP, while $y_0$ uses absolute
;;    paths.

;; Position Conversion

;; The function `from-lsp-pos` takes an [LSP
;; position](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#position)
;; and reutnrs a corresponding number used as position by $y_0$.
(fact
 (from-lsp-pos {:line 3 :character 5}) => 4000006)

;; Conversely, the function `to-lsp-pos` takes a numeric position and returns
;; and LSP position.
(fact
 (to-lsp-pos 4000006) => {:line 3 :character 5})

;; `from-lsp-text-doc-pos` converts a LSP
;; [TextDocumentPositionParams](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentPositionParams)
;; to a pair of an absolute path and a numeric position.
(fact
 (from-lsp-text-doc-pos {:text-document {:uri "file:///path/to/module.c0"}
                         :position {:line 3
                                    :character 5}}) =>
 ["/path/to/module.c0" 4000006])

;; Location Conversion

;; `to-lsp-location` takes a $y_0$ location map and returns a corresponding [LSP
;; location](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#location).
(fact
 (to-lsp-location {:start 4000001
                   :end 4000006
                   :path "/path/to/module.c0"}) =>
 {:uri "file:///path/to/module.c0"
  :range {:start {:line 3 :character 0}
          :end {:line 3 :character 5}}})