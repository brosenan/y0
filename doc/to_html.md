* [Tree to HTML](#tree-to-html)
  * [Preprocessing](#preprocessing)
```clojure
(ns y0.to-html-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.to-html :refer :all]
            [y0.term-utils :refer [postwalk-meta]]))

```
# Tree to HTML

This module provides the ability to turn
[evaluated module-stores](polyglot_loader.md#evaluating-all-modules) into
sets of interlinked HTML files, colored and styled according to their
semantics.

This provides an easy way to visualize the semantic analysis done by $y_0$.

## Preprocessing

Before we can convert modules to HTML we need to add annotations to
parse-tree nodes. We need to do this in order to be able to create links
between nodes, including across modules (which translate to separate HTML
files).

The function `annotate-module-nodes` takes a module-store and updates it in
place, adding to every decorated node a key `:html` to its `:matches`
decoration. This is a valid approach because the normal keys inside
`:matches` are symbols, and `:html` is a keyword.

The value associated with `:html` will be a map containing two keys:
`:module`, which will record the module name and `:id`, which will contain a
numeric ID for the node.

First, let us define `decorate-tree`, which postwalks a tree and adds mutable
`:matches` decorations.
```clojure
(defn- decorate-tree [tree]
  (postwalk-meta #(assoc % :matches (atom {})) tree))

```
In the following example we build a fake module-store and then we annotate it
using `annotate-module-nodes`. Finally, we check that nodes are properly
annotated.
```clojure
(fact
 (let [ms {"l1:foo" {:name "foo"
                     :statements (decorate-tree [`a [`b `c]])}
           "l1:bar" {:name "bar"
                     :statements (decorate-tree [`d `e])}}
       _ (annotate-module-nodes ms)
       foo-body (-> ms (get "l1:foo") :statements)
       bar-body (-> ms (get "l1:bar") :statements)]
   (-> foo-body first meta :matches deref) => {:html {:module "foo"
                                                      :id 1}}
   (-> foo-body second first meta :matches deref) => {:html {:module "foo"
                                                             :id 2}}
   (-> foo-body second second meta :matches deref) => {:html {:module "foo"
                                                              :id 3}}
   (-> foo-body second meta :matches deref) => {:html {:module "foo"
                                                       :id 4}}
   (-> foo-body meta :matches deref) => {:html {:module "foo"
                                                :id 5}}
   (-> bar-body first meta :matches deref) => {:html {:module "bar"
                                                      :id 6}}
   (-> bar-body second meta :matches deref) => {:html {:module "bar"
                                                       :id 7}}
   (-> bar-body meta :matches deref) => {:html {:module "bar"
                                                :id 8}}))
```

