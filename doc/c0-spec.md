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
ERROR: Invalid expression a in void foo() { ... }
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
ERROR: int32 is not a floating-point type when assigning floating point literal 0.12345 in void foo() { ... }
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
ERROR: Type int16 cannot be used in this context: Type mismatch. Expression a is of type int16 but type int8 was expected in void foo() { ... }
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
ERROR: Type [:int64_type] cannot be used in this context: Type mismatch. Expression x is of type [:int64_type] but type int32 was expected in void foo() { ... }
```

Floating-point literals are inferred as `float64`.

```c
void foo() {
    var x = 3.14;
    float32 a = x;
}
```
```status
ERROR: Type [:float64_type] cannot be used in this context: Type mismatch. Expression x is of type [:float64_type] but type float32 was expected in void foo() { ... }
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
ERROR: An initializer list for a numeric type must have exactly one element. [[:expr ...] [:expr ...]] is given in void foo() { ... }
```

The single value in the initializer list must be a numeric expression.
```c
void foo() {
    int32 a = 2;
    int32 b = {&a};
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in assignment to numeric type [:int32_type] in void foo() { ... }
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
ERROR: Type int64 cannot be used in this context: Type mismatch. Expression d is of type int64 but type int32 was expected in void foo() { ... }
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
ERROR: The two operands of -a+b do not agree on types. -a has type int64 while b has type int8 in void foo() { ... }
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
ERROR: [:pointer_type t] is not a numeric type in expression &a+&b in void foo() { ... }
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
ERROR: *int64 is not a numeric type in expression &a+&b in void foo() { ... }
```

What's true for binary operators is also true for the unary `-` operator.

```c
void foo() {
    int64 a = 1;

    var res = -&a;
}
```
```status
ERROR: [:pointer_type t] is not a numeric type in expression -&a in void foo() { ... }
```

```c
void foo() {
    int64 a = 1;

    *int64 res = -&a;
}
```
```status
ERROR: *int64 is not a numeric type in expression -&a in void foo() { ... }
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
ERROR: null can only be assigned to a pointer type. Given  int32 instead in void foo() { ... }
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
ERROR: Type mismatch. Expression &a is of type [:pointer_type t] but type *int32 was expected in void foo() { ... }
```
