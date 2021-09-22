package marcono1234.gson.recordadapter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static marcono1234.gson.recordadapter.RecordTypeAdapterFactoryTest.getAdapter;
import static marcono1234.gson.recordadapter.RecordTypeAdapterFactoryTest.getDefaultAdapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for usage of {@link JsonAdapter @JsonAdapter} */
class JsonAdapterTest {
    static class Adapter extends TypeAdapter<String> {
        private final String prefix;

        public Adapter(String prefix) {
            this.prefix = prefix;
        }

        @SuppressWarnings("unused")
        public Adapter() {
            this("");
        }

        @Override
        public void write(JsonWriter out, String value) throws IOException {
            out.value(prefix + "write:" + value);
        }

        @Override
        public String read(JsonReader in) throws IOException {
            return prefix + "read:" + in.nextString();
        }
    }

    record WithCustomAdapter(
        @JsonAdapter(Adapter.class)
        String s
    ) { }

    @Test
    void test() throws IOException {
        TypeAdapter<WithCustomAdapter> typeAdapter = getDefaultAdapter(WithCustomAdapter.class);
        assertEquals("{\"s\":\"write:a\"}", typeAdapter.toJson(new WithCustomAdapter("a")));
        assertEquals(new WithCustomAdapter("read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    @Test
    void testCustomAdapterCreator() throws IOException {
        TypeAdapter<WithCustomAdapter> typeAdapter = getAdapter(
            WithCustomAdapter.class,
            RecordTypeAdapterFactory.builder().registerJsonAdapterCreator(c -> {
                assertEquals(Adapter.class, c);
                return Optional.of(new Adapter("creator-"));
            })
        );
        assertEquals("{\"s\":\"creator-write:a\"}", typeAdapter.toJson(new WithCustomAdapter("a")));
        assertEquals(new WithCustomAdapter("creator-read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    @Test
    void testMultipleCustomCreators() throws IOException {
        AtomicBoolean wasSecondCreatorCalled = new AtomicBoolean(false);
        TypeAdapter<WithCustomAdapter> typeAdapter = getAdapter(
                WithCustomAdapter.class,
                RecordTypeAdapterFactory.builder()
                    .registerJsonAdapterCreator(c -> {
                        assertEquals(Adapter.class, c);
                        // Creators are called in reverse registration order, so other creator should have
                        // already been used
                        assertTrue(wasSecondCreatorCalled.get());
                        return Optional.of(new Adapter("creator-"));
                    })
                    .registerJsonAdapterCreator(c -> {
                        assertEquals(Adapter.class, c);
                        wasSecondCreatorCalled.set(true);
                        return Optional.empty();
                    })
        );
        assertTrue(wasSecondCreatorCalled.get());
        assertEquals("{\"s\":\"creator-write:a\"}", typeAdapter.toJson(new WithCustomAdapter("a")));
        assertEquals(new WithCustomAdapter("creator-read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    /**
     * Creator using default constructor should always be fallback, even when custom creator
     * has been registered
     */
    @Test
    void testCustomAdapterCreatorFallbackToDefault() throws IOException {
        AtomicBoolean wasCreatorCalled = new AtomicBoolean(false);
        TypeAdapter<WithCustomAdapter> typeAdapter = getAdapter(
                WithCustomAdapter.class,
                RecordTypeAdapterFactory.builder().registerJsonAdapterCreator(c -> {
                    assertEquals(Adapter.class, c);
                    wasCreatorCalled.set(true);
                    return Optional.empty();
                })
        );
        assertTrue(wasCreatorCalled.get());
        assertEquals("{\"s\":\"write:a\"}", typeAdapter.toJson(new WithCustomAdapter("a")));
        assertEquals(new WithCustomAdapter("read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    static class AdapterWithoutDefaultConstructor {
        @SuppressWarnings("unused")
        public AdapterWithoutDefaultConstructor(int i) { }
    }

    record WithAdapterWithoutDefaultConstructor(
        @JsonAdapter(AdapterWithoutDefaultConstructor.class)
        int i
    ) { }

    @Test
    void testNoDefaultConstructor() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(WithAdapterWithoutDefaultConstructor.class));
        assertEquals("None of the creators can create an instance of adapter " + AdapterWithoutDefaultConstructor.class + " for " + WithAdapterWithoutDefaultConstructor.class.getName() + ".i; registered creators: DEFAULT_CONSTRUCTOR_INVOKER", e.getMessage());
    }

    static class ThrowingAdapter {
        public ThrowingAdapter() {
            throw new IllegalStateException("test");
        }
    }

    record WithThrowingAdapter(
        @JsonAdapter(ThrowingAdapter.class)
        int i
    ) { }

    @Test
    void testThrowingAdapter() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(WithThrowingAdapter.class));
        assertEquals("Creator DEFAULT_CONSTRUCTOR_INVOKER failed creating instance of adapter " + ThrowingAdapter.class + " for " + WithThrowingAdapter.class.getName() + ".i", e.getMessage());

        Throwable cause = e.getCause();
        assertEquals(JsonAdapterCreator.AdapterCreationException.class, cause.getClass());
        assertEquals("Failed invoking default constructor for " + ThrowingAdapter.class, cause.getMessage());

        cause = cause.getCause();
        assertEquals(IllegalStateException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }

    static class Factory implements TypeAdapterFactory {
        public Factory() { }

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            assertEquals(String.class, type.getType());
            @SuppressWarnings("unchecked")
            TypeAdapter<T> r = (TypeAdapter<T>) new TypeAdapter<String>() {
                @Override
                public void write(JsonWriter out, String value) throws IOException {
                    out.value("factory-write:" + value);
                }

                @Override
                public String read(JsonReader in) throws IOException {
                    return "factory-read:" + in.nextString();
                }
            };
            return r;
        }
    }

    record WithCustomAdapterFactory(
        @JsonAdapter(Factory.class)
        String s
    ) { }

    @Test
    void testFactory() throws IOException {
        TypeAdapter<WithCustomAdapterFactory> typeAdapter = getDefaultAdapter(WithCustomAdapterFactory.class);
        assertEquals("{\"s\":\"factory-write:a\"}", typeAdapter.toJson(new WithCustomAdapterFactory("a")));
        assertEquals(new WithCustomAdapterFactory("factory-read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    static class DelegatingFactory implements TypeAdapterFactory {
        public DelegatingFactory() { }

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != String.class) {
                return null;
            }

            TypeAdapter<String> delegate = gson.getDelegateAdapter(this, TypeToken.get(String.class));
            @SuppressWarnings("unchecked")
            TypeAdapter<T> r = (TypeAdapter<T>) new TypeAdapter<String>() {
                @Override
                public void write(JsonWriter out, String value) throws IOException {
                    delegate.write(out, "delegated-write:" + value);
                }

                @Override
                public String read(JsonReader in) throws IOException {
                    return "delegated-read:" + delegate.read(in);
                }
            };
            return r;
        }
    }

    record WithDelegatingAdapterFactory(
        @JsonAdapter(DelegatingFactory.class)
        String s
    ) { }

    @Disabled("Not working, see https://github.com/google/gson/issues/1028")
    @Test
    void testFactory_Delegate() throws IOException {
        TypeAdapter<WithDelegatingAdapterFactory> typeAdapter = getDefaultAdapter(WithDelegatingAdapterFactory.class);
        assertEquals("{\"s\":\"delegated-write:a\"}", typeAdapter.toJson(new WithDelegatingAdapterFactory("a")));
        assertEquals(new WithDelegatingAdapterFactory("delegated-read:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    static class Base {
        int i1;

        public Base(int i1) {
            this.i1 = i1;
        }
    }
    static class Sub extends Base {
        int i2;

        public Sub(int i1, int i2) {
            super(i1);
            this.i2 = i2;
        }
    }

    static class Serializer implements JsonSerializer<String> {
        public Serializer() { }

        @Override
        public JsonElement serialize(String src, Type typeOfSrc, JsonSerializationContext context) {
            assertEquals(String.class, typeOfSrc);

            // Verify that `context` works correctly
            {
                JsonObject actualJsonObject = context.serialize(new Sub(1, 2)).getAsJsonObject();
                JsonObject expectedJsonObject = new JsonObject();
                expectedJsonObject.addProperty("i1", 1);
                expectedJsonObject.addProperty("i2", 2);
                assertEquals(expectedJsonObject, actualJsonObject);
            }
            {
                // Serialize Sub as Base
                JsonObject actualJsonObject = context.serialize(new Sub(1, 2), Base.class).getAsJsonObject();
                JsonObject expectedJsonObject = new JsonObject();
                expectedJsonObject.addProperty("i1", 1);
                assertEquals(expectedJsonObject, actualJsonObject);
            }

            return new JsonPrimitive("serialize:" + src);
        }
    }

    record WithCustomSerializer(
        @JsonAdapter(Serializer.class)
        String s
    ) { }

    @Test
    void testToJson_Serializer() {
        TypeAdapter<WithCustomSerializer> typeAdapter = getDefaultAdapter(WithCustomSerializer.class);
        assertEquals("{\"s\":\"serialize:a\"}", typeAdapter.toJson(new WithCustomSerializer("a")));
    }

    @Test
    void testFromJson_Serializer() throws IOException {
        TypeAdapter<WithCustomSerializer> typeAdapter = getDefaultAdapter(WithCustomSerializer.class);
        // Should fall back to registered adapter
        assertEquals(new WithCustomSerializer("a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    static class Deserializer implements JsonDeserializer<String> {
        public Deserializer() { }

        @Override
        public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            assertEquals(String.class, typeOfT);

            // Verify that `context` works correctly
            JsonArray jsonArray = new JsonArray();
            jsonArray.add(1);
            jsonArray.add(true);
            List<Object> actualList = context.deserialize(jsonArray, List.class);
            assertEquals(List.of(1.0, true), actualList);

            return "deserialize:" + json.getAsString();
        }
    }

    record WithCustomDeserializer(
        @JsonAdapter(Deserializer.class)
        String s
    ) { }

    @Test
    void testToJson_Deserializer() {
        TypeAdapter<WithCustomDeserializer> typeAdapter = getDefaultAdapter(WithCustomDeserializer.class);
        // Should fall back to registered adapter
        assertEquals("{\"s\":\"a\"}", typeAdapter.toJson(new WithCustomDeserializer("a")));
    }

    @Test
    void testFromJson_Deserializer() throws IOException {
        TypeAdapter<WithCustomDeserializer> typeAdapter = getDefaultAdapter(WithCustomDeserializer.class);
        assertEquals(new WithCustomDeserializer("deserialize:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    static class NonAdapterClass {
        public NonAdapterClass() { }

        @Override
        public String toString() {
            return "non-adapter";
        }
    }

    record WithNonAdapter(
        @JsonAdapter(NonAdapterClass.class)
        int i
    ) { }

    record WithDifferentAdapterType(
        @JsonAdapter(WithDifferentAdapterType.class)
        String s
    ) {
        static TypeAdapter<String> CUSTOM_ADAPTER = new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, String value) throws IOException {
                out.value("write-custom:" + value);
            }

            @Override
            public String read(JsonReader in) throws IOException {
                return "read-custom:" + in.nextString();
            }
        };
    }

    /**
     * Test that adapter returned by JsonAdapterCreator does not have to be
     * an instance of the specified class
     */
    @Test
    void testDifferentAdapterType() throws IOException {
        TypeAdapter<WithDifferentAdapterType> typeAdapter = getAdapter(
            WithDifferentAdapterType.class,
            RecordTypeAdapterFactory.builder().registerJsonAdapterCreator(c -> {
                if (c == WithDifferentAdapterType.class) {
                    return Optional.of(WithDifferentAdapterType.CUSTOM_ADAPTER);
                } else {
                    return Optional.empty();
                }
            })
        );

        assertEquals("{\"s\":\"write-custom:a\"}", typeAdapter.toJson(new WithDifferentAdapterType("a")));
        assertEquals(new WithDifferentAdapterType("read-custom:a"), typeAdapter.fromJson("{\"s\":\"a\"}"));
    }

    @Test
    void testNonAdapterClass() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(WithNonAdapter.class));
        assertEquals("Adapter non-adapter of type " + NonAdapterClass.class.getName() + " created by DEFAULT_CONSTRUCTOR_INVOKER for " + WithNonAdapter.class.getName() + ".i is not supported", e.getMessage());
    }

    static class NullSafeAdapter extends TypeAdapter<String> {
        public NullSafeAdapter() { }

        @Override
        public void write(JsonWriter out, String value) throws IOException {
            assertNull(value);
            out.value("write-null");
        }

        @Override
        public String read(JsonReader in) throws IOException {
            in.nextNull();
            return "read-null";
        }
    }

    @SuppressWarnings("DefaultAnnotationParam")
    record WithNullSafeTrue(
        @JsonAdapter(value = NullSafeAdapter.class, nullSafe = true)
        String s
    ) { }

    @Test
    void testNullSafe_True() throws IOException {
        TypeAdapter<WithNullSafeTrue> typeAdapter = getDefaultAdapter(WithNullSafeTrue.class);
        assertEquals("{\"s\":null}", typeAdapter.toJson(new WithNullSafeTrue(null)));
        assertEquals(new WithNullSafeTrue(null), typeAdapter.fromJson("{\"s\":null}"));
    }

    record WithNullSafeFalse(
        @JsonAdapter(value = NullSafeAdapter.class, nullSafe = false)
        String s
    ) { }

    @Test
    void testNullSafe_False() throws IOException {
        TypeAdapter<WithNullSafeFalse> typeAdapter = getDefaultAdapter(WithNullSafeFalse.class);
        assertEquals("{\"s\":\"write-null\"}", typeAdapter.toJson(new WithNullSafeFalse(null)));
        assertEquals(new WithNullSafeFalse("read-null"), typeAdapter.fromJson("{\"s\":null}"));
    }
}
