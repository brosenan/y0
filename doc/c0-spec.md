# $C_0$ is not C

This is a language spec for the $C_0$ example language. This is a C-like
language provided here as an example of how to define a language using $y_0$.

Language: `c0`

## Essentials

We will start our description of $C_0$ with types and expressions. However, in
order to be able to provide examples, we need to have enough language constructs
to be able to place expressions and types in.

In this section we define those language constructs, but do not go beyond what
is essential to start the discussion.

### Void Functions

A `void` function is a function that does not return a value, and is therefore
only called for its side-effect.

```c
void foo() {}
```
```status
Success
```

### Variable Definitions

A function (and in particular, a `void` function) can include zero or more
_statements_. There are many statement types in $C_0$, but at this point we
introduce _variable definitions_, of the form `type name = value;`

```c
void foo() {
    int32 a = 2;
}
```
```status
Success
```

A variable that is defined can be used as value in subsequent definitions.

```c
void foo() {
    int32 a = 2;
    int32 b = a;
}
```
```status
Success
```

The example above would not have been valid in reverse.

```c
void foo() {
    int32 b = a;
    int32 b = a;
}
```
```status
ERROR: Invalid expression a in int32 b = a;
```

#### Implicit Variable Definitions

If we replace the type in a variable definition with the keyword `var`, the type
of the new variable is determined by inferring the type of the expression.

```c
void foo() {
    var a = 2;
}
```
```status
Success
```

## Numeric Types and Expressions

$C_0$ supports the following numeric types:

```c
void foo() {
    int8 a = 0;
    int16 b = 0;
    int32 c = 0;
    int64 d = 0;
    
    uint8 e = 0;
    uint16 f = 0;
    uint32 g = 0;
    uint64 h = 0;

    float32 i = 0;
    float64 j = 0;
}
```
```status
Success
```

As shown above, all numeric types can receive integer literals. However, only
`floatX` types can receive float literals.

```c
void foo() {
    float32 i = 0.12345;
    float64 j = 1.2345e+6;
}
```
```status
Success
```

Integer types cannot.

