(ns y0lsp.addons.sem-tokens-test
  (:require
   [midje.sweet :refer [fact =>]]
   [y0lsp.addons.sem-tokens :refer :all]
   [y0lsp.all-addons]
   [y0lsp.initializer-test :refer [addon-test]]))

;; # Semantic Tokens

;; [Semantic
;; tokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens)
;; is probably one of the most complicated features in the LSP. The reason for
;; this is that it involves a high rate of information sent from the server to
;; the client for every change. This, in turn, means that if the feature is not
;; thought of carefully, it may cause performance issues.

;; To minimize the potential performance issues, the designers of the LSP have
;; decided to use a binary-like representation for the bulk of the data (namely,
;; the tokens list), This representation requires the client and the server to
;; agree on numeric representation for token types as well as on whether the
;; tokens will be sent for a complete module each time, or just for a requested
;; range.

;; ## Integer Encoding for Tokens

;; The [LSP
;; spec](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens)
;; describe an encoding that encodes each token as a sequence of five integers,
;; which are then concatenated together to get the tokens `:data`.

;; Here we build this sequence step by step.

;; ### Token Type Encoding

;; Token types are encoded as integers. The server is free to choose the
;; mapping, but it has to publish it, as an order list of strings, as part of
;; the
;; [SemanticTokensOptions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokensOptions)
;; in the server capabilities.

;; The function `token-type-encoder` receives a list of strings and returns a
;; function that translates strings into their index in the list.
(fact
 (let [f (token-type-encoder ["function" "variable" "type"])]
   (f "function") => 0
   (f "type") => 2
   (f "not-on-the-list") => -1))

;; ### Relative Position

;; One of the tricks the LSP pulls in order to reduce the volume of data being
;; sent is to reduce the magnitude of the values being sent by using _relative
;; positions_.

;; Relative positions are computed as the difference in row and column number
;; between the start of the previous token and that of the current one. For the
;; first token, the absolute position (zero-based) is used.

;; Additionally, instead of representing the end position of a token, the size
;; of the token is represented as the third integer.

