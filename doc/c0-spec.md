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