```c
void foo() {
    float32 a = 0.12345; 
    int32 b = 0.12345;
}
```
```status
ERROR: int32 is not a floating-point type when assigning floating point literal 0.12345 in int32 b = 0.12345;
```
While we haven't yet introduced non-numeric types, integer literals can only be
assinged to numeric types. Trying to assign an integer literal to, e.g., a
[pointer](#pointers-and-addresses) (or any other non-numeric type) results in an error.

```c
void foo() {
    *int64 a = 1;
}
```
```status
ERROR: *int64 is not a numeric type in *int64 a = 1;
```


### Assignability

A variable of one type can be assigned to a variable of another type if this
assignment cannot result in overflow. This means that for integer types,
assignment is possible from a type of a given width to the same or larger width.

```c
void foo() {
    int8 a = 100;
    int8 a1 = a;
    int16 b = a;
    int16 b1 = b;
    int32 c = b;
    int32 c1 = c;
    int64 d = c;
    int64 d1 = d;
}

void bar() {
    uint8 a = 100;
    uint8 a1 = a;
    uint16 b = a;
    uint16 b1 = b;
    uint32 c = b;
    uint32 c1 = c;
    uint64 d = c;
    uint64 d1 = d;
}
```
```status
Success
```

In contrast, assigning a wide-integer to a narrow one can cause overflow and is
therefore not allowed.

```c
void foo() {
    int16 a = 100;
    int8 b = a;
}
```
```status
ERROR: Type int16 cannot be used in this context: Type mismatch. Expression a is of type int16 but type int8 was expected in int8 b = a;
```

When mixing signed and unsigned values, assignment is only allowed from strictly
narrower values.
```c
void foo() {
    int8 a = 100;
    uint16 b = a;
    int32 c = b;
    uint64 d = c;
}

void bar() {
    uint8 a = 100;
    int16 b = a;
    uint32 c = b;
    int64 d = c;
}
```
```status
Success
```

Floating point types are assignable from strictly narrower numeric types (signed
or unsigned integers, floating point), and from themeselves.

```c
void foo() {
    int8 a = 100;
    float32 a1 = a;
    float64 a2 = a;

    int16 b = 200;
    float32 b1 = b;
    float64 b2 = b;

    int32 c = 300;
    float64 c2 = c;

    float64 d = b1;
}

void bar() {
    uint8 a = 100;
    float32 a1 = a;
    float64 a2 = a;

    uint16 b = 200;
    float32 b1 = b;
    float64 b2 = b;

    uint32 c = 300;
    float64 c2 = c;
}
```
```status
Success
```

### Inferring Numeric Types

Despite the fact that numeric literals can be assigned to many numeric types,
when inferring their type (e.g., using the `var` keyword), they evaluate to
specific types.

Integer literals are inferred to be of type `int64`, as can be seen from the
error message in the following example.

```c
void foo() {
    var x = 2;
    int32 a = x;
}
```
```status
ERROR: Type [:int64_type] cannot be used in this context: Type mismatch. Expression x is of type [:int64_type] but type int32 was expected in int32 a = x;
```

Floating-point literals are inferred as `float64`.

```c
void foo() {
    var x = 3.14;
    float32 a = x;
}
```
```status
ERROR: Type [:float64_type] cannot be used in this context: Type mismatch. Expression x is of type [:float64_type] but type float32 was expected in float32 a = x;
```

### Explicit Type Conversion

While implicit type conversion is allowed only in cases where overflow is not
possible, explicit conversion is allowed between any two numeric types.

Explicit conversion uses the _initializer list_ (`{}`), where for numeric values
a list of length 1 is expected.

```c
void foo() {
    int32 a = {3.14};
}
```
```status
Success
```

```c
void foo() {
    int32 a = {3.14, 1.44};
}
```
```status
ERROR: An initializer list for a numeric type must have exactly one element. 3.14, 1.44 is given in int32 a = {3.14, 1.44};
```

The single value in the initializer list must be a numeric expression.
```c
void foo() {
    int32 a = 2;
    int32 b = {&a};
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in assignment to numeric type [:int32_type] in int32 b = {&a};
```

Because initializer lists can convert from any numeric type to any other numeric
type, it is not possible to infer their type.

```c
void foo() {
    var a = {2};
}
```
```status
ERROR: Cannot infer the type of initializer list {2} in var a = {2};
```

For inference to work, an explicit type must be provided to the initializer
list.


```c
void foo() {
    var a = uint16{2};
}
```
```status
Success
```

A typed initializer list requires that the initializer list is valid for the
given type. For numeric types, this means that the list must contain exactly one
element that must be numeric.

```c
void foo() {
    var a = int32{3.14, 1.44};
}
```
```status
ERROR: An initializer list for a numeric type must have exactly one element. 3.14, 1.44 is given in var a = int32{3.14, 1.44};
```

### Arithmetic Operators

$C_0$ supports the following arithmetic operators: `+`, `-` (binary and unary),
`*`, `/` and `%`, with semantic similar to that in C.

An expression comprising of arithmetic operator is assignable to a given numeric
type if and only if all of the values participating in the expression are
assignable to that type.

```c
void foo() {
    int8 a = 1;
    int16 b = 2;
    int32 c = 3;
    int64 d = 4;

    int64 res = -a+b-c*d/a%b;
}
```
```status
Success
```

If the target type would be narrower, this would not have been valid.

```c
void foo() {
    int8 a = 1;
    int16 b = 2;
    int32 c = 3;
    int64 d = 4;

    int32 res = -a+b-c*d/a%b;
}
```
```status
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression d is of type int64 but type int32 was expected in int32 res = -a+b-c*d/a%b;
```

If the target type is not known, an arithmetic expression is only valid if all
operands have the same type.

```c
void foo() {
    int64 a = 1;
    int64 b = 2;
    int64 c = 3;
    int64 d = 4;

    var res = -a+b-c*d/a%b;
}
```
```status
Success
```

If one of the operands has a different type, the expression does not compile.

```c
void foo() {
    int64 a = 1;
    int8 b = 2;
    int64 c = 3;
    int64 d = 4;

    var res = -a+b-c*d/a%b;
}
```
```status
ERROR: The two operands of -a+b do not agree on types. -a has type int64 while b has type int8 in var res = -a+b-c*d/a%b;
```

Note that the same expression would have compiled if only the `var` keyword
would have been replaced with a concrete, appropriate type.

```c
void foo() {
    int64 a = 1;
    int8 b = 2;
    int64 c = 3;
    int64 d = 4;

    int64 res = -a+b-c*d/a%b;
}
```
```status
Success
```

Arithmetic expressions support all numeric types.

```c
void foo() {
    int8 a = 1;
    var ar = a+a;
    int16 b = 2;
    var br = b-b;
    int32 c = 3;
    var cr = c*c;
    int64 d = 4;
    var dr = d/d;
}

void bar() {
    uint8 a = 1;
    var ar = a+a;
    uint16 b = 2;
    var br = b-b;
    uint32 c = 3;
    var cr = c*c;
    uint64 d = 4;
    var dr = d/d;
}

void baz() {
    float32 a = 1.1;
    var ar = a+a;
    float64 b = 2.2;
    var br = b-b;
}
```
```status
Success
```

Non-numeric types (e.g., pointers) are not supported.

```c
void foo() {
    int64 a = 1;
    int64 b = 2;

    var res = &a+&b;
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in expression &a+&b in var res = &a+&b;
```

This is even the case if we provide the target type explicitly.

```c
void foo() {
    int64 a = 1;
    int64 b = 2;

    *int64 res = &a+&b;
}
```
```status
ERROR: *int64 is not a numeric type in expression &a+&b in *int64 res = &a+&b;
```

What's true for binary operators is also true for the unary `-` operator.

```c
void foo() {
    int64 a = 1;

    var res = -&a;
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in expression -&a in var res = -&a;
```

```c
void foo() {
    int64 a = 1;

    *int64 res = -&a;
}
```
```status
ERROR: *int64 is not a numeric type in expression -&a in *int64 res = -&a;
```

## Pointers and Addresses

$C_0$ supports pointer types. To simplify parsing, $C_0$ takes its syntax for
pointer types from [Go](https://go.dev/tour/moretypes/1), placing the `*`
_before_ and not _after_ the pointee-type.

A pointer can be initialized to `null`.

```c
void foo() {
    *int32 p = null;
}
```
```status
Success
```

`null` can only be assigned to a pointer type.

```c
void foo() {
    int32 n = null;
}
```
```status
ERROR: null can only be assigned to a pointer type. Given  int32 instead in int32 n = null;
```

A pointer can be assigned the _address_ (`&`) of a variable of the pointee type.

```c
void foo() {
    int32 a = 42;
    *int32 p = &a;
}
```
```status
Success
```

Assigning the address of a different type will result in an error.

```c
void foo() {
    int16 a = 42;
    *int32 p = &a;
}
```
```status
ERROR: Type mismatch. Expression &a is of type [:pointer_type t] but type *int32 was expected in *int32 p = &a;
```

## Type Aliases

Type aliases allow types to be given names. In the following example we define a
type alias named `large_num` which is an alias for `int64`. Then we define a
variable of that type.

```c
void foo() {
    type large_num = int64;
    large_num a = 1000000000;
}
```
```status
Success
```

In contrast, it is an error to use a type without first defining it.

```c
void foo() {
    large_num a = 1000000000;
}
```
```status
ERROR: large_num is not a type alias in large_num a = 1000000000;
```

Type aliases can only be given to valid types.

```c
void foo() {
    type large_num = huge_num;
}
```
```status
ERROR: huge_num is not a type alias in type large_num = huge_num;
```

A type alias for floating point types can accept floating-point literals.

```c
type double = float64;

void foo() {
    double a = 3.14;
}
```
```status
Success
```

But not type aliases for non-float types.

```c
type long = int64;

void foo() {
    long a = 3.14;
}
```
```status
ERROR: int64 is not a floating-point type when assigning floating point literal 3.14 in long a = 3.14;
```

Assignability rules apply to type aliases the same way they apply to the
underlying types, for both the source and target types. The following is valid:

```c
type long = int64;
type short = int16;

void foo() {
    short a = 12;
    long b = a;
}
```
```status
Success
```
While the following is not:

```c
type long = int64;
type short = int16;

void foo() {
    long a = 12;
    short b = a;
}
```
```status
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression a is of type long but type short was expected in short b = a;
```

Initializer lists apply to type aliases as if they were their respective
underlying types. The following is valid:

```c
type short = int16;

void foo() {
    short a = {3.14};
}
```
```status
Success
```

However, the following is not:

```c
type short = int16;

void foo() {
    short a = {3.14};
    short b = {&a};
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in assignment to numeric type [:int16_type] in short b = {&a};
```

## Structs

A `struct` is a type that contains zero or more different, individually typed
_fields_. A `struct` type is specified using the `struct` keyword, followed by a
braced block (`{}`) consisting of field definitions.

```c
void foo() {
    struct {
        float64 real;
        float64 imaginary;
    } z = {1, 2};

    float64 re = z.real;
    float64 im = z.imaginary;
}
```
```status
Success
```

The example above defines variable `z` with a one-off `struct` type containing
two `float64` fields, `real` and `imaginary`. It initializes them, assigning
`real` to 1 and `imaginary` to 2. Then we assign these values into other
`float64` variables, by using the `.` operator on `z`.

This way of using `struct` types is not common. Insted, they are usually given
aliases, as in the following example:

```c
type Complex64 = struct {
    float64 real;
    float64 imaginary;
};

void foo() {
    Complex64 z = {1, 2};

    float64 re = z.real;
    float64 im = z.imaginary;
}
```
```status
Success
```

A `struct` is only valid if the types inside it are valid.

```c
type Foo = struct {
    Bar bar;
};
```
```status
ERROR: Bar is not a type alias in Bar bar;
```

With that said, recursive type definitions are allowed, that is, a type alias
given to a `struct` is considered when type-checking the `struct` itself.

```c
type Tree = struct {
    int64 key;
    *Tree left;
    *Tree right;
};
```
```status
Success
```

The `.` operator can be used for both `struct` types and pointers to `struct`s.
In the following example we redefine the tree from the previous example and
access a node in a fixed path along the tree.


```c
type Tree = struct {
    int64 key;
    *Tree left;
    *Tree right;
};

void foo() {
    Tree root = {42, null, null};
    var some_node = root.left.left.right.left.right.right;
}
```
```status
Success
```

It cannot, however, be used on non-aggregate types such as scalar types.

```c
void foo() {
    int64 foo = 3;
    var something = foo.bar;
}
```
```status
ERROR: int64 is not an aggergate type when accessing a member of foo in var something = foo.bar;
```

The same applies to pointer types of non-aggregate types.

```c
void foo() {
    int64 foo = 3;
    *int64 foo_p = &foo;
    var something = foo_p.bar;
}
```
```status
ERROR: int64 is not an aggergate type when accessing a member of foo_p in var something = foo_p.bar;
```

The same applies to type aliases of non-aggregate types.

```c
type long = int64;
void foo() {
    long foo = 3;
    var something = foo.bar;
}
```
```status
ERROR: int64 is not an aggergate type when accessing a member of foo in var something = foo.bar;
```

Only a member of the `struct` is allowed to the right of the `.`. In the
following example we get an error because we place a variable name there.

```c
type Complex64 = struct {
    float64 real;
    float64 imaginary;
};

void foo() {
    Complex64 z = {1, 2};
    int64 something_else = 42;

    int64 wrong = z.something_else;
}
```
```status
ERROR: something_else is not a member of z in int64 wrong = z.something_else;
```

### Struct Initializer List

The initializer list for a `struct` type must have one element for each field in
the struct. In the following, we get an error for making the list too short.

```c
type Complex64 = struct {
    float64 real;
    float64 imaginary;
};

void foo() {
    Complex64 z = {1};
}
```
```status
ERROR: Too few elements in initializer list. float64 imaginary; does not have an initializer in Complex64 z = {1};
```

Similarly, in the following example we get an error for making the list too
long.

```c
type Complex64 = struct {
    float64 real;
    float64 imaginary;
};

void foo() {
    Complex64 z = {1, 2, 3};
}
```
```status
ERROR: Too many elements in initializer list. 3 are extra in Complex64 z = {1, 2, 3};
```

The types of values in the initializer list must be appropriate initializers for
the respective fields. In the following example we define a `Point` `struct`,
with two `int16` coordinates, and try to initialize one of them with a
floating-point literal. This of-course is not allowed.


```c
type Point = struct {
    int16 x;
    int16 y;
};

void foo() {
    Point p = {100, 200.3};
}
```
```status
ERROR: int16 is not a floating-point type when assigning floating point literal 200.3 in Point p = {100, 200.3};
```

### Unions

Unions are sets of fields that occupy the same space, and therefore only one of
them can be set at any given time. $C_0$ handles unions in a very different way
than C's. The main problem with unions in C is that the union itself does not
contain the information which field is actually set, and therefore does not
allow for its own safe interpretation.

In $C_0$, a `union` is always part of a `struct`, contributing two dinstinct
parts to it: the union of types within the `union` and an enumeration,
specifying which field (if any) is set.

The following example shows `struct` containing a `union` of different integer
values.

```c
type Int = struct {
    union width {
        int8 char_val;
        int16 short_val;
        int32 int_val;
        int64 long_val;
    }
};
```
```status
Success
```

The types used for the options need to be valid types.

```c
type Int = struct {
    union width {
        int8 char_val;
        int14 shorter_val;
        int32 int_val;
        int64 long_val;
    }
};
```
```status
ERROR: int14 is not a type alias in int14 shorter_val;
```

#### Unions in Initializer Lists

To initialize a union from within an intializer list we need to provide two
pieces of information: (1) which of the option is being initialized and (2) the
value for that option. This is done using the syntax `opt=val`, where `opt` is
the name of the option and `val` is an expression to initialize it.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
}
```
```status
Success
```

It is an error to initialize a `union` with a normal expression.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {42};
}
```
```status
ERROR: Initialization for union width needs to be done with an option initializer, but 42 was given in Int n = {42};
```

The `opt` must be one of the options in the union.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {wrong_name=42};
}
```
```status
ERROR: wrong_name is not an option in union width in Int n = {wrong_name=42};
```

The value (`val`) must be an appropriate initializer for the option.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int8_val=int16{42}};
}
```
```status
ERROR: Type int16 cannot be used in this context: Type mismatch. Expression int16{42} is of type int16 but type int8 was expected for option int8_val in Int n = {int8_val=int16{42}};
```

