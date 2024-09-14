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
   (r "foo.bar.baz") => {:ok (io/file "foo/bar/baz.y2")}))

;; ## Absolute Path from Prefix List

;; Given a resolver that provides us with a relative path for our module, we
;; need a way to convert this into an absolute path of an existing file. One
;; common way of doing this is by having a list of possible prefixes (think,
;; `JAVA_PATH` or `PYTHONPATH`). This is an ordered list. The module system
;; is expected to try these prefixes one by one and return the first path that
;; resolves to an existing file.

;; `prefix-list-resolver` takes a sequence of prefixes (as strings) and a
;; relative-path resolver, and returns an absolute-path resolver.
(fact
 (let [rrel (fn [x] (ok (io/file (str x ".foo"))))
       paths ["/foo" "/bar" "./baz"]
       r (prefix-list-resolver paths rrel)
       path1 (io/file "/foo/my-module.foo")
       path2 (io/file "/bar/my-module.foo")]
   (r "my-module") => {:ok path2}
   (provided
    (exists? path1) => false
    (exists? path2) => true)))

;; ## Reading Path Prefixes from an Environment Variable

;; It is common that for a given language, the list of prefixes is taken from
;; an environment variable.

;; `path-prefixes-from-env` takes the name of an environment variable, reads it
;; and returns its contents, split on the colon (`:`) character.
(fact
 (path-prefixes-from-env "Y0_PATH") => ["/foo" "/bar" "."]
 (provided
  (getenv "Y0_PATH") => "/foo:/bar:."))

;; The sequence is lazy, and access to the environment variable is only made
;; once the first element is being fected.
(fact
 (let [paths (path-prefixes-from-env "Y0_PATH")]
   (first paths) => "/foo"
   (provided
    (getenv "Y0_PATH") => "/foo:/bar:.")))


;; ## The $y_0$ Resolver

;; In $y_0$, a module `a.b.c` is found in file `some/path/a/b/c.y0`, where
;; `some/path` is a subset of the `Y0_PATH`, an ordered collection of base
;; paths that often comes from an environment variable of the same name.

;; The function `y0-resolver` takes a sequence of strings representing
;; `Y0_PATH` (which is potentially read from an environment variable of the
;; same name), and returns a resolver for $y_0$.
(fact
 (let [r (y0-resolver ["/foo" "/bar" "./baz"])
       path1 (io/file "/foo/a/b/c.y0")
       path2 (io/file "/bar/a/b/c.y0")
       path3 (io/file "./baz/a/b/c.y0")]
   (r "a.b.c") => {:ok path3}
   (provided
    (exists? path1) => false
    (exists? path2) => false
    (exists? path3) => true)))
