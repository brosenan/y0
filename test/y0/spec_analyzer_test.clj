(ns y0.spec-analyzer-test
  (:require [midje.sweet :refer [fact => throws provided anything]]
            [y0.spec-analyzer :refer :all]
            [y0.status :refer [ok]]))

;; # Analyzing Language Specs

;; Language specs are a way to develop well tested $y_0$ language definitions,
;; while creating a human-readable definition of the language, a Markdown file
;; (`.md`), that contains positive and some negative examples of code in the
;; language being defined.

;; The analyzer described here supports these specs by testing them, making sure
;; that everything that all positive examples succeed and all negative examples
;; fail for the right reason.

;; We recommend growing the spec and the definition together, following a
;; test-drive approach (write something in the spec, expect it to fail, update
;; the definition, expect the spec to pass).


;; In this document we build the spec analyzer step-by-step. If you are
;; interested in the final result (how to write a spec), feel free to jump to
;; [this section](#language-spec-analysis).

;; ## Line-Based Processing

;; To be able to process Markdown files, we need to develop an easy way to
;; process text files, line by line, maintaining state between lines.

;; To do so, we follow a state-machine model. The state-machine is defined as a
;; map from state (keyword) to a vector of transitions. Each transition is
;; represented as a map that is semantically a record, with the following
;; fields:

;; 1. `:pattern`: A regex to match a line, triggering the rule.
;; 2. `:transition`: A keyword representing the next state, given the trigger.
;; 3. `:update-fn`: A function which updates the state value. This function
;;    takes the previous state value and the line's matches, as returned by the
;;    `:pattern`, and returns the new state value.

;; Let us consider the following state-machine example, which we will use to
;; demonstrate this mechanism. It is a state-machine for Markdown files, which
;; collects all the code-snippets in the markdown into a vector.
(def statemachine-example
  {:init [;; Start a code-block
          {:pattern #"```(.*)"
           :transition :code
           :update-fn (fn [v [_line lang]]
                        (-> v
                            (assoc :current [])
                            (update :languages #(conj % lang))))}]
   :code [;; At the end of the code-block, append the current code block to
          ;; :code-blocks
          {:pattern #"```"
           :transition :init
           :update-fn (fn [v _matches]
                        (-> v
                            (update :code-blocks #(conj % (:current v)))
                            (dissoc :current)))}
          ;; In any line within the code block, append the contents to :current
          {:update-fn (fn [v [line]]
                        (update v :current #(conj % line)))}]})

;; In the following we build this functionality step by step.

;; ### Finding a Matching Transition

;; Given a specific state and a line, how do we choose the transition to be
;; applied (if any)?

;; `find-transition` takes a state-spec (vector of transitions) and a line, and
;; returns the matching pattern, if any, and the match, as returned by the
;; regex.

;; If no pattern matches, it returns `[{} nil]`.
(fact
 (find-transition [{:pattern #"^```"
                    :transition :foo}
                   {:pattern #"^#+"
                    :transition :bar}] "hello, world") =>
 [{} nil])

;; If a pattern matches the line, the respective transition is returned, with
;; the `:pattern` removed.
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}] "### Hello, World") =>
 [{:transition :bar} ["### Hello, World" "Hello, World"]])

;; If a transition has no `:pattern`, it matches any line.
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}
                   {:transition :baz}] "hello, world") =>
 [{:transition :baz} ["hello, world"]])

;; If more than one transition matches, the first is returned.
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}
                   {:transition :baz}] "```c++") =>
 [{:transition :foo} ["```c++" "c++"]])

;; ### Stepwise Activation

;; The function `apply-line` takes a state-machine, a state and a line and
;; returns the state, after applying the line. The state is a map which contains
;; a `:state` key, which corresponding value is the state keyword.

;; If None of the transitions hold for the line, the state is unchanged.
(fact
 (apply-line statemachine-example {:state :init
                                   :foo :bar}
             "Some uninteresting line") => {:foo :bar
                                            :state :init})

;; If one pattern matches, `:transition` and `:update-fn` are applied.
(fact
 (apply-line statemachine-example {:foo :bar
                                   :state :init}
             "```c++") => {:foo :bar
                           :current []
                           :languages ["c++"]
                           :state :code}
 (apply-line statemachine-example {:current []
                                   :state :code}
             "println('hello, world')") =>
 {:current ["println('hello, world')"]
  :state :code})

;; If the state-machine contains a state `:any`, its transitions are considered
;; regardless of the current state. This is useful for things like counting
;; line numbers, which should be done at any state.
(fact
 (let [sm (assoc statemachine-example
                 :any
                 [{:update-fn (fn [v _matches]
                                (update v :linenum (fnil inc 0)))}])]
   (apply-line sm {:foo :bar
                   :state :init}
               "```c++") => {:foo :bar
                             :current []
                             :languages ["c++"]
                             :linenum 1
                             :state :code}))

;; ### Processing a Complete File

;; Given a state-machine definition, an intial state and the contents of an
;; input file given as a sequence of lines, `process-lines` will return the
;; state after processing the entire file.
(fact
 (let [file ["Hello"
             "```c++"
             "void foo() {"
             "}"
             "```"
             "Some text..."
             "```java"
             "class Bar {"
             "}"
             "```"]]
   (process-lines statemachine-example {:state :init} file) =>
   {:code-blocks [["class Bar {"
                   "}"]
                  ["void foo() {"
                   "}"]]
    :languages ["java" "c++"]
    :state :init}))

;; ## Converting Code Locations

;; When an error is reported for a code-example in a spec, the code locations
;; within the errors message (explanation) refer to the example itself.

;; In order for these locations to be useful, we need to convert them into
;; "global" locations, i.e., locations in the `.md` file itself.

;; `convert-error-locations` takes an explanation, a path and a base-line number
;; and returns the same explanation with the code locations updated to point to
;; the correct position in the `.md` file.
(fact
 (let [explanation ["Some text" (with-meta `foo {:path "example"
                                                 :start 1000003
                                                 :end 1000005})]
       converted (convert-error-locations explanation "path/to/my.md" 3)]
   converted => ["Some text" `foo]
   (-> converted second meta) => {:path "path/to/my.md"
                                  :start 4000003
                                  :end 4000005}))

;; If the explanation contains bound variables, they should be replaced with
;; their corresponding values.
(fact
 (let [explanation ["Some text" (atom (with-meta `foo {:path "example"
                                                       :start 1000003
                                                       :end 1000005}))]
       converted (convert-error-locations explanation "path/to/my.md" 3)]
   converted => ["Some text" `foo]
   (-> converted second meta) => {:path "path/to/my.md"
                                  :start 4000003
                                  :end 4000005}))

;; ## Language Spec Analysis

;; After having built the necessary building blocks, we are ready to process
;; Markdown files that represent language sepcs.

;; The function `process-lang-spec` takes an initial state value and a sequence
;; of lines, and returns the state after processing these lines.

;; ### Global Behavior

;; There is some functionality that the analyzer has, that is agnostic of
;; context in the Markdown document.

;; First, it counds lines, so that update functions have access to the current
;; line number.
(fact
 (process-lang-spec {:state :init}
                    ["Line number 1"
                     "Line number 2"
                     "Line number 3"
                     "Line number 4"])
 => {:state :init :line 4})

;; The analyzer can be activated in generation mode. In this mode it will
;; generate an updated version of itself, i.e., with correct error messages. To
;; activate this mode, the initial state has to have `:generate true`. If this
;; is the case, a `:generated` vector will accumulate the entire file.
(fact
 (process-lang-spec {:state :init
                     :generate true}
                    ["Line number 1"
                     "Line number 2"
                     "Line number 3"
                     "Line number 4"])
 => {:state :init :line 4
     :generate true
     :generated ["Line number 1"
                 "Line number 2"
                 "Line number 3"
                 "Line number 4"]})

;; ### Header

;; A language spec Markdown file must specify the language it is specifying.
;; This language must have an entry in the
;; [language config](config.md#language-configuration).

;; To specify the language in the markdown, in a separate line, write:
;; ```md
;; Language: `my-language-name`
;; ```

;; `process-lang-spec`, in its initial state, adds a `:lang` key to the state.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "Language: `y18`"
                     "some other unrelated line..."])
 => {:state :init
     :lang "y18"
     :line 3})

;; ### Dependency Modules

;; In order to specify imports and relationships between modules, it is
;; necessary to enable the spec to provide modules that will later be imported
;; by example code.

;; To specify a module, end a line with the module name in backticks followed by
;; a colon, and begin the next line with a code block. This code-block will be
;; stored under the `:modules` key, keyed by the module name.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "This can be seen in the module `foo.bar`:"
                     "```python"
                      "def foo(x):"
                      "  return x+2"
                      "```"
                     "Another unrelated line"])
 => {:state :init
     :modules {"foo.bar" ["def foo(x):"
                          "  return x+2"]}
     :line 7})

;; If the line ending in a backticked name and a colon is not followed by a code
;; block, the line is ignored.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "Some unrelated text to `foo.bar`:"
                     "Another unrelated line"])
 => {:state :init
     :line 3})