;; As a first step towards this, `pos-diff` takes two LSP
;; [positions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#position)
;; and returns a third position, representing their difference.

;; Within the same line, the `:line` is `0` and the `:character` is the
;; difference between the `:character`s in the two arguments.
(fact
 (pos-diff {:line 3 :character 10}
           {:line 3 :character 4}) => {:line 0 :character 6})

;; If the lines differ, the `:line` is the line difference and the `:character`
;; is the `:character` of the first argument.
(fact
 (pos-diff {:line 4 :character 4}
           {:line 3 :character 10}) => {:line 1 :character 4})

;; Finally, the function `rel-pos-encoder` takes no arguments and generates a
;; function that takes a $y_0$ start position (integer) of a token and its
;; length, and returns a three-vector with the delta-line, delta-character and
;; length.
(fact
 ;; xxxx_yyy_zz
 ;; __aa
 (let [f (rel-pos-encoder)]
   (f 1000001 4) => [0 0 4]
   (f 1000006 3) => [0 5 3]
   (f 1000010 2) => [0 4 2]
   (f 2000005 2) => [1 4 2]))

;; ### Tokens in a Parse Tree

;; This addon needs to traverse the parse tree of a given module and classify
;; its identifiers (symbols). This section describes how we do this.

;; #### Symbol Real Location

;; One of the challenges we have to face is that in some cases (e.g.,
;; [Instaparse-based parsers](../../doc/instaparser.md)) the location assigned
;; to a symbol often includes the preceding whitespace. This is problematic
;; since the LSP does not allow for tokens to span more than a single line.

;; To fix this, we calculate a symbol's start position by taking its `:end` and
;; subtracting the length of the token.

;; As a first step, the function `symbol-len` returns the length of the `name`
;; of a symbol.
(fact
 (symbol-len 'foo) => 3
 (symbol-len 'abcd/ef) => 2)

;; As a second step, the function `symbol-start` subtracts this length from the
;; symbol's `:end` metadata.
(fact
 (let [sym (with-meta 'abcdefg/foo {:end 2000004})]
   (symbol-start sym) => 2000001))

;; #### Tree Traversal

;; Given a parse tree (e.g., the `:statements` attribute of a module), the
;; function `all-symbols` returns a lazy sequence of all its symbols.

;; Given a symbol, it returns a sequence containing that one symbol.
(fact
 (all-symbols 'foo) => ['foo])

;; Given a leaf that is not a symbol, it returns an empty sequence.
(fact
 (all-symbols 123) => [])

;; For anything sequential, it extracts all symbols from within.
(fact
 (all-symbols [123 'foo 456 'bar]) => ['foo 'bar])

;; Nested structures are supported as well.
(fact
 (all-symbols [123 'foo [456 ['bar] 'baz]]) => ['foo 'bar 'baz])

;; ### The Encoding Function

;; Putting it all together, the function `encode-symbols` takes a parse tree
;; (e.g., a module's `:statements`) and a token classification function, and
;; returns a sequence of integers, corresponding with the `:data` field of the
;; [SemanticTokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokens)
;; message.

;; The token classification function takes a symbol and returns a vector with
;; two integers, one representing the token type and the other representing its
;; modifiers.
(fact
 ;; xxxx_yyy_zz
 ;; __aa
 (let [tree [:foo
             (with-meta 'xxxx {:start 1000001
                               :end 1000005})
             (with-meta 'yyy {:start 1000005
                              :end 1000009})
             [:bar (with-meta 'zz {:start 1000009
                                   :end 1000012})
              [:baz (with-meta 'aa {:start 1000012
                                    :end 2000007})]]]]
   (encode-symbols tree (constantly [7 0])) =>
   [0 0 4 7 0
    0 5 3 7 0
    0 4 2 7 0
    1 4 2 7 0]))

;; #### Ignoring Symbols

;; We sometimes wish to ignore certain symbols, e.g., when they don't have
;; special semantic information. We do that by allowing the classification
;; function to return `nil` in some cases, and have `encode-symbols` ignore
;; these symbols. The relative position needs to be maintained.

;; In the following example we repeat the previous example, but classify `yyy`
;; as `nil`. The coordinates for `zz` are then shifted to be the offset from the
;; beginning of `xxxx`.
(fact
 ;; xxxx_____zz
 ;; __aa
 (let [tree [:foo
             (with-meta 'xxxx {:start 1000001
                               :end 1000005})
             (with-meta 'yyy {:start 1000005
                              :end 1000009})
             [:bar (with-meta 'zz {:start 1000009
                                   :end 1000012})
              [:baz (with-meta 'aa {:start 1000012
                                    :end 2000007})]]]]
   (encode-symbols tree #(if (= % 'yyy) nil [7 0])) =>
   [0 0 4 7 0
    0 9 2 7 0
    1 4 2 7 0]))

;; ## Capabilities Exchange

;; The capability exchange for semantic tokens is used to agree on the type of
;; requests that can be made (range or full) and to agree on the set of semantic
;; types.

;; This addon currently supports a subset of the capabilities provided by the
;; protocol:

;; 1. We only support `full` requests. `range` and `full/delta` requests are not
;;    supported.
;; 2. We return the full list of `:token-types` in the
;;    [SemanticTokensLegend](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokensLegend)
;;    and to not try to restrict the list to only the types used by the language
;;    definition.
;; 3. Motifiers are not supported.

;; In the following example we perform an `initialize` request, sending client
;; capabilities that contain three types of tokens. The response will contain
;; the same types of tokens in the same order as the `SemanticTokensLegend`.
(fact
 (let [{:keys [send shutdown]} (addon-test "init" "sem-tokens")
       {:keys [capabilities]}
       (send "initialize" {:capabilities
                           {:text-document
                            {:semantic-tokens
                             {:requests {:range true
                                         :full {:delta true}}
                              :token-types ["foo" "bar" "baz"]}}}})]
   (-> capabilities :semantic-tokens-provider) =>
   {:legend {:token-types ["foo" "bar" "baz"]
             :token-modifiers []}
    :full true}
   (shutdown)))