Obviously, `union`s can be combined with other `struct` members, and the
initializer lists expect the option initializer to be in the `union`'s position
in the list.

```c
type IntPlus = struct {
    int64 something_before;
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
    float64 something_after;
};

void foo() {
    IntPlus n = {42, int64_val=42, 3.14};
}
```
```status
Success
```

#### Accessing Union Fields and the `case` Expression

Union fields cannot be accessed directly.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int64 val = n.int64_val;
}
```
```status
ERROR: int64_val is not a member of n in int64 val = n.int64_val;
```

The reason for this is that there is no guarantee that at runtime the field
being accessed will be the one that has been populated, and since `union` fields
share the same memory space, accessing a field other than the one that has been
populated may lead to bugs.

To solve this, $C_0$ offers the `case` expression. This is an expression that
allows programmers to provide different expressions for different `union`
options.

In the following example, we convert an `Int` into `int16`, applying (unsafe)
type conversions where needed.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        int64_val: {x}
    };
}
```
```status
Success
```

As can be seen above, the `case` expression defines a variable (`x` in this
example), with the value of the union. It then consists of cases for the
different fields in the union. In each case, the variable (`x`) is assigned the
value of the respective field and has that type.

The expression to the right of the `=` must be a `union`. The following example
fails to compile because it is not.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
    float64 something_else;
};

