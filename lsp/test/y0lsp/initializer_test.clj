(ns y0lsp.initializer-test
  (:require
   [midje.sweet :refer [fact]]
   [y0.config :refer [lang-config-spec]]
   [y0lsp.initializer :refer :all]))

;; # The Initializer

;; The initializer is a mechanism that bootstraps the server context. It starts
;; with a [language config](../../doc/config.md) and ends up with a running
;; server, ready to take client requests.

;; The initializer uses _addons_ to customize its behavior (and consequently,
;; the behavior of the launguage server). These come in two flavors:

;; 1. _Config addons_, which add options to the language config and is a way to
;;    introduce new types of parsers, resolvers etc, and
;; 2. _Feature addons_, which add support for new features (e.g., handle new
;;    types of requests or notifications).

;; We give a bottom-up description of the initializer in the following sections.

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
