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
