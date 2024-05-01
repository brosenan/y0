# y0, AKA Y Nought, AKA Why Not

y0, AKA Y Nought, AKA Why Not, is a declarative language intended for semantic analysis and other post-parsing, pre-optimizations compilation tasks.

y0 is a logic-programming language. It means that a y0 program consists of rules that use logic to determine properties of a program in the target language -- the language which we wish to compile / analyze.

In comparison with Prolog, the most well-known logic programming language, y0 has a few unique features:

1. Predicates in y0 are either deterministic or semi-deterministic. Deterministic predicates either return a result for every query or return an explanation _why not_, i.e., why the input program is not valid. Semi-deterministic predicates, which names end with a `?` have a third outcome -- not returning a result. These can be used by deterministic predicates as conditions, or by providing the lack of result as an error.
2. Variables are quantified. In Prolog, logic variables are distinguished from other symbols in the language lexically, by starting with a capital letter or an underscore. In y0, however, logic variables are declared within `all` and `exist` forms, which correspond to the &#2200 and &#2203 operators in first-order logic, respectively. Using explicit quantification in y0 allows for variables to be indistinguishable from other symbols in the program, which in turn opens the door to _meta variables_.
3. The `given` form allows for assumptions to be injected into a logic goal. This allows for an easy and intuitive definition of scopes.
4. Meta variables are variables that come from the program being analyzed but act as logic variables in the y0 program. These can be used, e.g., as type variables in algebraic data types or generics.
5. y0 is a purely-declarative programming language. Its built-in predicates have no side effects.


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
