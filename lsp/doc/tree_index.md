```clojure
(ns y0lsp.tree-index-test
  (:require
   [clojure.string :as str]
   [midje.sweet :refer [=> fact]]
   [y0.edn-parser :refer [edn-parser]]
   [y0.status :refer [unwrap-status]]
   [y0lsp.tree-index :refer :all]))

```
# Tree Index

In a launguage server we often need to fetch a parse-tree node that
corresponds to a given code location, or a range of nodes that corresponds to
a given code range.

This module defines functions for doing this. The revolve around a _tree
index_, which is a data structure which maps lines in a source file to tree
nodes that overlap with that line. We define functions for constructing the
index and for querying it.

## Testing Utils

In order to easily write tests / examples for this module, we introduce
utilities for creating parse-trees easily and marking positions or ranges
within them.

As a first step in their implementation, we define `extract-marker`, which
takes two strings: `text` and `marker`, with the former intended to contain
the latter exactly once, and returns a vector consisting of `text` with the
`marker` removed and the position of `marker` within `text`.
```clojure
(defn extract-marker [text marker]
  (let [index (str/index-of text marker)
        before (subs text 0 index)
        after (subs text (+ index (count marker)))
        line (->> before (filter #(= % (char 10))) count)]
    [(str before after) (+ 1000000
                           (* line 1000000)
                           (- (count before) (.lastIndexOf before "\n")))]))

(fact
 (extract-marker "abc$defg" "$") => ["abcdefg" 1000004]
 (extract-marker "ab\nc$de\nfg" "$") => ["ab\ncde\nfg" 2000002])

```
`pos-in-tree` takes a string and returns a vector containing the
[edn](../../doc/edn-parser.md) parse-tree of this text and the position of
the `$` marker within that text (the `$` is removed before parsing).
```clojure
(defn- pos-in-tree [text]
  (let [[text pos] (extract-marker text "$")
        parser (edn-parser {} nil [])
        [tree _deps] (unwrap-status (parser "x" text (constantly {:ok "x"})))] 
    [tree pos]))

(fact
 (pos-in-tree "(ns foo)\n[this $is the position]") =>
 ['[[x/this x/is x/the x/position]] 2000007])

```
`range-in-tree` takes a string that contains the markers `<<` and `>>` and
returns a vector containing a parse-tree and a start and end position of the
range defined by these markers.
```clojure
(defn- range-in-tree [text]
  (let [[text start] (extract-marker text "<<")
        [text end] (extract-marker text ">>")
        parser (edn-parser {} nil [])
        [tree _deps] (unwrap-status (parser "x" text (constantly {:ok "x"})))]
    [tree start end]))

(fact
 (range-in-tree "(ns foo)\n[<<this>> is the position]") =>
 ['[[x/this x/is x/the x/position]] 2000002 2000006])

```
## Positions and Nodes

Before we can index and query nodes by location, we need to develop tools to
relate nodes and code positions.

Our convention for code positions and locations is described
[elsewhere](../../doc/location_util.md).

The function `relate-node-to-pos` takes a tree node and a code position
(number) and returns whether the node is `:before`, `:within` or `:after` the
position.
```clojure
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

```
Note: in the above example, recall that the code location of a node is
`[:start, :end)`, i.e., `:start` is part of the node, `:end` is the first
position after the node.

If the node does not have location information, `:dontknow` is returned.
```clojure
(fact
 (relate-node-to-pos
  'foo
  1000004) => :dontknow)

```
`find-sub-node-at-pos` takes a list of nodes and a position and returns the
innermost node that comtains the position.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree "(ns foo)\n(do\n (:this $is)\n (that is not))")]
   (find-sub-node-at-pos nodes pos) => 'x/is)
 (let [[nodes pos] (pos-in-tree "(ns foo)\n(do\n $(:this is)\n (that is not))")]
   (find-sub-node-at-pos nodes pos) => '(:this x/is))
 (let [[nodes pos] (pos-in-tree "(ns foo)\n(do\n (this :i$s)\n (that is not))")]
   (find-sub-node-at-pos nodes pos) => '(x/this :is)))

```
If the position is outside the range of the node-list, `nil` is returned.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree "(ns foo)\n(do\n (:this is)\n (that is not))$")]
   (find-sub-node-at-pos nodes pos) => nil))

```
### Finding Nodes for Ranges

Given a range, with a start and end position, we would like to find a
sequence of nodes in the tree that make up this range.

This sequence of nodes comes from different levels in the tree, as we try to
find the nodes that most closely align with the region.

We build this functionality in steps. First, we introduce `nodes-after-pos`,
which takes a sequence of nodes and a position and returns another list,
which filters out nodes that are not after the position, and replaces
a compound node at the beginning with its sub-nodes that come after the
position. The last part repeats until we cannot break down the node anymore.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree "(ns foo) a b c d $e f g")]
   (nodes-after-pos nodes pos) => '[x/e x/f x/g])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b (c d $e) f) g")]
   (nodes-after-pos nodes pos) => '[x/e x/f x/g])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b (c d e$) f) g")]
   (nodes-after-pos nodes pos) => '[x/f x/g])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b (c :d $e) f) g")]
   (nodes-after-pos nodes pos) => '[x/e x/f x/g])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b (c d e$e) f) g")]
   (nodes-after-pos nodes pos) => '[x/ee x/f x/g])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b $ (c d e) f) g")]
   (nodes-after-pos nodes pos) => '[[x/c x/d x/e] x/f x/g]))

