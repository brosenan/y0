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

This example would not have been valid without the definition of `a`.
```c
void foo() {
    int32 b = a;
}
```
```status
ERROR: Invalid expression a in void foo() { ... }
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
