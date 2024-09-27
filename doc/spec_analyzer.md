* [Analyzing Language Specs](#analyzing-language-specs)
  * [Line-Based-Processing](#line-based-processing)
    * [Finding a Matching Transition](#finding-a-matching-transition)
    * [Stepwise Activation](#stepwise-activation)
    * [Processing a Complete File](#processing-a-complete-file)
  * [Language Spec Analysis](#language-spec-analysis)
    * [Global Behavior](#global-behavior)
    * [Header](#header)
    * [Dependency Modules](#dependency-modules)
    * [Example Code](#example-code)
      * [Positive Code Examples](#positive-code-examples)
```clojure
(ns y0.spec-analyzer-test
  (:require [midje.sweet :refer [fact => throws provided anything]]
            [y0.spec-analyzer :refer :all]
            [y0.status :refer [ok]]))

```
# Analyzing Language Specs

A language sepc is a Markdown file (`.md`), that contains some positive and
some negative examples of code in the language being specified. Negative
examples are followed by the text of the "why not" explanation (error
message) to be produced by the language's semantic definition.

## Line-Based Processing

To be able to process Markdown files, we need to develop an easy way to
process text files, line by line, maintaining state between lines.

To do so, we follow a state-machine model. The state-machine is defined as a
map from state (keyword) to a vector of transitions. Each transition is
represented as a map that is semantically a record, with the following
fields:

1. `:pattern`: A regex to match a line, triggering the rule.
2. `:transition`: A keyword representing the next state, given the trigger.
3. `:update-fn`: A function which updates the state value. This function
   takes the previous state value and the line's matches, as returned by the
   `:pattern`, and returns the new state value.

Let us consider the following state-machine example, which we will use to
demonstrate this mechanism. It is a state-machine for Markdown files, which
collects all the code-snippets in the markdown into a vector.
```clojure
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

```
In the following we build this functionality step by step.

### Finding a Matching Transition

Given a specific state and a line, how do we choose the transition to be
applied (if any)?

`find-transition` takes a state-spec (vector of transitions) and a line, and
returns the matching pattern, if any, and the match, as returned by the
regex.

If no pattern matches, it returns `[{} nil]`.
```clojure
(fact
 (find-transition [{:pattern #"^```"
                    :transition :foo}
                   {:pattern #"^#+"
                    :transition :bar}] "hello, world") =>
 [{} nil])

```
If a pattern matches the line, the respective transition is returned, with
the `:pattern` removed.
```clojure
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}] "### Hello, World") =>
 [{:transition :bar} ["### Hello, World" "Hello, World"]])

```
If a transition has no `:pattern`, it matches any line.
```clojure
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}
                   {:transition :baz}] "hello, world") =>
 [{:transition :baz} ["hello, world"]])

```
If more than one transition matches, the first is returned.
```clojure
(fact
 (find-transition [{:pattern #"```(.*)"
                    :transition :foo}
                   {:pattern #"#+ *(.*)"
                    :transition :bar}
                   {:transition :baz}] "```c++") =>
 [{:transition :foo} ["```c++" "c++"]])

```
### Stepwise Activation

The function `apply-line` takes a state-machine, a state and a line and
returns the state, after applying the line. The state is a map which contains
a `:state` key, which corresponding value is the state keyword.

If None of the transitions hold for the line, the state is unchanged.
```clojure
(fact
 (apply-line statemachine-example {:state :init
                                   :foo :bar}
             "Some uninteresting line") => {:foo :bar
                                            :state :init})

```
If one pattern matches, `:transition` and `:update-fn` are applied.
```clojure
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

```
If the state-machine contains a state `:any`, its transitions are considered
regardless of the current state. This is useful for things like counting
line numbers, which should be done at any state.
```clojure
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

```
### Processing a Complete File

Given a state-machine definition, an intial state and the contents of an
input file given as a sequence of lines, `process-lines` will return the
state after processing the entire file.
```clojure
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

