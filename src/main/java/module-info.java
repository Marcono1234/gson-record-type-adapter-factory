/**
 * Provides the Gson type adapter factory {@link marcono1234.gson.recordadapter.RecordTypeAdapterFactory} which
 * adds JSON serialization and deserialization support for Record classes (Java 16 feature).
 */
// Suppress warning for module name ending with digits, see also https://bugs.openjdk.java.net/browse/JDK-8264488
// (uses javac and IntelliJ warning names)
@SuppressWarnings({"module", "JavaModuleNaming"})
module marcono1234.gson.recordadapter {
    requires transitive com.google.gson;

    exports marcono1234.gson.recordadapter;
}
