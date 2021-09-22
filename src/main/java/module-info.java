/**
 * Provides the Gson type adapter factory {@link marcono1234.gson.recordadapter.RecordTypeAdapterFactory} which
 * adds JSON serialization and deserialization support for Record classes (Java 16 feature).
 */
@SuppressWarnings("JavaModuleNaming")
module marcono1234.gson.recordadapter {
    requires transitive com.google.gson;

    exports marcono1234.gson.recordadapter;
}
