```clojure
(ns y0lsp.initializer-test
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [edamame.core :refer [parse-string-all]]
   [lsp4clj.lsp.requests :as lsp.requests]
   [lsp4clj.server :as server]
   [midje.sweet :refer [fact]]
   [y0.config :refer [*y0-path* lang-config-spec]]
   [y0.rules :refer [*error-target* *skip-recoverable-assertions*]]
   [y0.status :refer [ok]]
   [y0lsp.addon-utils :refer [add-notification-handler add-req-handler
                              register-addon]]
   [y0lsp.initializer :refer :all]
   [y0lsp.location-utils :refer [to-lsp-pos]]
   [y0lsp.server :refer [register-notification register-req]]
   [y0lsp.tree-index-test :refer [extract-marker]]
   [y0lsp.workspace :refer [add-module eval-with-deps]]))

```
# The Initializer

The initializer is a mechanism that bootstraps the server. It starts
with a [language config](../../doc/config.md) and ends up with a running
server, ready to take client requests.

The initializer uses _addons_ to customize its behavior (and consequently,
the behavior of the launguage server). Addons are functions from the server
context to itself, each making a different change (typically, adding things).

The process goes roughly as follows:

1. The context is initialized with `:config-spec` containing the default
   [config spec](../../doc/config.md#generic-mechanism), `:y0-path`
   containing a list of paths where $y_0$ files can be found and `:config`
   containing the language config.
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
  (-> "lang-conf.edn"
      io/resource
      io/file
      slurp
      parse-string-all
      first))

```
Next, we take our language config and override its `:stylesheet` attributes
(for both `y1` and `c0`).
```clojure
(def lang-config-with-style
  (-> (read-lang-config)
      (update "y1" assoc :stylesheet [{:foo 1}])
      (update "c0" assoc :stylesheet [{:foo 2}])))

