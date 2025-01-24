(ns y0lsp.tree-index-test
  (:require
   [clojure.string :as str]
   [midje.sweet :refer [=> fact]]
   [y0.edn-parser :refer [edn-parser]]
   [y0.status :refer [unwrap-status]]
   [y0lsp.tree-index :refer :all]))

;; # Tree Index

;; In a launguage server we often need to fetch a parse-tree node that
;; corresponds to a given code location, or a range of nodes that corresponds to
;; a given code range.

;; This module defines functions for doing this. The revolve around a _tree
;; index_, which is a data structure which maps lines in a source file to tree
;; nodes that overlap with that line. We define functions for constructing the
;; index and for querying it.

;; ## Testing Utils

;; In order to easily write tests / examples for this module, we introduce
;; utilities for creating parse-trees easily and marking positions or ranges
;; within them.

;; As a first step in their implementation, we define `extract-marker`, which
;; takes two strings: `text` and `marker`, with the former intended to contain
;; the latter exactly once, and returns a vector consisting of `text` with the
;; `marker` removed and the position of `marker` within `text`.
(defn- extract-marker [text marker]
  (let [index (str/index-of text marker)
        before (subs text 0 index)
        after (subs text (+ index (count marker)))
        line (->> before (filter #(= % (char 10))) count)]
    [(str before after) (+ 1000000
                           (* line 1000000)
                           (count before)
                           (- 0 (.lastIndexOf before "\n")))]))

(fact
 (extract-marker "abc$defg" "$") => ["abcdefg" 1000004]
 (extract-marker "ab\nc$de\nfg" "$") => ["ab\ncde\nfg" 2000002])

;; `pos-in-tree` takes a string and returns a vector containing the
;; [edn](../../doc/edn-parser.md) parse-tree of this text and the position of
;; the `$` marker within that text (the `$` is removed before parsing).
(defn- pos-in-tree [text]
  (let [[text pos] (extract-marker text "$")
        parser (edn-parser {} nil [])
        [tree _deps] (unwrap-status (parser "x" text (constantly {:ok "x"})))] 
    [tree pos]))

(fact
 (pos-in-tree "(ns foo)\n[this $is the position]") =>
 ['[[x/this x/is x/the x/position]] 2000007])

;; `range-in-tree` takes a string that contains the markers `<<` and `>>` and
;; returns a vector containing a parse-tree and a start and end position of the
;; range defined by these markers.
(defn- range-in-tree [text]
  (let [[text start] (extract-marker text "<<")
        [text end] (extract-marker text ">>")
        parser (edn-parser {} nil [])
        [tree _deps] (unwrap-status (parser "x" text (constantly {:ok "x"})))]
    [tree start end]))

(fact
 (range-in-tree "(ns foo)\n[<<this>> is the position]") =>
 ['[[x/this x/is x/the x/position]] 2000002 2000006])

;; ## Locations and Nodes

;; Before we can index and query nodes by location, we need to develop tools to
;; relate nodes and locations.

;; Our convention for code positions and locations is described
;; [elsewhere](../../doc/location_util.md).

;; The function `relate-node-to-pos` takes a tree node and a code position
;; (number) and returns whether the node is `:before`, `:within` or `:after` the
;; position.
(fact
 (relate-node-to-pos
  (with-meta 'foo {:start 1000001
                   :end 1000004})
  1000004) => :before
 (relate-node-to-pos
  (with-meta 'foo {:start 1000005
                   :end 1000008})
  1000004) => :after
 (relate-node-to-pos
  (with-meta 'foo {:start 1000004
                   :end 1000007})
  1000004) => :within)

;; Note: in the above example, recall that the code location of a node is
;; `[:start, :end)`, i.e., `:start` is part of the node, `:end` is the first
;; position after the node.

