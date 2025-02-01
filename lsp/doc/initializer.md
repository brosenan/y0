```clojure
(ns y0lsp.initializer-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [edamame.core :refer [parse-string-all]]
   [midje.sweet :refer [fact]]
   [y0.config :refer [*y0-path* lang-config-spec]]
   [y0.status :refer [ok]]
   [y0lsp.initializer :refer :all]))

```
# The Initializer

The initializer is a mechanism that bootstraps the server. It starts
with a [language config](../../doc/config.md) and ends up with a running
server, ready to take client requests.

The initializer uses _addons_ to customize its behavior (and consequently,
the behavior of the launguage server). Addons are functions from the server
context to itself, each making a different change (typically, adding things).

The process goes roughly as follows:

1. The context is initialized with the default [config
   spec](../../doc/config.md#generic-mechanism) assigned to the key
   `:config-spec`.
2. Addons are applied one by one. Some addons add options to the
   `:config-spec`, e.g., to introduce new types of parsers, resolvers, etc.
   Others may install new `:req-handlers` and `:notification-handlers` to
   implement new LSP services (this is often accompanied by updating
   `:server-capabilities`).
3. The language config is read and a [language
   map](../../doc/config.md#generating-a-language-map-from-config) is
   generated.
4. Based on the language map, a module loader function is constructed.
5. The module loader function is used to create a [workspace](workspace.md),
   that is stored in an atom under the key `:ws`.
6. A `lsp4clj` server is created and stored under `:server` in the context.
   Then it is started.

In the following sections we describe the building blocks of the
initialization process, and then put it all together.

## Language Map Creation

As described in the $y_0$ documentation, the goal of the lauguage config is
to [create a _language
map_](../../doc/config.md#generating-a-language-map-from-config). We augment
the original language map with a `:lss` ([language
stylesheet](language_stylesheet.md)), which we compile from the `:stylesheet`
key in the config.

To demonstrate this, we first introduce a function for reading the language
config from the root of the `y0` repo. 
```clojure
(defn read-lang-config []
  (-> "lang-conf.clj"
      io/resource
      io/file
      slurp
      parse-string-all
      first))

```
Next, we take our language config and override its `:stylesheet` attributes
(for both `y1` and `c0`). Then we test that we get a proper language map for
both languages.
```clojure
(fact
 (let [config (-> (read-lang-config)
                  (update "y1" assoc :stylesheet [{:foo 1}])
                  (update "c0" assoc :stylesheet [{:foo 2}]))
       lang-map (to-language-map lang-config-spec config)]
   (-> lang-map (get "y1") :parse) => fn?
   (-> lang-map (get "c0") :resolve) => fn?
   (let [f1 (-> lang-map (get "y1") :lss)
         f2 (-> lang-map (get "c0") :lss)
         some-node (with-meta [:some-node] {:matches (atom {})})]
     (f1 some-node :foo) => 1
     (f2 some-node :foo) => 2)))

```
## Module Loader Creation

A [module loader](../../doc/polyglot_loader.md#loading-a-single-module) has
been introduced as part of $y_0$'s [Polyglot
loader](../../doc/polyglot_loader.md).

We need a module loader as one of the two parameters to initialize a
[workspace](workspace.md#data-structure).

This loader, however, is a bit different than the one provided by the
polyglot loader. First, it does not return [status](../../doc/status.md) but
rather always succeeds.

Second, in addition to loading the module's `:statements`, it also creates an
`:index`, [mapping line numbers to sequences of tree nodes](tree_index.md)
and an empty `:errs` atom, for collecting evaluation errors.

The function `module-loader` takes a language-map and returns function
(loader) that takes a partial module (could be a `:path` only) and returns a
complete module, adding the missing keys.
```clojure
(fact
 (let [config (read-lang-config)
       lang-map (binding [*y0-path* [(-> "y0_test/"
                                         io/resource
                                         io/file)]]
                  (to-language-map lang-config-spec config))
       lang-map (update lang-map "c0" assoc :read (constantly "void foo() {}"))
       loader (module-loader lang-map)
       {:keys [path lang text statements deps index semantic-errs]}
       (loader {:path "/path/to/my-module.c0"})]
   path => "/path/to/my-module.c0"
   lang => "c0"
   text => "void foo() {}"
   statements => [[:func_def [:void_type]
                   (symbol "/path/to/my-module.c0" "foo") [:arg_defs]]]
   (-> deps first) => #(str/ends-with? % "/c0.y0")
   index => {1 [[:func_def [:void_type]
                 (symbol "/path/to/my-module.c0" "foo") [:arg_defs]]]}
   semantic-errs => #(instance? clojure.lang.IAtom %)))

```
If an error is reported by one of the operations, and `:err` entry is added
to the module to report it.

Please note the difference between `:err`, which holds loading errors (e.g.,
parsing errors) and `:semantic-errs`, which is an atom that contains
potential evaluation errors (i.e., semantic errors).

To demonstrate this, we repeat the previous example, but we replace the
`:parse` function in the `lang-map` with one that returns an error. We show
that the error is captured in the returned module.
```clojure
(fact
 (let [config (read-lang-config)
       lang-map (binding [*y0-path* [(-> "y0_test/"
                                         io/resource
                                         io/file)]]
                  (to-language-map lang-config-spec config))
       lang-map (update lang-map "c0" assoc :read (constantly "void foo() {}"))
       lang-map (update lang-map "c0" assoc :parse
                        (constantly {:err ["Some parse error"]}))
       loader (module-loader lang-map)
       {:keys [err]}
       (loader {:path "/path/to/my-module.c0"})]
   err => ["Some parse error"]))

```
## Module Evaluator Creation


`module-evaluator` takes a function that in production should be
`y0.rules/apply-statements` as its only parameter and returns a workspace
`eval` function that is based on it.

The function returned by `module-evaluator` calls the underlying statements
evaluation function with the module's `:statements`, the given predstore
(`ps`) and an empty `vars` map, and, assuming that all goes well, returns the
updated `ps`.
```clojure
(fact
 (let [apply-statements (fn [stmts ps vars]
                          (-> ps
                              (assoc :stmts stmts)
                              (assoc :vars vars)
                              ok))
       m {:statements ["These" "are" "statements"]
          :semantic-errs (atom nil)}
       ps {:foo :bar}
       eval-module (module-evaluator apply-statements)]
   (eval-module ps m) => {:foo :bar
                          :stmts ["These" "are" "statements"]
                          :vars {}}))
```