void foo() {
    Int n = {int64_val=42, 3.14};
    int16 as_int16 = case (x = n.something_else) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        int64_val: {x}
    };
}
```
```status
ERROR: n.something_else has non-union type float64 given in a case expression in int16 as_int16 = case (x = n.something_else) { ... };
```

Cases must be unique. The following example results in a compilation error due
to a duplicate case for `int32_val`

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        int32_val: {x}
    };
}
```
```status
ERROR: The rule for (option-is-covered v) conflicts with a previous rule defining (option-is-covered v) in predicate c0/option-is-covered with arity 1
```

**TODO**: Improve this error message.

The cases in the case expression must all be options in the `union`. The
following example fails to compile due to a misspelled option.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int17_val: x,
        int32_val: {x},
        int64_val: {x}
    };
}
```
```status
ERROR: int17_val is not an option for n.width in int16 as_int16 = case (x = n.width) { ... };
```

All cases must be covered. In the following example we get an error for not
covering `int64_val`.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x}
    };
}
```
```status
ERROR: Union option int64_val is not covered by case expression for n.width in int16 as_int16 = case (x = n.width) { ... };
```

A `default` clause removes the need for full coverage, as it handles the options
not covered by cases.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        default: 0
    };
}
```
```status
Success
```

The `default` case must appear last.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        default: 0,
        int32_val: {x}
    };
}
```
```status
ERROR: int32_val: {x} appear(s) after the default case for n.width in int16 as_int16 = case (x = n.width) { ... };
```

