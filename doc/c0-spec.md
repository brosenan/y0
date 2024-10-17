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

