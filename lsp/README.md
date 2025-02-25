# `y0lsp`: A Universal Language Server based on $y_0$

`y0lsp` is a **universal language server** based on the [$y_0$](../README.md)
language. Its architecture is based on a separation of concerns between the
[language server protocol
(LSP)](https://en.wikipedia.org/wiki/Language_Server_Protocol) on the one hand,
and the semantics of a given language, on the other hand.

`y0lsp` provides this separation of concerns by providing a declarative language
([$y_0$](../README.md)) for defining the semantics of languages and
instrumenting the implementation of this language to provide semantic
information about the code. Then, using [addons](doc/addon_utils.md), `y0lsp`
implements the different LSP services, based on the semantic information that is
extracted from the evaluation of $y_0$ over the code, thus working for all
languages with such a definition.

Supplementary to the semantic definition, syntax definition and module system
behavior are configured in a [language config](../doc/config.md).

## Working with `y0lsp`

The goal of `y0lsp` is to ease the definition of high-quality language support
for editors and IDEs which can act as LSP clients. This means that one must
refer to the documentation of any target editor / IDE in order to know how to
create a language extension.

However, here we give the overall outline of such an integration.

### Running the Server

`y0lsp.jar` can be [downloaded from here](bin/y0lsp.jar), as a _self-contained
executable JAR file_. This means you need a Java Runtime Environment installed
in order to use it.

Assuming `y0lsp.jar` is placed in some `EXTENSION_HOME`, the command line to
execute the server is:

```sh
$ $JAVA_HOME/bin/java -jar $EXTENSION_HOME/y0lsp.jar $EXTENSION_HOME
```

### The Extension Directory

The last component of the command (`$EXTENSION_HOME`) is an argument to the
JAR's `main` function, which is a path to a directory which, at the very least,
contain a `lang-conf.edn` file, containing the [$y_0$ language
configuration](../doc/config.md) for all the languages this extension is
intended to define (so basically, your language(s)), and `.y0` files containing
their semantic definitions. [demo/](demo/) is such a directory, defining two
sample languages: `y1` and `c0`.

**Note**: `$EXTENSION_HOME` can, but doesn't have to be the root of your extension. Also,
there is no need for `y0lsp.jar` to be in the same directory as
`lang-conf.edn` and the environment pointing to them certainly doesn't have to
be named "EXTENSION_HOME". This is only given as an example setup.

## Resources

* The server can be downloaded from [here](bin/y0lsp.jar).
* The language config documentation is [here](../doc/config.md), with an example
  [here](demo/lang-conf.edn).
* Resources regarding the $y_0$ language can be found
  [here](../README.md#documentation).

## License

Copyright © 2025 Boaz Rosenan

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