;; ### Example Code

;; A language spec contains example code blocks. Any code block that is not a
;; [dependency module](#dependency-modules) is considered by the analyzer as an
;; example code block.

;; Example code blocks require that the initial state contains `:langmap`, a
;; [language-map](polyglot_loader.md#module-and-language-representation),
;; `:lang`, which references a key in the language-map and `:path`, a path to
;; the spec file needed to correctly assign code-locations.

;; An example code-block consists of two code blocks: The first, containing the
;; code example, and the second, under the (Markdown-unsupported) `status`
;; "language", containing the expected result.

;; Code blocks without a subsequent `status` block are ignored.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "```c++"
                     "void main() {}"
                     "}"
                     "```"
                     "Another unrelated line"])
 => {:state :init
     :line 6})

;; There are two types of example code blocks: _positive_ and _negative_.
;; Positive examples contain code that should be valid, while negative examples
;; contain code that should be invalid. The latter also contains the error
;; message that should be produced by the language definition.

;; The status block should only contain a single line, requiring either success
;; or an error, as explained in the following subsections. If a different line
;; is present, and exception is raised.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "```c++"
                     "void main() {}"
                     "}"
                     "```"
                     "```status"
                     "This is an invalid status line"
                     "```"
                     "Another unrelated line"])
 => (throws "A status block should contain either 'Success' or 'ERROR: ...', but 'This is an invalid status line' was found"))

