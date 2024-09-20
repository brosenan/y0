{"y1" {;; Use an EDN parser
       :parser :edn
       ;; Initialize the root namespace from a list of symbols 
       :root-refer-map :root-symbols
       ;; All these symbols...
       :root-symbols [defn deftype declfn defclass definstance]
       ;; ...populate this namespace 
       :root-namespace "y1.core"
       ;; The resolver is based on a prefix list
       :resolver :prefix-list
       ;; The path relative to the prefixes is based on dots in
       ;; the module name (e.g., a.b.c => a/b/c.ext)
       :relative-path-resolution :dots
       ;; The file extension for the source files
       :file-ext "y1"
       ;; The modules that define the language's semantics
       :extra-modules [{:lang "y0" :name "y1"}]
       ;; The prefix list comes from an environment variable...
       :path-prefixes :from-env
       ;; ...named Y0-PATH
       :path-prefixes-env "Y1-PATH"}
 "c0" {;; Use an Instaparser
       :parser :insta
       ;; The grammar with a layout section
       :grammar "compilation_unit = import* definition*
                 
                 import = <'import'> dep <';'>
                 dep = #'[a-z_0-9.]+'
                 
                 <definition> = func_decl | func_def
                 func_decl = type identifier <'('> arg_defs <')'> <';'>
                 func_def = type identifier <'('> arg_defs <')'> <'{'> statement* <'}'>
                 
                 arg_defs = ((arg_def <','>)* arg_def)?
                 arg_def = type identifier

                 <type> = pointer_type | 'int' | 'float' | 'string'
                 pointer_type = type <'ptr'>

                 <statement> = vardef | assign

                 vardef = type identifier <'='> expr <';'>
                 assign = expr <'='> expr <';'>

                 expr = sum_expr
                 <sum_expr> = mult_expr | add | sub
                 <mult_expr> = unary_expr | mult | div | mod
                 <unary_expr> = atomic_expr | addressof | deref | minus
                 <atomic_expr> = literal | identifier | <'('> expr <')'>
                 <literal> = (int / float) | string

                 addressof = <'&'> unary_expr
                 deref = <'*'> unary_expr
                 add = sum_expr <'+'> mult_expr
                 sub = sum_expr <'-'> mult_expr
                 mult = mult_expr <'*'> unary_expr
                 div = mult_expr <'/'> unary_expr
                 mod = mult_expr <'%'> unary_expr
                 minus = <'-'> unary_expr
                 
                 keyword = 'int' | 'string' | 'float' | 'ptr'
                 identifier = !keyword #'[a-zA-Z_][a-zA-Z_0-9]*'
                 int = #'-?[1-9][0-9]*'
                 float = #'-?[1-9][0-9]*([.][0-9]+)?([eE][+\\-][0-9]+)?'
                 string = #'\"([^\"\\\\]|\\\\.)*\"'
                 --layout--
                 layout = #'\\s'+"
       ;; The keyword (or keywords) representing an identifier in
       ;; the grammar
       :identifier-kws :identifier
       ;; The keyword representing a dependency module name in the
       ;; grammar
       :dependency-kw :dep
       ;; The resolver is based on a prefix list
       :resolver :prefix-list
       ;; The path relative to the prefixes is based on dots in
       ;; the module name (e.g., a.b.c => a/b/c.ext)
       :relative-path-resolution :dots
       ;; The file extension for the source files
       :file-ext "c0"
       ;; The modules that define the language's semantics
       :extra-modules [{:lang "y0" :name "c0"}]
       ;; The prefix list comes from an environment variable...
       :path-prefixes :from-env
       ;; ...named C0-PATH
       :path-prefixes-env "C0-PATH"}}
