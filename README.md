# $y_0$: A Language for Defining Programming Language Semantics

$y_0$, AKA Y Nought, AKA Why Not, is a declarative language intended for defining the semantics of programming languages. Similar to how [parser generators](https://en.wikipedia.org/wiki/Comparison_of_parser_generators) define languages for defining the _syntax_ of computer and programming languages, $y_0$ is designed to define their _semantics_.

The term "semantics" is ambiguous, and can mean different things. For example, it can mean _what syntactically-valid inputs are valid programs_ but it can also mean _what does a given program do_.

$y_0$ is capable of doing both, but it is intended for the former. Given a parse tree, determine whether it represents a semantically valid program, and if now, why. The latter part gives $y_0$ its name.

## Motivation

[Traditionally](https://en.wikipedia.org/wiki/Compilers:_Principles,_Techniques,_and_Tools), the task of performing semantic analysis on syntax trees has been implemented in compilers using general-purpose code. After tackling the challenge of parsing, reasoning over a tree is something that any language that can handle structured data and recursion can handle.

However, ever since InteliJ first shared their IDEA (pun intended) that IDEs (Integrated Development Environment) can benfit heavily by semantically-understanding the program, the role of semantic analyzers has changed significantly. Language implementation is no longer limited to compilers. Editors need to understand the programs too, but need to do significantly different things with this understanding.

A later breakthrough in this field was the introduction of the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) by VS Code. It created a standard separation between an editor / IDE and a language implementation, turning what was previously a `n*m` problem (`n` languages times `m` editors) to a `n+m` problem, where every editor adopting this protocol needs to implement its client side, while a language server needs to be implemented for each language.

However, implementing a language server is never easy. An implementation needs to be based on a semantic understanding of the language, but the different services such a server can implement need to do different things with it. In a way, this is still an `n*k` problem, with `n` languages times `k` services (e.g., syntax highlighting, auto-completion, error reporting etc).

$y_0$ is intended to turn this into a `n+k` problem instead. For each language, a semantic definition is provided. The different language services, however, are implemented as part of $y_0$. This allows us to tackle each of the `k` language services only once, while $y_0$ users only need to define their language semantics in $y_0$ (`n` definitions overall).

We are not there yet, but this is where we strive to go...

## The $y_0$ Language

$y_0$ is a logic programming language. However, it is significantly different from other logic-programming languages such as Prolog and Datalog, so readers unfamiliar with these languages and logic programming in general, are not necessarily at a disadvantage trying to learn $y_0$.

The $y_0$ of defining the semantics of a programming language is by defining a [_predicate_](doc/hello.md#predicates) that accepts it. For example, the predicate `bit` defined below accepts a language consisting of eitehr `0` or `1`:

```clojure
;; Base case: reject everything.
(all [x]
     (bit x ! "Expected a bit, received" x))
;; Accept 0 and 1
(all []
     (bit 0))
(all []
     (bit 1))
```

The code-snippet above contains three rules (starting with the `all` keyword, followed by a vector or free variables). The first is a "catch all" rule that rejects (using the `!` symbol) everything. It is followed by two rules that each accepts one value (either `0` or `1`).

As can be seen in this example, $y_0$'s syntax is based on [s-expressions](https://en.wikipedia.org/wiki/S-expression). We did this because s-expressions are inherently trees, so parse-trees and parse-tree fragments are easy to express in such a language. For people who are not familiar with LISP-like languages and find the use of s-expressions intimidating, I would suggest to find an extension along the lines of [Rainbow Brackets](https://marketplace.visualstudio.com/items?itemName=2gua.rainbow-brackets) for your favorite editor. It accomplishes two goals at once: allows you to better understand nesting levels and at the same time makes the code look colorful.

## Documentation

*   [Language Introduction](doc/hello.md)
*   [Rules and Conditions](doc/conditions.md)
*   [Statements and Translation Rules](doc/statements.md)
*   [Why-Not Explanations](doc/why-not.md)
*   [Built-in Predicates](doc/builtins.md)
*   [An example language definition](doc/y1.md)

## Development Status

At this point the language itself is implemented to the point where one can define the semantics of s-expression-based languages with it (e.g., [our example language](doc/y1.md)).

Some features are still missing. For example:

*   A bootstrapping of $y_0$ to validate itself.
*   Integration with one or more parser generators.

LSP support will be provided by a separate project.

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
