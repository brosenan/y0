# $y_0$, AKA Y Nought, AKA Why Not

$y_0$, AKA Y Nought, AKA Why Not, is a declarative language intended for semantic analysis and other post-parsing, pre-optimizations compilation tasks.

$y_0$ is a logic-programming language. It means that a $y_0$ program consists of rules that use logic to determine properties of a program in the target language -- the language which we wish to compile / analyze.

In comparison with Prolog, the most well-known logic programming language, $y_0$ has a few unique features:

1. Predicates in $y_0$ are either deterministic or semi-deterministic. Deterministic predicates either return a result for every query or return an explanation _why not_, i.e., why the input program is not valid. Semi-deterministic predicates, which names end with a `?` have a third outcome -- not returning a result. These can be used by deterministic predicates as conditions, or by providing the lack of result as an error.
2. Variables are quantified. In Prolog, logic variables are distinguished from other symbols in the language lexically, by starting with a capital letter or an underscore. In $y_0$, however, logic variables are declared within `all` and `exist` forms, which correspond to the &#2200 and &#2203 operators in first-order logic, respectively. Using explicit quantification in $y_0$ allows for variables to be indistinguishable from other symbols in the program, which in turn opens the door to _meta variables_.
3. The `given` form allows for assumptions to be injected into a logic goal. This allows for an easy and intuitive definition of scopes.
4. $y_0$ supports both Prolog-style _top down_ and Datalog-style _bottom up evaluation_. The `<-` operator means that for the goal on the left to be true, all goals on the right must be true, while the `->` operator means that if the goal on the left is said to be true, so are the goals on the right. The operator `<->` says both and acts as "if and only if".
5. Meta variables are variables that come from the program being analyzed but act as logic variables in the $y_0$ program. These can be used, e.g., as type variables in algebraic data types or generics.
6. $y_0$ is a purely-declarative programming language. Its built-in predicates have no side effects.


## Usage

FIXME

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
