(ns y0.resolvers-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.resolvers :refer :all]
            [y0.status :refer [ok unwrap-status ->s let-s]]
            [clojure.java.io :as io]))

;; # Resolvers

;; In the context of $y_0$'s [polyglot module system](polyglot_loaders.md),
;; resolvers are functions that resolve a module's `:path` based on a module's
;; `:name`. While not being language-specific, the behavior of the resolver may
;; change from language to language. For this reason, a resolver is provided
;; as part of the
;; [language map](polyglot_loaders.md#module-and-language-representation).

;; ## Qualified Name as Path

;; Many module systems map qualified names of such as `foo.bar.baz` to paths
;; such as `foo/bar/baz.ext`, where `ext` is some fixed, language-dependent
;; file extension.

;; The function `qname-to-rel-path-resolver` takes a file extension string and
;; returns a function that converts a qualified name into a relative path,
;; given as a `java.io.File`.
(fact
 (let [r (qname-to-rel-path-resolver "y2")]
   (r "foo.bar.baz") => (io/file "foo/bar/baz.y2")))
