(ns y0lsp.addons.ws-folders-test
  (:require
   [clojure.java.io :as io]
   [midje.sweet :refer [fact provided =>]]
   [y0.resolvers :refer [exists?]]
   [y0.status :refer [unwrap-status]]
   [y0lsp.addon-utils :refer [add-req-handler]]
   [y0lsp.addons.ws-folders :refer :all]
   [y0lsp.all-addons]
   [y0lsp.initializer-test :refer [addon-test]]
   [y0lsp.server :refer [register-req]]))

;; # Workspace Folders

;; In order for the server to be able to resolve dependency modules, it needs to
;; have a sense of where files are located on disk.

;; The LSP addresses this by providing `:workspace-folders` in the [`initialize`
;; request](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#initializeParams).

;; The `ws-folders` addon listens to the `y0lsp/initialized` notification and
;; updates the root paths used by [resolvers](../../doc/resolvers.md).

;; To demonstrate this we create a test addon that extracts the `:lang-map` form
;; the context and applies the `:resolve` function on the input. Then we send an
;; `initialize` request, providing some `:workspace-folders` and use the `test`
;; request to resolve some module.
(register-req "test" any?)
(fact
 (let [{:keys [send shutdown]}
       (addon-test "ws-folders" "init"
                   (->> (fn [{:keys [lang-map]} {:keys [name]}]
                          (let [{:keys [resolve]} (get lang-map "c0")]
                            (str (unwrap-status (resolve name)))))
                        (add-req-handler "test")))
       path1 (io/file "/foo/hello/world.c0")
       path2 (io/file "/bar/hello/world.c0")]
   (send "initialize" {:workspace-folders [{:uri "file:///foo"}
                                           {:uri "file:///bar"}]})
   (send "test" {:name "hello.world"}) => "/bar/hello/world.c0"
   (provided
    (exists? path1) => false
    (exists? path2) => true)
   (shutdown)))

;; Older clients may be populating the now-deprecated `:root-uri` property
;; instead of `:workspace-folders`. We support it, with a lower priority to that
;; of `:workspace-folders`.
(fact
 (let [{:keys [send shutdown]}
       (addon-test "ws-folders" "init"
                   (->> (fn [{:keys [lang-map]} {:keys [name]}]
                          (let [{:keys [resolve]} (get lang-map "c0")]
                            (str (unwrap-status (resolve name)))))
                        (add-req-handler "test")))
       path1 (io/file "/foo/hello/world.c0")
       path2 (io/file "/bar/hello/world.c0")
       path3 (io/file "/baz/hello/world.c0")]
   (send "initialize" {:workspace-folders [{:uri "file:///foo"}
                                           {:uri "file:///bar"}]
                       :root-uri "file:///baz"})
   (send "test" {:name "hello.world"}) => "/baz/hello/world.c0"
   (provided
    (exists? path1) => false
    (exists? path2) => false
    (exists? path3) => true)
   (shutdown)))