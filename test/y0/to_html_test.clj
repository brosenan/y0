(ns y0.to-html-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.to-html :refer :all]))

;; # Tree to HTML

;; This module provides the ability to turn
;; [evaluated module-stores](polyglot_loader.md#evaluating-all-modules) into
;; sets of interlinked HTML files, colored and styled according to their
;; semantics.

;; This provides an easy way to visualize the semantic analysis done by $y_0$.

;; ## Preprocessing

;; Before we can convert modules to HTML we need to add annotations to
;; parse-tree nodes. We need to do this in order to be able to create links
;; between nodes, including across modules (which translate to separate HTML
;; files).

;; The function `annotate-module-nodes` takes a module-store and updates it in
;; place, adding to every decorated node a key `:html` to its `:matches`
;; decoration. This is a valid approach because the normal keys inside
;; `:matches` are symbols, and `:html` is a keyword.

;; The value associated with `:html` will be a map containing two keys:
;; `:module`, which will record the module name and `:id`, which will contain a
;; numeric ID for the node.

;; In the following example we build a fake module-store and then we annotate it
;; using `annotate-module-nodes`. Finally, we check that nodes are properly
;; annotated.
(comment (fact
          (let [ms {"l1:foo" {:name "foo"
                              :statements (decorate-tree [`a [`b `c]])}
                    "l1:bar" {:name "bar"
                              :statements (decorate-tree [`d `e])}}]))
         )