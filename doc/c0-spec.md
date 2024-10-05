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
    int a = 2;
}
```
```status
Success
```
