![logo](y0.png)

**Note:** This README refers to the $y_0$ language implementation. For the
`y0lsp` universal language server implementation refer to [y0lsp](lsp/).

# $y_0$: A Language for Defining Programming Language Semantics

$y_0$, AKA Y Nought, AKA Why Not, is a declarative language intended for
defining the semantics of programming languages. Similar to how [parser
generators](https://en.wikipedia.org/wiki/Comparison_of_parser_generators)
define languages for defining the _syntax_ of computer and programming
languages, $y_0$ is designed to define their _semantics_.

The term "semantics" is ambiguous, and can mean different things. For example,
it can mean _what syntactically-valid inputs are valid programs_ but it can also
mean _what does a given program do_.

$y_0$ is capable of doing both, but it is intended for the former. Given a parse
tree, determine whether it represents a semantically valid program, and if not,
why. The latter part gives $y_0$ its name.

## Motivation

[Traditionally](https://en.wikipedia.org/wiki/Compilers:_Principles,_Techniques,_and_Tools),
the task of performing semantic analysis on syntax trees has been implemented in
compilers using a general-purpose programming language. After having tackled the
challenge of parsing, reasoning over a tree is something that most programming
launguages handle fairly well.

However, ever since InteliJ first shared their IDEA (pun intended) that IDEs
(Integrated Development Environment) can benfit heavily from
semantically-understanding the program, the role of semantic analyzers has
changed significantly. Language implementation is no longer limited to
compilers. Editors need to understand the programs too, but need to do
significantly different things with this understanding.

A later advancement in this field was the introduction of the [Language Server
Protocol](https://microsoft.github.io/language-server-protocol/) by VS Code.
With this protocol, a the semantics of a language can be exposed by a _server_,
of which editors and IDEs are _clients_, thus turning what was previously a
`n*m` problem (`n` languages times `m` editors) to a `n+m` problem.

However, implementing a language server is never easy. An implementation needs
to be based on a semantic understanding of the language, but the different
services such a server can implement need to do different things with it. In a
way, this is still an `n*k` problem, with `n` languages times `k` services
(e.g., semantic highlighting, auto-completion, error reporting etc).

$y_0$ is intended to turn this into a `n+k` problem instead. For each language,
a semantic definition is provided. The different language services, however, are
implemented as part of $y_0$. This allows us to tackle each of the `k` language
services only once, while $y_0$ users only need to define their language
semantics in $y_0$ (`n` definitions overall).

We are not there yet, but this is where we strive to go...

## The $y_0$ Language

$y_0$ is a logic programming language. However, it is significantly different
from other logic-programming languages such as Prolog and Datalog, so readers
unfamiliar with these languages and logic programming in general, are not
necessarily at a disadvantage trying to learn $y_0$.

The way of defining the semantics of a programming language in $y_0$ is by
defining a [_predicate_](doc/hello.md#predicates) that accepts its parse tree.
For example, the predicate `bit` defined below accepts a language consisting of
eitehr `0` or `1`:

```clojure
;; Base case: reject everything.
(all [x]
     (bit x ! "Expected a bit, received" x))
;; Accept 0 and 1
(fact (bit 0))
(fact (bit 1))
```

The first thing to notice in this example is the fact that $y_0$'s syntax is
based on [s-expressions](https://en.wikipedia.org/wiki/S-expression). We made
this decision because s-expressions are inherently trees, so parse-trees and
parse-tree fragments are easy to express in such a language. For people who are
not familiar with LISP-like languages and find the use of s-expressions
intimidating, I would recommend finding an extension along the lines of [Rainbow
Brackets](https://marketplace.visualstudio.com/items?itemName=2gua.rainbow-brackets)
for your favorite editor. It accomplishes two goals at once: allowing you to
better understand nesting level of your expressions and at the same time making
the code more colorful and therefore more cheerful.

Having gone pased that, the code-snippet above contains a _rule_ (starting with
the `all` keyword, followed by a vector (`[]`) of free variables) and two
_facts_ (starting with the keyword `fact`). The rule is a "catch all" rule that
matches everything (because `x` could be anything) and rejects (using the `!`
symbol) them, providing an explanation (`"Expected a bit, received" x`).

It is followed by two facts that state that both `0` and `1` are valid bits.

This works as expected because $y_0$ chooses the _most specific rule_ when
matching an input tree. Therefore, `0` or `1` will be matched by their dedicated
facts, while anything else will be matched by the catch-all rule.

### Deduction Rules

A slightly more complex example accepts s-expressions that represent [Peano
numbers](https://en.wikipedia.org/wiki/Peano_axioms):

```clojure
(all [x] (peano x ! x "is not a Peano number"))
(fact (peano z))
(all [n]
     (peano (s n)) <- (peano n))
```

As before, the first statement is a rule that rejects all non-Peano values. The
second statement is a fact that accepts `z` (zero), and the thrid is a
_deduction rule_, which accepts anything of the form `(s n)`, for any `n` that
is a Peano number by itself.

The last bit is done using the `<-` operator (the deduction operator), which
means that the term written to its left (`(peano (s n))`) holds if the terms
written to its right (`(peano n)`) hold.

### More Language Features

As our third example we define `lambda-expr`, a predicate that accepts
expressions in the [Lambda
Calculs](https://en.wikipedia.org/wiki/Lambda_calculus).

As usual, we begin with a catch-all rule.

```clojure
(all [x]
     (lambda-expr x ! x "is not a lambda-calulus expression"))
```

Then, we define a [translation rule](doc/statements.md) that defines `def`
_statements_.

```clojure
(all [v x]
     (def v x) =>
       (assert (lambda-expr x))
       (fact (lambda-expr v)))
```

Statements are top-level elements of the parse-tree. Translation rules translate
them into other statements, including rules (`all`), facts or assertions
(`assert`). In this case, a `def` statement is translated to an `assert` block
which verifies that `x` is a valid Lambda expression, followed by a fact that
defines states that `v` is now a valid lambda expression.

Next we define a rule for _lambda application_, an expression of the form `(f
x)`, where both `f` and `x` are Lambda expressions.

```clojure
(all [f x]
     (lambda-expr (f x)) <-
     (lambda-expr f)
     (lambda-expr x))
```

This is a deduction rule with two terms on its right-hand side.

Finally, we get to the most difficult piece to define in the Lambda Calculus,
the Lambda abstraction, of the form `(lambda v x)`, where `v` is a symbol and
`x` is an expression. The challenge here is that `x` _may include `v`_, so `v`
needs to be defined as a valid Lambda expression _within_ `x`, but nowhere else.

We do this as follows:

```clojure
(all [v x]
     (lambda-expr (lambda v x)) <-
     (given (fact (lambda-expr v))
            (lambda-expr x)))
```

The `given` keyword does the trick. It introduces the fact `(fact (lambda-expr
v))` locally, in the scope of checking that `x` is a Lambda expression, and only
there.

The entire example, with tests, can be found
[here](doc/conditions.md#example-the-lambda-calculus).

For a full-featured language, with algebraic data types and type classes, see
$y_1$, [our example language](doc/y1.md).

## Ecosystem

Beyond the language itself, this project implements the core of $y_0$'s
ecosystem, including:

*   A [polyglot module system](doc/polyglot_loader.md).
*   Parser support for [EDN-based languages](doc/edn_parser.md) as well as
    [context-free grammers](doc/instaparser.md).
*   [Language-spec](doc/spec_analyzer.md) support for allowing literate
    unit-tests for language definitions.

See [y0lsp](lsp/) for a universal language server implementation based on $y_0$.

## Documentation

### $y_0$ Language Documentation

*   [Language Introduction](doc/hello.md)
*   [Rules and Conditions](doc/conditions.md)
*   [Statements and Translation Rules](doc/statements.md)
*   [Why-Not Explanations](doc/why-not.md)
*   [Built-in Predicates](doc/builtins.md)
*   [A Library of Utility Predicates](doc/util.md)

### Example Languages

*   $y_1$: [a statically typed functional language with type classes](doc/y1.md)
*   $C_0$: a language inspired by C/go.
    *   [Language spec](doc/c0-spec.md)
    *   [Language semantics](doc/c0.md)
    *   [Language config, defining c0's syntax](lang-conf.edn)

### Developer Documentation

<!-- generated with:
for f in $(ls -1); do b=${f%_test.clj}; echo "* [$b](doc/$b.md)"; done
ran from test/y0
-->
* [config](doc/config.md)
* [core](doc/core.md)
* [edn_parser](doc/edn_parser.md)
* [explanation](doc/explanation.md)
* [instaparser](doc/instaparser.md)
* [location_util](doc/location_util.md)
* [polyglot_loader](doc/polyglot_loader.md)
* [predstore](doc/predstore.md)
* [resolvers](doc/resolvers.md)
* [rules](doc/rules.md)
* [spec_analyzer](doc/spec_analyzer.md)
* [status](doc/status.md)
* [term_utils](doc/term_utils.md)
* [testing](doc/testing.md)
* [to_html](doc/to_html.md)
* [unify](doc/unify.md)

## License

Copyright © 2024 Boaz Rosenan

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
