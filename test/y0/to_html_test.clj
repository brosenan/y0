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