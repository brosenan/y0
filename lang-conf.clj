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
       :path-prefixes-env "Y1-PATH"
       ;; Read files using Clojure's slurp function.
       :reader :slurp}
 "c0" {;; Use an Instaparser
       :parser :insta
       ;; The grammar with a layout section
       :grammar "compilation_unit = import* definition*
                 
                 import = <'import'> dep <';'>
                 dep = #'[a-z_0-9.]+'
                 
                 <definition> = func_decl | func_def | type_alias_def
                 func_decl = type identifier <'('> arg_defs <')'> <';'>
                 func_def = type identifier <'('> arg_defs <')'> <'{'> statement* <'}'>
                 
                 arg_defs = ((arg_def <','>)* arg_def)?
                 arg_def = type identifier

                 <type> = pointer_type
                          | int8_type | int16_type | int32_type | int64_type
                          | uint8_type | uint16_type | uint32_type | uint64_type
                          | float32_type | float64_type
                          | void_type | type_alias | struct_type | array_type
                 pointer_type = <'*'> type
                 int8_type    = <'int8'>
                 int16_type   = <'int16'>
                 int32_type   = <'int32'>
                 int64_type   = <'int64'>
                 uint8_type   = <'uint8'>
                 uint16_type  = <'uint16'>
                 uint32_type  = <'uint32'>
                 uint64_type  = <'uint64'>
                 float32_type = <'float32'>
                 float64_type = <'float64'>
                 void_type ='void'
                 type_alias = identifier
                 struct_type = <'struct'> <'{'> struct_member_def* <'}'>
                 array_type = <'['> int <']'> type
                 

                 <statement> = vardef | implicit_vardef | assign
                             | type_alias_def

                 vardef = type identifier <'='> expr <';'>
                 implicit_vardef = <'var'> identifier <'='> expr <';'>
                 assign = expr <'='> expr <';'>
                 type_alias_def = <'type'> identifier <'='> type <';'>
                 
                 <struct_member_def> = field_def | union_def
                 field_def = type identifier <';'>
                 union_def = <'union'> identifier <'{'> option_def* <'}'>
                 option_def = type identifier <';'>

                 expr = sum_expr
                 <sum_expr> = mult_expr | add | sub
                 <mult_expr> = unary_expr | mult | div | mod
                 <unary_expr> = atomic_expr | addressof | deref | minus
                 <atomic_expr> = literal | identifier | null | initializer_list
                               | typed_initializer_list | qualified_expr
                               | case_expr | <'('> expr <')'>
                 <literal> = (int / float) | string

                 addressof = <'&'> unary_expr
                 deref = <'*'> unary_expr
                 add = sum_expr <'+'> mult_expr
                 sub = sum_expr <'-'> mult_expr
                 mult = mult_expr <'*'> unary_expr
                 div = mult_expr <'/'> unary_expr
                 mod = mult_expr <'%'> unary_expr
                 minus = <'-'> unary_expr
                 null = <'null'>
                 initializer_list = <'{'> (initializer (<','> initializer)* )? <'}'>
                 <initializer> = expr | option_initializer
                 option_initializer = identifier <'='> expr
                 typed_initializer_list = type <'{'> (expr (<','> expr)* )? <'}'>
                 qualified_expr = atomic_expr <'.'> member_expr
                 case_expr = <'case'> <'('> identifier <'='> expr <')'>
                             <'{'> case_option_or_default (<','> case_option_or_default)* <'}'>
                 <case_option_or_default> = case_option | default_case
                 case_option = identifier <':'> expr
                 default_case = <'default'> <':'> expr

                 <member_expr> = identifier

                 keyword = 'int8' | 'int16' | 'int32' | 'int64'
                           | 'uint8' | 'uint16' | 'uint32' | 'uint64'
                           | 'float32' | 'float64' | 'void' | 'null' | 'var'
                           | 'type' | 'struct' | 'union' | 'case' | 'default'
                 identifier = !(keyword #'[^a-zA-Z_0-9]') #'[a-zA-Z_][a-zA-Z_0-9]*'
                 int = #'0|(-?[1-9][0-9]*)'
                 float = #'-?(0|(-?[1-9][0-9]*))([.][0-9]+)?([eE][+\\-][0-9]+)?'
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
       :path-prefixes-env "C0-PATH"
       ;; Read files using Clojure's slurp function.
       :reader :slurp
       ;; Stringify by extracting text from the source file
       :expr-stringifier :extract-text}}