In each case in a `case` expression (except `default`), the variable whose name
was provided in the parentheses before the `=` is bound to the value (and thus,
type) of the `union` field mentioned in the case.

We demonstrate this in the following example when we try to implicitly convert
`int64_val` to `int16`.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        int64_val: x
    };
}
```
```status
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression x is of type int64 but type int16 was expected in int16 as_int16 = case (x = n.width) { ... };
```

In a `default` case, the case variable (`x` in the previous examples) is not
available.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        default: x
    };
}
```
```status
ERROR: Invalid expression x in default case in int16 as_int16 = case (x = n.width) { ... };
```

Like the other cases, the `default` case must evaluate to the case expression's
target type.

```c
type Int = struct {
    union width {
        int8 int8_val;
        int16 int16_val;
        int32 int32_val;
        int64 int64_val;
    }
};

void foo() {
    Int n = {int64_val=42};
    int16 as_int16 = case (x = n.width) {
        int8_val: x,
        int16_val: x,
        int32_val: {x},
        default: 3.14
    };
}
```
```status
ERROR: int16 is not a floating-point type when assigning floating point literal 3.14 in default case in int16 as_int16 = case (x = n.width) { ... };
```

## Arrays and Slices

An array is a type containing a fixed number of instances of another type. The
number of instances must be known at compile-time.

