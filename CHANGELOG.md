# Changelog

## [Unreleased ???] - ???

- Fix `ParameterizedTypeImpl` not supporting empty type arguments
- Update Gson dependency to 2.9.1 and add `RecordComponentNamingStrategy.UPPER_CASE_WITH_UNDERSCORES`, matching Gson's
  new `FieldNamingPolicy` entry
- Detect unsupported usage of Gson annotations `@Expose`, `@Since` and `@Until` on record components

## [0.2.0] - 2022-01-22

### Breaking changes
- Allow unknown JSON properties by default ([#1](https://github.com/Marcono1234/gson-record-type-adapter-factory/issues/1))
  - `RecordTypeAdapterFactory.DEFAULT` (and the no-args constructor) has been changed to allow unknown JSON properties.
    Previously unknown properties were disallowed by default.
  - The method `RecordTypeAdapterFactory.Builder.allowUnknownProperties()` has been removed and instead a new method
    `disallowUnknownProperties()` has been added.

### Changes
- Update Gson dependency to 2.8.9
- Make Gradle builds reproducible
- Make exception for clashing property names deterministic
- Fix bug in internal `GenericArrayTypeImpl.equals`

## [0.1.0] - 2021-09-27

Initial release