```
The function `create-language-map` takes a server context containing
`:config-spec` and `:config`, and adds `:lang-map`, containing a languages
map based on the config and the spec.
```clojure
(fact
 (let [ctx (-> {:config lang-config-with-style
                :config-spec lang-config-spec}
               create-language-map)
       lang-map (:lang-map ctx)]
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

The function `module-loader` takes a server context containing a `:lang-map`
and returns function (loader) that takes a partial module (could be a `:path`
only) and returns a complete module, adding the missing keys.
```clojure
(fact
 (let [ctx (binding [*y0-path* [(-> "y0_test/"
                                    io/resource
                                    io/file)]]
             (-> {:config (read-lang-config)
                  :config-spec lang-config-spec}
                 create-language-map
                 (update-in [:lang-map "c0"]
                            assoc :read (constantly "void foo() {}"))))
       loader (module-loader ctx)
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
 (let [ctx (binding [*y0-path* [(-> "y0_test/"
                                    io/resource
                                    io/file)]]
             (-> {:config (read-lang-config)
                  :config-spec lang-config-spec}
                 create-language-map
                 (update-in [:lang-map "c0"]
                            assoc :read (constantly "void foo() {}"))
                 (update-in [:lang-map "c0"]
                            assoc :parse 
                            (constantly {:err ["Some parse error"]}))))
       loader (module-loader ctx)
       {:keys [err]}
       (loader {:path "/path/to/my-module.c0"})]
   err => ["Some parse error"]))

```
## Module Evaluator Creation


`module-evaluator` takes a function that in production should be
`y0.rules/apply-statements` as its only parameter and returns a workspace
`eval` function that is based on it.

The function returned by `module-evaluator` calls the `apply-statements` with
the module's `:statements`, the given predstore (`ps`) and an empty `vars`
map, and, assuming that all goes well, returns the updated `ps`.
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
### Error Handling

We use $y_0$'s [mechanism for recoverable
errors](/doc/testing.md#recoverable-and-unrecoverable-assertions) to collect
errors. To do this, the module evaluator calls its `apply-statements` after
binding two parameters.

First, `*error-target*` is set to the module's `:semantic-errs` atom, which
is cleared before the call. This allows the $y_0$'s implementation to
populate it with errors and move on, rather than return an error status.
```clojure
(fact
 (let [apply-statements (fn [_stmts ps _vars]
                          (swap! *error-target* conj ["A new error"])
                          (-> ps (assoc :new :baz) ok))
       m {:statements ["These" "are" "statements"]
          :semantic-errs (atom [["Some previous error"]])}
       ps {:foo :bar}
       eval-module (module-evaluator apply-statements)]
   (eval-module ps m) => {:foo :bar
                          :new :baz}
   @(:semantic-errs m) => [["A new error"]]))

```
Second, we bind `*skip-recoverable-assertions*` to `true` unless the module
is marked with `:is-open`. This is meant to distinguish between modules that
are opened by the client and for which we need to provide a full account of
all errors, and modules that are only dependencies of such modules, where we
wish to skip unnecessary checks and only get the definitions.

We demonstrate this by creating three modules, one with `:is-open true`, one
with `:is-open false` and one without `:is-open`. The `apply-statements`
function will then place the value of `*skip-recoverable-assertions*` to the
predstore. We will check that in all but the first module,
`*skip-recoverable-assertions*` is set to `true`.
```clojure
(fact
 (let [apply-statements (fn [_stmts ps _vars]
                          (-> ps
                              (assoc :skip *skip-recoverable-assertions*)
                              ok))
       m1 {:is-open true
           :semantic-errs (atom nil)}
       m2 {:is-open false
           :semantic-errs (atom nil)}
       m3 {:semantic-errs (atom nil)}
       ps {}
       eval-module (module-evaluator apply-statements)]
   (eval-module ps m1) => {:skip false}
   (eval-module ps m2) => {:skip true}
   (eval-module ps m3) => {:skip true}))

```
Finally, we discuss what happens in case of an non-recoverable error. In such
a case, `apply-statements` will return an `:err` status. The module evaluator
function will in this case add this error to the `:semantic-errs` atom and
return the original `ps`.
```clojure
(fact
 (let [apply-statements (fn [_stmts ps _vars]
                          (swap! *error-target* conj ["Some recoverable error"])
                          {:err ["Non-recoverable error"]})
       m {:statements ["These" "are" "statements"]
          :semantic-errs (atom [["Some previous error"]])}
       ps {:foo :bar}
       eval-module (module-evaluator apply-statements)]
   (eval-module ps m) => {:foo :bar}
   @(:semantic-errs m) => [["Non-recoverable error"] 
                           ["Some recoverable error"]]))

```
## Workspace Initialization

In the previous sections we described the building blocks needed to construct
a workspace in the server context. Here we use these pieces to construct the
workspace itself.

The function `create-workspace` takes a context containing `:config`,
`:config-spec` and `:y0-path` and returns it, adding the key `:ws`,
containing an atom containing a [workspace](workspace.md). We then check that
the module's `:ps` contains a `function` named `foo`.
```clojure
(fact
 (let [{:keys [ws] :as ctx} (-> {:config (read-lang-config)
                                 :config-spec lang-config-spec
                                 :y0-path [(-> "y0_test/" io/resource io/file)]}
                                create-workspace)]
   ws => #(instance? clojure.lang.IAtom %)
   (swap! ws add-module {:path "/path/to/module.c0"
                         :text "void foo() {}"
                         :is-open true})
   (swap! ws eval-with-deps "/path/to/module.c0")
   (let [c0ns (-> "y0_test/c0.y0" io/resource io/file .getAbsolutePath)]
     (-> ctx :ws deref :ms (get "/path/to/module.c0")
         :cache :ps (get {:arity 3 :name (str c0ns "/function")})
         (get {:symbol "/path/to/module.c0/foo"})) => fn?)))

```
## Context Initialization

`initialize-context` takes a language config, a `y0-path` and a sequence of
addon functions, and returns an initialized context that includes a `:ws`.

In the following example we use `initialize-context` to create a context with
two addons. The first addon creates a new type of parser (the `:foo-parser`),
and the other handles a `test/foo` notification.
```clojure
(fact
 (let [add-foo-parser #(update-in % [:config-spec :parser]
                                  assoc :foo {:func (constantly [["foo"] []])
                                              :args []})
       handle-foo-notification (add-notification-handler "test/foo"
                                                         (fn [_ctx _res]
                                                           "foo"))
       ctx (initialize-context (read-lang-config)
                               [(-> "y0_test/" io/resource io/file)]
                               [add-foo-parser
                                handle-foo-notification])]
   (:ws ctx) => #(instance? clojure.lang.IAtom %)
   (-> ctx :config-spec :parser :foo :func) => fn?
   (-> ctx :notification-handlers (get "test/foo") first) => fn?))

```
## Starting a Server

Once a context exists, we can start a server. `start` takes a server context
and a [lsp4clj](https://github.com/clojure-lsp/lsp4clj) server (of any type)
and does the following:

1. Adds the server as `:server` to the context.
2. Adds a `:notify` function to the context, which sends notifications to the
   server.
3. Starts the server, given the context.

It returns the `done` future [returned when starting the
server](https://github.com/clojure-lsp/lsp4clj?tab=readme-ov-file#start-and-stop-a-server).

In the following example we create a `test/didFoo` notification handler that
checks whether the `:server` in the context is the same as the server passed
to `start`, then it delivers the notification to a promise (so that it can be
checked by the test), and finally it uses `:notify` to send a `test/didBar`
notification back to the server.
```clojure
(register-notification "test/didFoo")
(fact
 (let [input-ch (async/chan 3)
       output-ch (async/chan 3)
       server (server/chan-server {:output-ch output-ch
                                   :input-ch input-ch})
       ctx (initialize-context
            (read-lang-config)
            [(-> "y0_test/" io/resource io/file)]
            [#(assoc % :my-promise (promise))
             (add-notification-handler "test/didFoo"
                                       (fn [{:keys [my-promise notify] :as ctx} notif]
                                         (when (= (:server ctx) server)
                                           (notify "test/didBar" notif)
                                           (deliver my-promise notif))))])
       done (start ctx server)]
   (async/put! input-ch
               (lsp.requests/notification "test/didFoo" {:foo :bar}))
   @(:my-promise ctx) => {:foo :bar}
   (let [{:keys [params method]} (async/<!! output-ch)]
     params  => {:foo :bar}
     method => "test/didBar")
   (server/shutdown server)
   @done))

```
## Testing Support

To test addons, we want an easy way to set up a simulated server that
features everything an addon can interact with. We need to initialize a
context containing a workspace, add files to that workspace and send and
receive requests and notifications.

The following function is intended to do this.
```clojure
(defn addon-test [& addons]
  (let [notif-handlers (atom {})
        addons (map #(if (string? %)
                       (if (contains? @y0lsp.addon-utils/addons %)
                         (get @y0lsp.addon-utils/addons %)
                         (throw (Exception. (str "Addon " % " not found"))))
                       %) addons)
        ctx (-> (initialize-context
                 (read-lang-config)
                 [(-> "y0_test/" io/resource io/file)]
                 addons)
                (assoc :notify (fn [method params]
                                 (when (contains? @notif-handlers method)
                                   ((get @notif-handlers method) params)))))
        add-module (fn [path text]
                     (swap! (:ws ctx) add-module {:path path
                                                  :text text
                                                  :is-open true})
                     (swap! (:ws ctx) eval-with-deps path))]
    {:send (fn [name req]
             (server/receive-request name ctx req))
     :notify (fn [name notif]
               (server/receive-notification name ctx notif))
     :add-module add-module
     :add-module-with-pos (fn [path text]
                            (let [[text pos] (extract-marker text "$")]
                              (add-module path text)
                              {:text-document {:uri (str "file://" path)}
                               :position (to-lsp-pos pos)}))
     :on-notification (fn [method handler]
                        (swap! notif-handlers assoc method handler))
     :shutdown (fn [])}))

```
`addon-test` takes zero or more addons as arguments and returns a map
containing functions for testing them. `shutdown` shuts the given server down
and must be called at the end of each test.
```clojure
(register-req "test/foo" any?)
(fact
 (let [{:keys [shutdown]}
       (addon-test (add-req-handler "test/foo" (fn [_ctx req]
                                                 req)))]
   shutdown => fn?
   (shutdown)))

```
**Note:** At the moment, the test server is only simulated, i.e., there is no
server to shut down and therefore `shutdown` is no-op. However, to allow for
this to become a real server, we need tests to end with `shutdown`.

### Requests and Notifications

`send` takes a request name and body, sends it to the (simulated) server and
returns the response.
```clojure
(fact
 (let [{:keys [send shutdown]}
       (addon-test 
        #(assoc % :foo [1 2 3 4])
        (add-req-handler "test/foo" (fn [{:keys [foo]} req]
                                      {:my-req req
                                       :foo-from-ctx foo})))]
   (send "test/foo" {:val 3}) => {:my-req {:val 3}
                                  :foo-from-ctx [1 2 3 4]}
   (shutdown)))

```
`notify` takes a notification name and body and sends it to the server,
invoking all notification handlers installed by the addons.
```clojure
(fact
 (let [p1 (promise)
       p2 (promise)
       {:keys [notify shutdown]}
       (addon-test
        #(assoc % :my-promise p1)
        #(assoc % :my-other-promise p2)
        (add-notification-handler "test/didFoo"
                                  (fn [{:keys [my-promise]} req]
                                    (deliver my-promise req)))
        (add-notification-handler "test/didFoo"
                                  (fn [{:keys [my-other-promise]} req]
                                    (deliver my-other-promise req))))]
   (notify "test/didFoo" {:bar 42})
   (deref p1 100 :timeout) => {:bar 42}
   (deref p2 100 :timeout) => {:bar 42}
   (shutdown)))

```
### Workspace Manipulation

`add-module` takes a path and contents of a module and loads it.

In the following example we define an addon that, when requested, returns the
number of semantic errors for a given module. We create two modules, one
semantically valid and another with an error and check that we get the
correct number.
```clojure
(fact
 (let [{:keys [add-module send shutdown]}
       (addon-test
        (add-req-handler "test/foo" (fn [{:keys [ws]} {:keys [path]}]
                                      {:err-count (-> @ws :ms (get path)
                                                      :semantic-errs deref
                                                      count)})))]
   (add-module "/path/to/m1.c0" "void foo() {}")
   (add-module "/path/to/m2.c0" "void foo() { int32 a = b; }")
   (send "test/foo" {:path "/path/to/m1.c0"}) => {:err-count 0}
   (send "test/foo" {:path "/path/to/m2.c0"}) => {:err-count 1}
   (shutdown)))

```
Sometimes we want to create a module for a test, and need a pointer to a
specific point within this module.

`add-module-with-pos` takes a path and contents of a new module, where the
contents contains the character `$` somewhere in the text. In addition to
adding the module (calling `add-module`) with the `$` removed, the function
returns a
[TextDocumentPositionParams](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentPositionParams)
that points to the location of the `$`.

In the following example we create an addon that returns the text contents of
a given module. We use `add-module-with-pos` to create a module and check the
position returned by it. Then we use the addon to see that the module has
indeed been added to the workspace, with the `$` removed.
```clojure
(fact
 (let [{:keys [add-module-with-pos send shutdown]}
       (addon-test
        (add-req-handler "test/foo" (fn [{:keys [ws]} {:keys [path]}]
                                      {:text (-> @ws :ms (get path) :text)})))]
   (add-module-with-pos "/path/to/m.c0" "void $foo() {}") =>
   {:text-document {:uri "file:///path/to/m.c0"}
    :position {:line 0 :character 5}}
   (send "test/foo" {:path "/path/to/m.c0"}) => {:text "void foo() {}"}
   (shutdown)))

```
### Working with Registered Addons

`addon-test` can test [registered addons](addon_utils.md#addon-registration)
by using the addon's registered name instead of a function as argument.

In the following example, we register an addon named `foo` and exersize it.
```clojure
(register-addon "foo" (->> (fn [_ctx {:keys [x]}]
                             {:y (+ x 2)})
                           (add-req-handler "test/foo")))
(register-req "test/foo" any?)

(fact
 (let [{:keys [send shutdown]}
       (addon-test "foo")]
   (send "test/foo" {:x 3}) => {:y 5}
   (shutdown)))

```
### Testing for Sent Notifications

Addons can send notifications to the server. In order to test this behavior,
the key `:on-notification` is bound to a function that takes a notification
method and a handler, and invokes the handler on calls to the `:notify`
function with that method.

We demonstrate this using a test addon which listens to a notification and
relays this notification back to the server. We hook to the latter and see
that it is invoked.
```clojure
(fact
 (let [ret (atom nil)
       {:keys [notify on-notification shutdown]}
       (addon-test
        (add-notification-handler "test/didFoo"
                                  (fn [{:keys [notify]} params]
                                    (notify "test/didBar" params))))]
   (on-notification "test/didBar" (fn [params]
                                    (reset! ret params)))
   (notify "test/didFoo" {:bar 42})
   @ret => {:bar 42}
   (shutdown)))
```