;; Similarly, if an extra line is added after a (valid) first line, an exception
;; is thrown.
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "```c++"
                     "void main() {}"
                     "}"
                     "```"
                     "```status"
                     "Success"
                     "This is an extra line"
                     "```"
                     "Another unrelated line"])
 => (throws "A status block should only contain a single line, but 'This is an extra line' was found"))

;; #### Positive Code Examples

;; A positive code example is a code example which `status` block contains a
;; single word: `Success`. The expectation is that code in the code block will
;; be evaluated successfully. On a successful run, the :success counter in the
;; state is incremented.
(defn mock-parse [name path text])
(fact
 (let [langmap {"y4" {:resolve #(throw
                                 (Exception. (str "resolve should not have been called: " %)))
                      :read #(throw
                              (Exception. (str "read should not have been called: " %)))
                      :parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :lang "y4"
                       :langmap langmap
                       :path "path/to/spec.md"}
                      ["```c++"
                       "void main() {"
                       "}"
                       "```"
                       "```status"
                       "Success"
                       "```"])
   => {:state :init
       :line 7
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :success 1}
   (provided
    (mock-parse "example" "example" "void main() {\n}") => (ok [[] []]))))

;; In case of an error, the explanation, along with the line-number of the block
;; header are added to the `:errors` vector. The :success counter is not
;; incremented.
(fact
 (let [langmap {"y4" {;; :resolve and :read are not used and can therefore be
                      ;; omitted here
                      :parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap
                       :path "path/to/spec.md"}
                      ["Language: `y4`"
                       "```c++"
                       "void main() {"
                       "}"
                       "```"
                       "```status"
                       "Success"
                       "```"])
   => {:state :init
       :line 8
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :errors [["No rules are defined to translate statement"
                 `(this-is-not-supported)
                 "and therefore it does not have any meaning"]]}
   (provided
    (mock-parse "example" "example" "void main() {\n}") =>
    (ok [[`(this-is-not-supported)] []]))))

;; #### Negative Code Examples

;; A negative example block is an example which status contains the string
;; `ERROR: `, followed by the expected error text.

