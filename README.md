# Testify
Unofficial plugin providing Goland support for the `stretchr/testify` library for assertions and mocking in Go

## Features

### Warnings / Inspections
- Detects when a `testify.Mock` expectation using `mockObject.On("TestMethod", args).Return(return1, return2, ...)` does not match the underlying method:
  - mocking an unrecognized method
  - using the wrong types (or wrong number of args) in the argument matchers
  - using the wrong types (or wrong number of values) in the return values
- Detects when `assert.Equals()` seems to have swapped the "expected" and "actual" values

## Installation

If you'd like automatic updates, just add the `Testify` plugin using the plugins menu in your IDE.

Download the `.zip` file from the releases on GitHub, and install it in Goland's plugin settings: https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk

## Contributing

This plugin still has issues! Please report them in the Issues tab on github.

Have an idea for a new feature? Please suggest it in the Issues tab on github,
or feel free to open a PR if you'd like to implement it yourself!

I make this plugin for my own benefit but would be happy to improve it for others to benefit from.
