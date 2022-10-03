:warning: This library is currently experimental, its behavior and API might change in the future.

---

[![JitPack badge](https://img.shields.io/jitpack/v/github/Marcono1234/gson-record-type-adapter-factory)](https://jitpack.io/#Marcono1234/gson-record-type-adapter-factory)
[![Javadoc badge](https://img.shields.io/badge/-javadoc-success)](https://jitpack.io/com/github/Marcono1234/gson-record-type-adapter-factory/latest/javadoc/)

# Gson Record type adapter factory

[Gson](https://github.com/google/gson) `TypeAdapterFactory` implementation for Java
[Record classes](https://docs.oracle.com/en/java/javase/17/language/records.html) (Java 16 feature). This project
was inspired by the discussion on [Gson issue #1794](https://github.com/google/gson/issues/1794).

## Features
- [`@SerializedName`](https://javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/annotations/SerializedName.html) on Record components
- [`@JsonAdapter`](https://javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/annotations/JsonAdapter.html) on Record components
- Generic Record classes  
(type resolution differs slightly from Gson's implementation, hopefully making it easier to use)

## Installation
Currently this library is not published to Maven Central. You can either [build the project locally](#building)
or you can [use JitPack as Maven repository](https://jitpack.io/#Marcono1234/gson-record-type-adapter-factory) serving this library.

When using JitPack it is recommended to put the jitpack.io repository last in the list of declared repositories for
better performance and to avoid pulling undesired dependencies from it. When using Gradle as build tool you should also
use [repository content filtering](https://docs.gradle.org/current/userguide/declaring_repositories.html#sec:repository-content-filtering):
```kotlin
repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://jitpack.io")
            }
        }
        filter {
            // Only use JitPack for the `gson-record-type-adapter-factory` library
            includeModule("com.github.Marcono1234", "gson-record-type-adapter-factory")
        }
    }
}
```

## Usage
This type adapter factory can either be used by [registering it with a `GsonBuilder`](https://javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/GsonBuilder.html#registerTypeAdapterFactory(com.google.gson.TypeAdapterFactory)):
```java
Gson gson = new GsonBuilder()
    .registerTypeAdapterFactory(RecordTypeAdapterFactory.DEFAULT)
    .create();
```

Or it can be referenced using a [`@JsonAdapter`](https://javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/annotations/JsonAdapter.html)
annotation placed on a Record class:
```java
@JsonAdapter(RecordTypeAdapterFactory.class)
record MyRecord(int i) { }
```

A customized type adapter factory can be created using `RecordTypeAdapterFactory.builder()`.

## Changelog

See [`CHANGELOG.md`](./CHANGELOG.md).

## Building
This project uses Gradle for building; just run:
```
./gradlew build
```

It is built against Java 17, but there is no need to manually install the correct JDK; Gradle's [toolchain](https://docs.gradle.org/current/userguide/toolchains.html)
feature automatically downloads the needed JDK. Some IDEs do not support toolchains yet, so you might have to
configure them manually.

### Creating a release

This project uses the [axion-release-plugin](https://axion-release-plugin.readthedocs.io/en/latest/) to create releases.
It is configured to automatically perform additional tasks, such as updating the [changelog](./CHANGELOG.md) by
replacing the placeholder `[Unreleased ???] - ???` with the version number and date.

A new release can be created with:
```
./gradlew release
```
This will automatically increment the version number. It is also possible to adjust [how the version number is incremented](https://axion-release-plugin.readthedocs.io/en/latest/configuration/version/#incrementing)
or to [force a version number](https://axion-release-plugin.readthedocs.io/en/latest/configuration/force_version/).

Use `-Prelease.dryRun` to perform a [dry run](https://axion-release-plugin.readthedocs.io/en/latest/configuration/dry_run/).

## Known issues / limitations
- **Using a type adapter factory as value for `@JsonAdapter` on a Record component and calling `Gson.getDelegateAdapter`
inside the factory does not work correctly.**  
The underlying issue is a [known bug](https://github.com/google/gson/issues/1028) in the implementation of
`Gson.getDelegateAdapter`. Hopefully that can be fixed in a way which also allows this type adapter factory to work
correctly.
- **Record type adapter factory is not detected as being reflection-based by Gson's `TypeAdapterRuntimeTypeWrapper`.**  
Gson's internal class `TypeAdapterRuntimeTypeWrapper` is used by the built-in reflection type adapter to decide whether
for a given value of a field the runtime type adapter or the compile-time type adapter should be used. The intention is
to prefer custom type adapters over reflection-based type adapters. So for example when a custom type adapter for class
`Base` exists and for its subclass `Sub` only the reflection-based adapter exists, then `Base`'s adapter should be
preferred. However, Gson does not recognize this Record type adapter factory as being reflection-based, so it will
always prefer it over any adapter for the base class.  
This issue can only occur when two or more type adapters for Record classes have been registered, so most use cases
will be unaffected by this.
- **Gson field exclusion logic is not supported.**  
The standard Gson exclusion annotations `@Expose`, `@Since` and `@Until` are not supported on record components and the
related `GsonBuilder` methods will have no effect because Gson does not expose this exclusion logic as public API.
Using these annotations on components will cause an exception.  
Similarly, `ExclusionStrategy.shouldSkipField` implementations have no effect on the serialization and deserialization
of Record components.
- **Reflection access filters are not considered.**  
Gson's [`ReflectionAccessFilter`](https://javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/ReflectionAccessFilter.html)
is not considered to determine whether this Record type adapter may serialize or deserialize a Record class or make its
canonical constructor or accessor methods accessible.

## License
This project [uses the MIT license](./LICENSE.txt); all contributions are implicitly under that license.