;; If the code block fails to evaluate, producing the expected text, the test
;; succeeds.
(fact
 (let [langmap {"y4" {:parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap
                       :path "path/to/spec.md"}
                      ["Language: `y4`"
                       "```c++"
                       "void main() {"
                       "}"
                       "```"
                       "```status"
                       "ERROR: No rules are defined to translate statement (this-is-not-supported) and therefore it does not have any meaning"
                       "```"])
   => {:state :init
       :line 8
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :success 1}
   (provided
    (mock-parse "example" "example" "void main() {\n}") =>
    (ok [[`(this-is-not-supported)] []]))))

;; If the evaluation succeeds, the test fails.
(fact
 (let [langmap {"y4" {:parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap
                       :path "path/to/spec.md"}
                      ["Language: `y4`"
                       "```c++"
                       "void main() {"
                       "}"
                       "```"
                       "```status"
                       "ERROR: No rules are defined to translate statement (this-is-not-supported) and therefore it does not have any meaning"
                       "```"])
   => {:state :init
       :line 8
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :errors [["The example should have produced an error, but did not"]]}
   (provided
    (mock-parse "example" "example" "void main() {\n}") =>
    (ok [[] []]))))

;; If the evaluation fails, but provides a different explanation (error
;; message), the test fails.
(fact
 (let [langmap {"y4" {:parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap
                       :path "path/to/spec.md"}
                      ["Language: `y4`"
                       "```c++"
                       "void main() {"
                       "}"
                       "```"
                       "```status"
                       "ERROR: No rules are defined to translate statement (this-is-not-supported) and therefore it does not have any meaning"
                       "```"])
   => {:state :init
       :line 8
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :errors [["The wrong error was reported:"
                 "No rules are defined to translate statement"
                 `(this-is-unexpected)
                 "and therefore it does not have any meaning"]]}
   (provided
    (mock-parse "example" "example" "void main() {\n}") =>
    (ok [[`(this-is-unexpected)] []])))

;; ### Code Location in Errors

;; The errors produced by code examples have code-locations that point to the
;; spec (`.md` file) with the correct line number.
 (fact
  (let [langmap {"y4" {:parse (fn [name path text]
                                (mock-parse name path text))}}]
    (def pos-example-err-status (process-lang-spec {:state :init
                                                    :langmap langmap
                                                    :path "path/to/spec.md"}
                                                   ["Language: `y4`"
                                                    "```c++"
                                                    "void main() {"
                                                    "}"
                                                    "```"
                                                    "```status"
                                                    "Success"
                                                    "```"]))) =>
  #'pos-example-err-status
  (provided
   (mock-parse "example" "example" "void main() {\n}") =>
   (ok [[(with-meta `(this-is-not-supported) {:path "foo"
                                              :start 1000005
                                              :end 1000007})] []]))
  (-> pos-example-err-status :errors) =>
  [["No rules are defined to translate statement"
    `(this-is-not-supported)
    "and therefore it does not have any meaning"]]
  (-> pos-example-err-status :errors first second meta) =>
  {:path "path/to/spec.md"
   :start 3000005
   :end 3000007}))

;; ### Updating the Spec

;; It is mostly recommended to write the spec first, and then update the
;; language $y_0$ definition to meed the spec. However, it sometimes happens
;; that this is not an easy thing to do.

;; One common example is when the text of an explanation (error message) is
;; changed. If this explanation repeats in many parts of the spec, it may be
;; hard to track it down in all its occurrences and update the spec. It should
;; be easier for the analyzer to simply update the `status` blocks in the spec
;; to match the up-to-date status.

;; This feature should be handled with care, since in case of a bug in the
;; definition, the spec could be updated to reflect the buggy behavior. Careful
;; review of the diff of the spec after an update is therefore recommended.

;; To trigger an update, set `:generate` to `true` in the analyzer state. This
;; will cause the `:generated` key to contain the entire file, with the `status`
;; blocks updated. Other behavior does not change, so that `:errors` and
;; `:success` are still accumulated.
(fact
 (let [langmap {"y4" {;; :resolve and :read are not used and can therefore be
                      ;; omitted here
                      :parse (fn [name path text]
                               (mock-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap
                       :path "path/to/spec.md"
                       :generate true}
                      ["Language: `y4`"
                       "```c++"
                       "void main() {"
                       "  something_wrong;"
                       "}"
                       "```"
                       "```status"
                       "Success"
                       "```"
                       ""
                       "```c++"
                       "void main() {"
                       "  something_correct;"
                       "}"
                       "```"
                       "```status"
                       "ERROR: Some error"
                       "```"])
   => {:state :init
       :line 18
       :lang "y4"
       :langmap langmap
       :path "path/to/spec.md"
       :errors [["The example should have produced an error, but did not"]
                ["No rules are defined to translate statement"
                 `(this-is-not-supported)
                 "and therefore it does not have any meaning"]]
       :generate true
       :generated ["Language: `y4`"
                   "```c++"
                   "void main() {"
                   "  something_wrong;"
                   "}"
                   "```"
                   "```status"
                   "ERROR: No rules are defined to translate statement (this-is-not-supported) and therefore it does not have any meaning"
                   "```"
                   ""
                   "```c++"
                   "void main() {"
                   "  something_correct;"
                   "}"
                   "```"
                   "```status"
                   "Success"
                   "```"]}
   (provided
    (mock-parse "example" "example" "void main() {\n  something_wrong;\n}") =>
    (ok [[`(this-is-not-supported)] []])
    (mock-parse "example" "example" "void main() {\n  something_correct;\n}") =>
    (ok [[] []]))))  