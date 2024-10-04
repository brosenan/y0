# $c_0$ Language Spec

This is a language spec for the $c_0$ example language.

Language: `c0`

## Variables

$c_0$ supports variable definitions.

```c
int foo() {
    int a = 1;
}
```
```status
Success
```

If the assigned value does not match the variable type, an error is reported.
```c
int foo() {
    int a = 1.2;
}
```
```status
ERROR: Type mismatch. Variable a was given type "int" but was assign value of type "float" in [:func_def "int" foo ...]
```
