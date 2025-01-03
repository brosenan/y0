# `y0lsp`: A Universal Language Server based on $y_0$

The goal of this project is to create a universal language server, implementing
the [Language Server
Protocol](https://microsoft.github.io/language-server-protocol/) that uses $y_0$
([link](../)) as a way to define languages.

The idea is:

* You provide a declarative definition of the language + some configuration.
* We provide a language sever for it.

`y0lsp` is designed around a plug-in approach, where different plug-ins
implement different LSP services.

This project is work in progress, so stay tuned for updates.

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