In the following example we define an array of size 4 and element-type `int64`.
We initialize the array with the values 1, 2, 3 and 4.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
}
```
```status
Success
```

As can be seen above, the syntax for the array type is `[n]t`, where `n` is an
integer representing the size of the array (number of elements) and `t` is a
type. If `t` is not a valid type, an error is reported.

```c
void foo() {
    [4]foo my_arr = {1, 2, 3, 4};
}
```
```status
ERROR: foo is not a type alias in [4]foo my_arr = {1, 2, 3, 4};
```

The length of the initializer list must match the size of the array.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4, 5};
}
```
```status
ERROR: The length of the initializer list is of size 5 but the array is of size 4 in [4]int64 my_arr = {1, 2, 3, 4, 5};
```

Each element in the initializer list should be assignable to the array's element
type.

```c
void foo() {
    [4]int64 my_arr = {1, 2.0, 3, 4};
}
```
```status
ERROR: int64 is not a floating-point type when assigning floating point literal 2.0 in [4]int64 my_arr = {1, 2.0, 3, 4};
```

### Element Access

Accessing an element of an array is done using the `[]` operator.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    int64 elem = my_arr[2];
}
```
```status
Success
```

An element access expression is of the form `arr[idx]`, where `arr` is
considered the _array expression_ and `idx` is the _index expression_.

The array expression must evaluate to an indexable type (e.g., an array).

```c
void foo() {
    float64 my_non_arr = 3.14;
    int64 elem = my_non_arr[2];
}
```
```status
ERROR: float64 is not an indexable type trying to access element of my_non_arr in int64 elem = my_non_arr[2];
```

Array types can be type-aliased.

```c
type vec3 = [3]float64;
void foo() {
    vec3 my_vec = {1.0, 2.0, 3.0};
    float64 y = my_vec[1];
}
```
```status
Success
```

... but only if the alias is to an indexable type.

```c
type scalar = float64;
void foo() {
    scalar my_scalar = {1.0};
    float64 y = my_scalar[1];
}
```
```status
ERROR: float64 is not an indexable type trying to access element of my_scalar in float64 y = my_scalar[1];
```

The index expression must be assignable to `uint64`.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    int64 elem = my_arr[2.0];
}
```
```status
ERROR: [:uint64_type] is not a floating-point type when assigning floating point literal 2.0 in int64 elem = my_arr[2.0];
```