```
## Language Spec Analysis

After having built the necessary building blocks, we are ready to process
Markdown files that represent language sepcs.

The function `process-lang-spec` takes an initial state value and a sequence
of lines, and returns the state after processing these lines.

### Global Behavior

There is some functionality that the analyzer has, that is agnostic of
context in the Markdown document.

First, it counds lines, so that update functions have access to the current
line number.
```clojure
(fact
 (process-lang-spec {:state :init}
                    ["Line number 1"
                     "Line number 2"
                     "Line number 3"
                     "Line number 4"])
 => {:state :init :line 4})

```
The analyzer can be activated in generation mode. In this mode it will
generate an updated version of itself, i.e., with correct error messages. To
activate this mode, the initial state has to have `:generate true`. If this
is the case, a `:generated` vector will accumulate the entire file.
```clojure
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

```
### Header

A language spec Markdown file must specify the language it is specifying.
This language must have an entry in the
[language config](config.md#language-configuration).

To specify the language in the markdown, in a separate line, write:
```md
Language: `my-language-name`
```

`process-lang-spec`, in its initial state, adds a `:lang` key to the state.
```clojure
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "Language: `y18`"
                     "some other unrelated line..."])
 => {:state :init
     :lang "y18"
     :line 3})

```
### Dependency Modules

In order to specify imports and relationships between modules, it is
necessary to enable the spec to provide modules that will later be imported
by example code.

To specify a module, end a line with the module name in backticks followed by
a colon, and begin the next line with a code block. This code-block will be
stored under the `:modules` key, keyed by the module name.
```clojure
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

```
If the line ending in a backticked name and a colon is not followed by a code
block, the line is ignored.
```clojure
(fact
 (process-lang-spec {:state :init}
                    ["Some unrelated line"
                     "Some unrelated text to `foo.bar`:"
                     "Another unrelated line"])
 => {:state :init
     :line 3})

```
### Example Code

A language spec contains example code blocks. Any code block that is not a
[dependency module](#dependency-modules) is considered by the analyzer as an
example code block.

Example code blocks require that the initial state contains `:langmap`, a
[language-map](polyglot_loader.md#module-and-language-representation), and
`:lang`, which references a key in the language-map.

There are two types of example code blocks: _positive_ and _negative_.
Positive examples contain code that should be valid, while negative examples
contain code that should be invalid. The latter also contains the error
message that should be produced by the language definition.

#### Positive Code Examples

A positive code example consists of a code block followed immediately by
another code block with the `status` "language", containing a single line
with a single word: `Success`. The expectation is that code in the code block
will be loaded successfully.
```clojure
(defn my-parse [name path text])
(fact
 (let [langmap {"y4" {:resolve #(throw
                                 (Exception. (str "resolve should not have been called: " %)))
                      :read #(throw
                              (Exception. (str "read should not have been called: " %)))
                      :parse (fn [name path text]
                               (my-parse name path text))}}]
   (process-lang-spec {:state :init
                       :lang "y4"
                       :langmap langmap}
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
       :code-examples 1}
   (provided
    (my-parse "example" "example" "void main() {\n}") => (ok [[] []]))))

```
In case of an error, the explanation, along with the line-number of the block
header are added to the `:errors` vector.
```clojure
(fact
 (let [langmap {"y4" {:resolve #(throw
                                 (Exception. (str "resolve should not have been called: " %)))
                      :read #(throw
                              (Exception. (str "read should not have been called: " %)))
                      :parse (fn [name path text]
                               (my-parse name path text))}}]
   (process-lang-spec {:state :init
                       :langmap langmap}
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
       :code-examples 1
       :errors [{:explanation ["No rules are defined to translate statement"
                               `(this-is-not-supported)
                               "and therefore it does not have any meaning"]
                 :line 2}]}
   (provided
    (my-parse "example" "example" "void main() {\n}") =>
    (ok [[`(this-is-not-supported)] []]))))
```

