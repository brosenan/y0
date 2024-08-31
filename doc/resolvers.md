* [Resolvers](#resolvers)
  * [Qualified Name as Path](#qualified-name-as-path)
  * [Absolute Path from Prefix List](#absolute-path-from-prefix-list)
```clojure
(ns y0.resolvers-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.resolvers :refer :all]
            [y0.status :refer [ok unwrap-status ->s let-s]]
            [clojure.java.io :as io]))

```
# Resolvers

In the context of $y_0$'s [polyglot module system](polyglot_loaders.md),
resolvers are functions that resolve a module's `:path` based on a module's
`:name`. While not being language-specific, the behavior of the resolver may
change from language to language. For this reason, a resolver is provided
as part of the
[language map](polyglot_loaders.md#module-and-language-representation).

## Qualified Name as Path

Many module systems map qualified names of such as `foo.bar.baz` to paths
such as `foo/bar/baz.ext`, where `ext` is some fixed, language-dependent
file extension.

The function `qname-to-rel-path-resolver` takes a file extension string and
returns a function that converts a qualified name into a relative path,
given as a `java.io.File`.
```clojure
(fact
 (let [r (qname-to-rel-path-resolver "y2")]
   (r "foo.bar.baz") => {:ok (io/file "foo/bar/baz.y2")}))

```
## Absolute Path from Prefix List

Given a resolver that provides us with a relative path for our module, we
need a way to convert this into an absolute path of an existing file. One
common way of doing this is by having a list of possible prefixes (think,
`JAVA_PATH` or `PYTHONPATH`). This is an ordered list. The module system
is expected to try these prefixes one by one and return the first path that
resolves to an existing file.

`prefix-list-resolver` takes a sequence of prefixes (as strings) and a
relative-path resolver, and returns an absolute-path resolver.
```clojure
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
```