The resulting expression is of type array's element type. We demonstrate this in
the following example by trying to assign the value of an element of a `int64`
array to a `int32` variable.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    int32 elem = my_arr[2];
}
```
```status
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression my_arr[2] is of type int64 but type int32 was expected in int32 elem = my_arr[2];
```

### Slices

A slice is a pair of memory addresses marking the beginning and end of a
continuous memory region containing values of the same type located one next to
the other. A slice allows element access similar to an array but unlike an
array, does not come with preallocated space, but rather points to memory
allocated by some other means.

A slice type has the syntax `[]t`, where `t` is the element type. In the
following example we define an array and then create a slice that points
to its elements.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []int64 my_slice = my_arr;
}
```
```status
Success
```

The element type must be a valid type.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []not_a_type my_slice = my_arr;
}
```
```status
ERROR: not_a_type is not a type alias in []not_a_type my_slice = my_arr;
```

When initializing a slice with an array, the slice's element type must match
type array's element type.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []int32 my_slice = my_arr;
}
```
```status
ERROR: Cannot assign to slice with element type int64 into a slice with element type int32 Type mismatch. Expression my_arr is of type [4]int64 but type []int32 was expected in []int32 my_slice = my_arr;
```

**TODO:** Fix this error message.

A slice can also be assigned the value of a different slice.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []int64 my_slice1 = my_arr;
    []int64 my_slice2 = my_slice1;
}
```
```status
Success
```

Assigning other types (non-slice types) is not premitted.

```c
void foo() {
    int64 foo = 1234;
    []int64 my_slice = foo;
}
```
```status
ERROR: Cannot assign non-slice type to a slice. Type mismatch. Expression foo is of type int64 but type []int64 was expected in []int64 my_slice = foo;
```

Similar to an array, it is possible to access an element of a slice using the
`[]` operator.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []int64 my_slice = my_arr;
    int64 my_element = my_slice[3];
}
```
```status
Success
```

The type of the element is the element-type.

```c
void foo() {
    [4]int64 my_arr = {1, 2, 3, 4};
    []int64 my_slice = my_arr;
    int32 my_element = my_slice[3];
}
```
```status
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression my_slice[3] is of type int64 but type int32 was expected in int32 my_element = my_slice[3];
```