```
Similarly, `nodes-before-pos` returns a list of nodes that come strictly
_before_ the given position.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree "(ns foo) a b c d $e f g")]
   (nodes-before-pos nodes pos) => '[x/a x/b x/c x/d])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b c (d $e f)) g")]
   (nodes-before-pos nodes pos) => '[x/a x/b x/c x/d])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b c (d $e :f)) g")]
   (nodes-before-pos nodes pos) => '[x/a x/b x/c x/d])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b c (d e$e :f)) g")]
   (nodes-before-pos nodes pos) => '[x/a x/b x/c x/d])
 (let [[nodes pos] (pos-in-tree "(ns foo) a (b c (d e f)$) g")]
   (nodes-before-pos nodes pos) => '[x/a x/b x/c [x/d x/e x/f]]))

```
We now combine the two functions to define `nodes-within-range`, which takes
a sequence of nodes and a start and end positions, and returns a sequence of
nodes in between them.
```clojure
(fact
 (let [[nodes start end] (range-in-tree "(ns foo) a (b <<c (d e>> f)) g")]
   (nodes-within-range nodes start end) => '[x/c x/d x/e])
 (let [[nodes start end] (range-in-tree "(ns foo) a <<(b c (d e f)>>) g")]
   (nodes-within-range nodes start end) => '[x/b x/c [x/d x/e x/f]])
 (let [[nodes start end] (range-in-tree "(ns foo) a (b c (d <<e f)) g>>")]
   (nodes-within-range nodes start end) => '[x/e x/f x/g]))

```
## Indexing

To provide quick mapping from a source position to a tree node, we would like
to build an index for each parse-tree. The index is a map from line number to
a sequence of nodes that are contained in that line. These nodes are disjoint
and are ordered by their location in the source.

As a first step towards a full index, the function `index-single-node` takes
an initial index and a single parse-tree node with location information and
returns a partial index, just for this node.
```clojure
(fact
 (let [[[node]] (pos-in-tree (str "(ns foo)\n"
                                  "(this\n"
                                  "(is the)\n"
                                  "node)$"))]
   (index-single-node {} node) => '{2 [x/this]
                                    3 [[x/is x/the]]
                                    4 [x/node]}))

```
If a line is alreay covered by the initial map, the values are concatenated.
```clojure
(fact
 (let [[[node1 node2]] (pos-in-tree (str "(ns foo)\n"
                                         "(this\n"
                                         "(is the)\n"
                                         "first node) (and\n"
                                         "this\n"
                                         "(is the)\n"
                                         "second node)$"))
       idx (-> {}
               (index-single-node node1)
               (index-single-node node2))]
   idx => '{2 [x/this]
            3 [[x/is x/the]]
            4 [x/first x/node x/and]
            5 [x/this]
            6 [[x/is x/the]]
            7 [x/second x/node]}))

```
To index a complete file, `index-nodes` goes through a sequence of nodes,
running `index-single-node` on each and merging the results.
```clojure
(fact
 (let [[nodes] (pos-in-tree (str "(ns foo)\n"
                                 "(this\n"
                                 "(is the)\n"
                                 "first node) (and\n"
                                 "this\n"
                                 "(is the)\n"
                                 "second node)$"))]
   (index-nodes nodes) => '{2 [x/this]
                            3 [[x/is x/the]]
                            4 [x/first x/node x/and]
                            5 [x/this]
                            6 [[x/is x/the]]
                            7 [x/second x/node]}))

```
## Querying Node for Location

Now that we can build an index, our next step would be to use this index to
find a node in a tree based on its source location.

`find-node` takes an index and a position and returns a single node, found in
that position.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree (str "(ns foo)\n"
                                     "(this\n"
                                     "(is the)\n"
                                     "first node) (and\n"
                                     "this\n"
                                     "(is $the)\n"
                                     "second node)"))
       idx (index-nodes nodes)]
   (find-node idx pos) => 'x/the))

```
If the position is not on any node, `nil` is returned.
```clojure
(fact
 (let [[nodes pos] (pos-in-tree (str "(ns foo)\n"
                                     "(this\n"
                                     "(is the)\n"
                                     "first node) (and\n"
                                     "this\n"
                                     "(is the)\n"
                                     "second node)$"))
       idx (index-nodes nodes)]
   (find-node idx pos) => nil))
```

