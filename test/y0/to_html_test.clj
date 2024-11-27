(ns y0.to-html-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.to-html :refer :all]
            [y0.term-utils :refer [postwalk-meta]]))

;; # Tree to HTML

;; This module provides the ability to turn
;; [evaluated module-stores](polyglot_loader.md#evaluating-all-modules) into
;; sets of interlinked HTML files, colored and styled according to their
;; semantics.

;; This provides an easy way to visualize the semantic analysis done by $y_0$.

;; ## Prerequisites

;; Before we can convert modules to HTML we need to satisfy a few prerequisites.
;; First, we need to add annotations to parse-tree nodes. We need to do this in
;; order to be able to create links between nodes, including across modules
;; (which translate to separate HTML files). Next, we need to be able to convert
;; these annotations to relative URIs. This in turn requires a way to translate
;; a module name into an HTML file name.

;; ### Preprocessing

;; The function `annotate-module-nodes` takes a module-store and updates it in
;; place, adding to every decorated node a key `:html` to its `:matches`
;; decoration. This is a valid approach because the normal keys inside
;; `:matches` are symbols, and `:html` is a keyword.

;; The value associated with `:html` will be a map containing two keys:
;; `:module`, which will record the module name and `:id`, which will contain a
;; numeric ID for the node.

;; First, let us define `decorate-tree`, which postwalks a tree and adds mutable
;; `:matches` decorations.
(defn- decorate-tree [tree]
  (postwalk-meta #(assoc % :matches (atom {})) tree))

;; In the following example we build a fake module-store and then we annotate it
;; using `annotate-module-nodes`. Finally, we check that nodes are properly
;; annotated.
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

;; Undecorated nodes are safelu ignored.
(fact
 (let [ms {"l1:foo" {:name "foo"
                     :statements [`a (decorate-tree [`b `c])]}}
       _ (annotate-module-nodes ms)
       foo-body (-> ms (get "l1:foo") :statements)]
   (-> foo-body second first meta :matches deref) => {:html {:module "foo"
                                                             :id 1}}
   (-> foo-body second second meta :matches deref) => {:html {:module "foo"
                                                              :id 2}}
   (-> foo-body second meta :matches deref) => {:html {:module "foo"
                                                       :id 3}}))

;; ### HTML File Names from Module Names

;; When converting a module-store to HTML files, we place all HTML files in a
;; flat directory. The name of each file is derived from the full name of the
;; module, with the `.html` extension added.

;; `module-name-to-html-file-name` performs this translation.
(fact
 (module-name-to-html-file-name "my.module.name") => "my.module.name.html")

;; If the module name contains slashes (`/`), they are translated to dashes
;; (`-`).
(fact
 (module-name-to-html-file-name "my/module/name") => "my-module-name.html")

;; ### Node URIs

;; Given a parse-tree node, `node-uri` returns a relative URI to that node in
;; one of the generated HTML files. The URI contains the HTML file name and the
;; node ID.
(fact
 (let [node (with-meta `foo {:matches (atom {:html {:module "bar"
                                                    :id 7}})})]
   (node-uri node) => "bar.html#7"))

;; It returns `nil` if the node is not annotated.
(fact
 (let [node `foo]
   (node-uri node) => nil))

;; ## Tree to Hiccup

;; At the core of generating HTML out of decorated parse-trees lies the ability
;; to take the contents of a file as a string along with its tree representation
;; and generate a [Hiccup](https://github.com/weavejester/hiccup) representation
;; of an HTML tree which follows the structure of the parse-tree, but weaves the
;; text inside it.

;; The function `tree-to-hiccup` does this. It takes a module (map) containing
;; the fields `:text` and `:statements`, and an annotation function which
;; returns the element name and attributes for each HTML element based on the
;; corresponding node.

;; `tree-to-hiccup` works list to list. For an empty tree (empty list), the text
;; is returned as a single element of a list.
(fact
 (tree-to-hiccup {:text "This is some text"
                  :statements []}
                 (constantly [:span {}])) => ["This is some text"])

;; Given a single node, the output consists of three elements: the text before
;; the start of that node, the HTML element representing the node and the text
;; after.
(fact
 (tree-to-hiccup {:text "123456789"
                  :statements [(with-meta [:int 456] {:start 1000004
                                                      :end 1000007})]}
                 (constantly [:span {}])) =>
 ["123" [:span {} "456"] "789"])

;; Given `n` "flat" nodes, the list will contain `2n+1` elements.
(fact
 (tree-to-hiccup {:text "1234567890"
                  :statements [(with-meta [:int 34] {:start 1000003
                                                      :end 1000005})
                               (with-meta [:int 78] {:start 1000007
                                                     :end 1000009})]}
                 (constantly [:span {}])) =>
 ["12" [:span {} "34"] "56" [:span {} "78"] "90"])

;; Nested nodes create nested HTML elements.
(fact
 (tree-to-hiccup {:text "123456789"
                  :statements [(with-meta [:foo
                                           (with-meta [:int 34] {:start 1000003
                                                                 :end 1000005})
                                           (with-meta [:int 56] {:start 1000005
                                                                 :end 1000007})]
                                 {:start 1000002
                                  :end 1000008})]}
                 (constantly [:span {}])) =>
 ["1" [:span {} "2" [:span {} "34"] "" [:span {} "56"] "7"] "89"])

;; ## Nodes Annotations

;; `tree-to-hiccup` takes an `annotate` function as parameter. This function
;; takes a node and returns a pair containing an element type and a map of
;; attributes for that node.

;; The function `annotate-node` provides the default functionality for this.
;; Given a node that doesn't have decorations, it returns `[:span {}]`, as the
;; default.
(fact
 (annotate-node `foo) => [:span {}])

;; The same is true for nodes with `:matches` decorations that do not contain
;; the `:html` key.
(fact
 (annotate-node (with-meta `foo
                  {:matches (atom {})})) => [:span {}])

;; However, if the `:html` key does exist and contains an `:id`, the `:id`
;; attribute is populated with its value.
(fact
 (annotate-node (with-meta `foo
                  {:matches (atom {:html {:id 7}})})) =>
 [:span {:id "7"}])

;; If the `:matches` contains any symbol keys (representing matched predicates),
;; the name of each such predicate becomes a class in the `:class` attribute.
(fact
 (annotate-node (with-meta `foo
                  {:matches (atom {`p1 {}
                                   `p2 {}})})) =>
 [:span {:class "p1 p2"}])