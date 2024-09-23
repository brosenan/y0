* [Analyzing Language Specs](#analyzing-language-specs)
  * [Line-Based-Processing](#line-based-processing)
    * [Finding a Matching Transition](#finding-a-matching-transition)
    * [Stepwise Activation](#stepwise-activation)
    * [Processing a Complete File](#processing-a-complete-file)
```clojure
(ns y0.spec-analyzer-test
  (:require [midje.sweet :refer [fact => throws provided anything]]
            [y0.spec-analyzer :refer :all]))

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
   takes the previous state value and the line, and returns the new state
   value.

Let us consider the following state-machine example, which we will use to
demonstrate this mechanism. It is a state-machine for Markdown files, which
collects all the code-snippets in the markdown into a vector.
```clojure
(def statemachine-example
  {:init [;; Start a code-block
          {:pattern #"```.*"
           :transition :code
           :update-fn (fn [v _line]
                        (assoc v :current []))}]
   :code [;; At the end of the code-block, append the current code block to
          ;; :code-blocks
          {:pattern #"```.*"
           :transition :init
           :update-fn (fn [v _line]
                        (-> v
                            (update :code-blocks #(conj % (:current v)))
                            (dissoc :current)))}
          ;; In any line within the code block, append the contents to :current
          {:update-fn (fn [v line]
                        (update v :current #(conj % line)))}]})

```
In the following we build this functionality step by step.

### Finding a Matching Transition

Given a specific state and a line, how do we choose the transition to be
applied (if any)?

`find-transition` takes a state-spec (vector of transitions) and a line, and
returns the matching pattern, if any.

If no pattern matches, it returns `{}`.
```clojure
(fact
 (find-transition [{:pattern #"^```"
                    :transition :foo}
                   {:pattern #"^#+"
                    :transition :bar}] "hello, world") =>
 {})

```
If a pattern matches the line, the respective transition is returned, with
the `:pattern` removed.
```clojure
(fact
 (find-transition [{:pattern #"```.*"
                    :transition :foo}
                   {:pattern #"#+.*"
                    :transition :bar}] "### Hello, World") =>
 {:transition :bar})

```
If a transition has no `:pattern`, it matches any line.
```clojure
(fact
 (find-transition [{:pattern #"```.*"
                    :transition :foo}
                   {:pattern #"#+.*"
                    :transition :bar}
                   {:transition :baz}] "hello, world") =>
 {:transition :baz})

```
If more than one transition matches, the first is returned.
```clojure
(fact
 (find-transition [{:pattern #"```.*"
                    :transition :foo}
                   {:pattern #"#+.*"
                    :transition :bar}
                   {:transition :baz}] "```c++") =>
 {:transition :foo})

```
### Stepwise Activation

The function `apply-line` takes a state-machine, a state keyword, a state
value and a line and returns a pair `[state val]` containing the state
keyword and the value, after applying the line.

If None of the transitions hold for the line, the state is unchanged.
```clojure
(fact
 (apply-line statemachine-example :init {:foo :bar}
             "Some uninteresting line") => [:init {:foo :bar}])

```
If one pattern matches, `:transition` and `:update-fn` are applied.
```clojure
(fact
 (apply-line statemachine-example :init {:foo :bar}
             "```c++") => [:code {:foo :bar
                                  :current []}]
 (apply-line statemachine-example :code {:current []}
             "println('hello, world')") =>
 [:code {:current ["println('hello, world')"]}])

```
### Processing a Complete File

Given a state-machine definition, an intial state (keyword and value) and the
contents of an input file given as a sequence of lines, `process-lines`
will return the state (value) after processing the entire file.
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
   (process-lines statemachine-example :init {} file) =>
   {:code-blocks [["class Bar {"
                   "}"]
                  ["void foo() {"
                   "}"]]}))
```

