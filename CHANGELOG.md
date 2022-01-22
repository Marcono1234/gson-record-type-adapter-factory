# Changelog

## [Unreleased 0.2.0] - ???

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
