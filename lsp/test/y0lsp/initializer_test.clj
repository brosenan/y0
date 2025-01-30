(ns y0lsp.initializer-test
  (:require
   [midje.sweet :refer [fact]]
   [y0.config :refer [lang-config-spec]]
   [y0lsp.initializer :refer :all]))

;; # The Initializer

;; The initializer is a mechanism that bootstraps the server. It starts
;; with a [language config](../../doc/config.md) and ends up with a running
;; server, ready to take client requests.

;; The initializer uses _addons_ to customize its behavior (and consequently,
;; the behavior of the launguage server). Addons are functions from the server
;; context to itself, each making a different change (typically, adding things).

;; The process goes roughly as follows:

;; 1. The context is initialized with the default [config
;;    spec](../../doc/config.md#generic-mechanism) assigned to the key
;;    `:config-spec`.
;; 2. Addons are applied one by one. Some addons add options to the
;;    `:config-spec`, e.g., to introduce new types of parsers, resolvers, etc.
;;    Others may install new `:req-handlers` and `:notification-handlers` to
;;    implement new LSP services (this is often accompanied by updating
;;    `:server-capabilities`).
;; 3. The language config is read and a [language
;;    map](../../doc/config.md#generating-a-language-map-from-config) is
;;    generated.
;; 4. Based on the language map, a module loader function is constructed.
;; 5. The module loader function is used to create a [workspace](workspace.md),
;;    that is stored in an atom under the key `:ws`.
;; 6. A `lsp4clj` server is created and stored under `:server` in the context.
;;    Then it is started.

;; In the following sections we describe the building blocks of the
;; initialization process, and then put it all together.

;; ## Language Map Creation

;; As described in the $y_0$ documentation, the goal of the lauguage config is
;; to [create a _language
;; map_](../../doc/config.md#generating-a-language-map-from-config). We augment
;; the original language map with a `:lss` ([language
;; stylesheet](language_stylesheet.md)), which we compile from the `:stylesheet`
;; key in the config.

;; In the following example we build a simple language config that contains a
;; `:stylesheet` and translate it to a language map using the `to-language-map`
;; function.
(fact
 (let [config {"y1" {:parser :edn
                     :root-refer-map :root-symbols
                     :root-symbols '[defn deftype declfn defclass definstance]
                     :root-namespace "y1.core"
                     :resolver :prefix-list
                     :relative-path-resolution :dots
                     :file-ext "y1"
                     :y0-modules ["y1"]
                     :path-prefixes :from-env
                     :path-prefixes-env "Y1-PATH"
                     :reader :slurp
                     :decorate :true
                     :stylesheet [{:foo 1}]}
               "y2" {:parser :edn
                     :root-refer-map :root-symbols
                     :root-symbols '[defn deftype declfn defclass definstance]
                     :root-namespace "y2.core"
                     :resolver :prefix-list
                     :relative-path-resolution :dots
                     :file-ext "y2"
                     :y0-modules ["y2"]
                     :path-prefixes :from-env
                     :path-prefixes-env "Y2-PATH"
                     :reader :slurp
                     :decorate :true
                     :stylesheet [{:foo 2}]}}
       lang-map (to-language-map lang-config-spec config)]
   (-> lang-map (get "y1") :parse) => fn?
   (-> lang-map (get "y2") :resolve) => fn?
   (let [f1 (-> lang-map (get "y1") :lss)
         f2 (-> lang-map (get "y2") :lss)]
     (f1 (with-meta [:some-node] {:matches (atom {})}) :foo) => 1
     (f2 (with-meta [:some-node] {:matches (atom {})}) :foo) => 2)))
