```clojure
(ns y0lsp.tree-index-test
  (:require [midje.sweet :refer [fact =>]]
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

## Locations and Nodes

Before we can index and query nodes by location, we need to develop tools to
relate nodes and locations.

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

