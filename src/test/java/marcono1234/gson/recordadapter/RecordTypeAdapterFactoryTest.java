package marcono1234.gson.recordadapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordTypeAdapterFactoryTest {
    static <T> TypeAdapter<T> getDefaultAdapter(TypeToken<T> t) {
        return new GsonBuilder()
            .registerTypeAdapterFactory(RecordTypeAdapterFactory.DEFAULT)
            .create()
            .getAdapter(t);
    }

    static <T> TypeAdapter<T> getDefaultAdapter(Class<T> c) {
        return getDefaultAdapter(TypeToken.get(c));
    }

    static <T> TypeAdapter<T> getAdapter(Class<T> c, RecordTypeAdapterFactory.Builder builder) {
        return new GsonBuilder()
            .registerTypeAdapterFactory(builder.create())
            .create()
            .getAdapter(c);
    }

    record N(int i) { }
    record R(int i, String s, boolean b, N n) { }

    @Test
    void testToJson() {
        TypeAdapter<R> typeAdapter = getDefaultAdapter(R.class);
        String json = typeAdapter.toJson(new R(1, "a", true, new N(2)));
        assertEquals("{\"i\":1,\"s\":\"a\",\"b\":true,\"n\":{\"i\":2}}", json);
    }

    record CustomAccessor(int i) {
        @SuppressWarnings("unused")
        @Override
        public int i() {
            return 1234;
        }
    }

    /** Should use accessor to get value */
    @Test
    void testToJson_CustomAccessor() {
        TypeAdapter<CustomAccessor> typeAdapter = getDefaultAdapter(CustomAccessor.class);
        String json = typeAdapter.toJson(new CustomAccessor(0));
        assertEquals("{\"i\":1234}", json);
    }

    @Test
    void testToJson_Null() {
        TypeAdapter<R> typeAdapter = getDefaultAdapter(R.class);
        assertEquals("null", typeAdapter.toJson(null));
    }

    record SelfCyclic(SelfCyclic c) {
        @SuppressWarnings("CopyConstructorMissesField")
        SelfCyclic {
            // Creates cycle
            c = this;
        }
    }

    @Test
    void testToJson_SelfCyclic() {
        TypeAdapter<SelfCyclic> typeAdapter = getDefaultAdapter(SelfCyclic.class);
        SelfCyclic c = new SelfCyclic(null);
        // Currently has no cycle detection, so just assert that any error is thrown
        // Implementing cycle detection is probably not worth it because it will most likely be rarely needed
        assertThrows(StackOverflowError.class, () -> typeAdapter.toJson(c));
    }

    record Cyclic(List<Cyclic> l) { }

    @Test
    void testToJson_Cyclic() {
        List<Cyclic> l = new ArrayList<>();
        Cyclic c = new Cyclic(l);
        l.add(c);

        TypeAdapter<Cyclic> typeAdapter = getDefaultAdapter(Cyclic.class);
        // Currently has no cycle detection, so just assert that any error is thrown
        // Implementing cycle detection is probably not worth it because it will most likely be rarely needed
        assertThrows(StackOverflowError.class, () -> typeAdapter.toJson(c));
    }

    @Test
    void testFromJson() throws IOException {
        TypeAdapter<R> typeAdapter = getDefaultAdapter(R.class);
        String json = "{\"i\":1,\"s\":\"a\",\"b\":true,\"n\":{\"i\":2}}";
        R actual = typeAdapter.fromJson(json);
        assertEquals(new R(1, "a", true, new N(2)), actual);
    }

    record CustomConstructor(String s) {
        CustomConstructor {
            s = "constructor:" + s;
        }
    }

    @Test
    void testFromJson_CustomConstructor() throws IOException {
        TypeAdapter<CustomConstructor> typeAdapter = getDefaultAdapter(CustomConstructor.class);
        CustomConstructor actual = typeAdapter.fromJson("{\"s\":\"a\"}");
        assertEquals("constructor:a", actual.s);
    }

    record ThrowingConstructor(int i) {
        ThrowingConstructor {
            throw new IllegalStateException("test");
        }
    }

    @Test
    void testFromJson_ThrowingConstructor() {
        TypeAdapter<ThrowingConstructor> typeAdapter = getDefaultAdapter(ThrowingConstructor.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"i\":1}"));
        assertEquals("Failed creating record instance for " + ThrowingConstructor.class, e.getMessage());
        assertEquals("test", e.getCause().getMessage());
    }

    record OtherConstructors(byte b) {
        @SuppressWarnings("unused")
        public OtherConstructors() {
            this((byte) 1);
            throw new AssertionError("Should not have been called");
        }

        @SuppressWarnings("unused")
        public OtherConstructors(Byte b) {
            this((byte) b);
            throw new AssertionError("Should not have been called");
        }

        @SuppressWarnings("unused")
        public OtherConstructors(int i) {
            this((byte) 1);
            throw new AssertionError("Should not have been called");
        }
    }

    /** Should only use canonical constructor; others should be ignored */
    @Test
    void testFromJson_OtherConstructors() throws IOException {
        TypeAdapter<OtherConstructors> typeAdapter = getDefaultAdapter(OtherConstructors.class);
        OtherConstructors actual = typeAdapter.fromJson("{\"b\":1}");
        assertEquals(new OtherConstructors((byte) 1), actual);
    }

    @Test
    void testFromJson_Null() throws IOException {
        TypeAdapter<R> typeAdapter = getDefaultAdapter(R.class);
        assertNull(typeAdapter.fromJson("null"));
    }

    @Test
    void testFromJson_MissingComponentValue() {
        TypeAdapter<N> typeAdapter = getDefaultAdapter(N.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{}"));
        assertEquals("Missing value for " + N.class.getName() + ".i; last property is at JSON path $.", e.getMessage());
    }

    @Test
    void testFromJson_MissingComponentValue_Allowed() throws IOException {
        TypeAdapter<R> typeAdapter = getAdapter(
            R.class,
            RecordTypeAdapterFactory.builder().allowMissingComponentValues()
        );
        R actual = typeAdapter.fromJson("{}");
        assertEquals(new R(0, null, false, null), actual);
    }

    record PrimitiveComponents(
        byte b,
        Byte B,
        short s,
        Short S,
        int i,
        Integer I,
        long l,
        Long L,
        float f,
        Float F,
        double d,
        Double D,
        boolean bool,
        Boolean Bool,
        char c,
        Character C,
        int[] array
    ) { }

    @Test
    void testFromJson_MissingComponentValue_Primitives() throws IOException {
        TypeAdapter<PrimitiveComponents> typeAdapter = getAdapter(
            PrimitiveComponents.class,
            RecordTypeAdapterFactory.builder().allowMissingComponentValues()
        );
        PrimitiveComponents expected = new PrimitiveComponents(
            (byte) 0, null,
            (short) 0, null,
            0, null,
            0L, null,
            0f, null,
            0d, null,
            false, null,
            '\0', null,
            null
        );
        PrimitiveComponents actual = typeAdapter.fromJson("{}");
        assertEquals(expected, actual);
    }

    @Test
    void testFromJson_UnknownProperty() {
        TypeAdapter<N> typeAdapter = getDefaultAdapter(N.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"i\":1,\"x\":2}"));
        assertEquals("Unknown property 'x' for " + N.class + " at JSON path $.x", e.getMessage());
    }

    @Test
    void testFromJson_UnknownProperty_Allowed() throws IOException {
        TypeAdapter<N> typeAdapter = getAdapter(
            N.class,
            RecordTypeAdapterFactory.builder().allowUnknownProperties()
        );
        N actual = typeAdapter.fromJson("{\"i\":1,\"x\":2}");
        assertEquals(new N(1), actual);
    }

    @Test
    void testFromJson_DuplicateProperty() {
        TypeAdapter<N> typeAdapter = getDefaultAdapter(N.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"i\":1,\"i\":1}"));
        assertEquals("Duplicate value for " + N.class.getName() + ".i provided by property 'i' at JSON path $.i", e.getMessage());
    }

    record ObjectComponent(Object o) { }

    @Test
    void testFromJson_DuplicatePropertyNull() {
        TypeAdapter<ObjectComponent> typeAdapter = getDefaultAdapter(ObjectComponent.class);
        // Should also detect duplicate property when value is null
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"o\":null,\"o\":null}"));
        assertEquals("Duplicate value for " + ObjectComponent.class.getName() + ".o provided by property 'o' at JSON path $.o", e.getMessage());
    }

    @Test
    void testFromJson_DuplicateUnknownPropertyIgnored() throws IOException {
        TypeAdapter<N> typeAdapter = getAdapter(
            N.class,
            RecordTypeAdapterFactory.builder().allowUnknownProperties()
        );
        // Duplicate unknown property 'x' should be ignored
        N actual = typeAdapter.fromJson("{\"i\":1,\"x\":2,\"x\":2}");
        assertEquals(new N(1), actual);
    }

    @Test
    void testFromJson_DuplicateProperty_Allowed() throws IOException {
        TypeAdapter<N> typeAdapter = getAdapter(
            N.class,
            RecordTypeAdapterFactory.builder().allowDuplicateComponentValues()
        );
        // Should use value of last occurrence
        N actual = typeAdapter.fromJson("{\"i\":1,\"i\":2}");
        assertEquals(new N(2), actual);
    }

    @Test
    void testFromJson_JsonNullPrimitive() {
        TypeAdapter<N> typeAdapter = getDefaultAdapter(N.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"i\":null}"));
        assertEquals("JSON null is not allowed for primitive " + N.class.getName() + ".i provided by property 'i' at JSON path $.i", e.getMessage());
    }

    @Test
    void testFromJson_JsonNullPrimitive_Allowed() throws IOException {
        TypeAdapter<N> typeAdapter = getAdapter(
            N.class,
            RecordTypeAdapterFactory.builder().allowJsonNullForPrimitiveComponents()
        );
        N actual = typeAdapter.fromJson("{\"i\":null}");
        assertEquals(new N(0), actual);
    }

    @Disabled("Not yet supported, see https://github.com/google/gson/pull/1969")
    @Test
    void testLocalRecord() throws IOException {
        record L(int i) { }

        TypeAdapter<L> typeAdapter = getDefaultAdapter(L.class);
        String json = typeAdapter.toJson(new L(1));
        assertEquals("{\"i\":1}", json);

        L actual = typeAdapter.fromJson("{\"i\":1}");
        assertEquals(new L(1), actual);
    }

    record NamingStrategy(
        int i,
        int myField,
        int _internal$fieldName
    ) { }

    @Test
    void testNamingStrategy() throws IOException {
        TypeAdapter<NamingStrategy> typeAdapter = getAdapter(
            NamingStrategy.class,
            RecordTypeAdapterFactory.builder().withComponentNamingStrategy(RecordComponentNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES)
        );
        String json = typeAdapter.toJson(new NamingStrategy(1, 2, 3));
        assertEquals("{\"I\":1,\"My Field\":2,\"_Internal$field Name\":3}", json);

        NamingStrategy actual = typeAdapter.fromJson("{\"I\":1,\"My Field\":2,\"_Internal$field Name\":3}");
        assertEquals(new NamingStrategy(1, 2, 3), actual);
    }

    static class Base {
        int i1;

        public Base(int i1) {
            this.i1 = i1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Base base = (Base) o;
            return i1 == base.i1;
        }
    }
    static class Sub extends Base {
        int i2;

        public Sub(int i1, int i2) {
            super(i1);
            this.i2 = i2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Sub sub = (Sub) o;
            return i1 == sub.i1 && i2 == sub.i2;
        }
    }
    static class CustomAdapter extends TypeAdapter<Base> {
        public CustomAdapter() { }

        @Override
        public void write(JsonWriter out, Base value) throws IOException {
            if (value == null) {
                out.value("custom-write-null");
            } else {
                out.value("custom-write");
            }
        }

        @Override
        public Base read(JsonReader in) throws IOException {
            in.skipValue();
            return new Base(-1);
        }
    }
    record RuntimeType(
        Base b,
        @JsonAdapter(value = CustomAdapter.class, nullSafe = false)
        Base annotated
    ) { }

    @Test
    void testNoRuntimeType() throws IOException {
        TypeAdapter<RuntimeType> typeAdapter = getDefaultAdapter(RuntimeType.class);
        Sub value = new Sub(1, 2);
        String json = typeAdapter.toJson(new RuntimeType(value, value));
        assertEquals("{\"b\":{\"i1\":1},\"annotated\":\"custom-write\"}", json);

        RuntimeType actual = typeAdapter.fromJson("{\"b\":{\"i1\":1},\"annotated\":2}");
        assertEquals(new Base(1), actual.b);
        assertEquals(new Base(-1), actual.annotated);
    }

    @Test
    void testRuntimeType() throws IOException {
        TypeAdapter<RuntimeType> typeAdapter = getAdapter(
            RuntimeType.class,
            RecordTypeAdapterFactory.builder().serializeRuntimeComponentTypes()
        );
        Sub value = new Sub(1, 2);
        String json = typeAdapter.toJson(new RuntimeType(value, value));
        // Runtime type is only used for non-annotated component
        assertEquals("{\"b\":{\"i2\":2,\"i1\":1},\"annotated\":\"custom-write\"}", json);

        RuntimeType actual = typeAdapter.fromJson("{\"b\":{\"i2\":2,\"i1\":1},\"annotated\":3}");
        // Uses compile-time type Base for deserialization
        assertEquals(new Base(1), actual.b);
        assertEquals(new Base(-1), actual.annotated);
    }

    @Test
    void testRuntimeTypeNull() {
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(Base.class, new TypeAdapter<Base>() {
                @Override
                public void write(JsonWriter out, Base value) throws IOException {
                    assertNull(value);
                    out.value("base-null");
                }

                @Override
                public Base read(JsonReader in) {
                    throw new AssertionError("Not needed for this test");
                }
            })
            .registerTypeAdapter(Sub.class, new TypeAdapter<Sub>() {
                @Override
                public void write(JsonWriter out, Sub value) throws IOException {
                    assertNotNull(value);
                    out.value("sub");
                }

                @Override
                public Sub read(JsonReader in) {
                    throw new AssertionError("Not needed for this test");
                }
            })
            .registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().serializeRuntimeComponentTypes().create())
            .create();

        TypeAdapter<RuntimeType> typeAdapter = gson.getAdapter(RuntimeType.class);

        // Verify that test is set up correctly; serialize non-null
        {
            Sub value = new Sub(1, 2);
            String json = typeAdapter.toJson(new RuntimeType(value, value));
            assertEquals("{\"b\":\"sub\",\"annotated\":\"custom-write\"}", json);
        }

        {
            String json = typeAdapter.toJson(new RuntimeType(null, null));
            // Should use compile-time type adapter / JsonAdapter type adapter
            assertEquals("{\"b\":\"base-null\",\"annotated\":\"custom-write-null\"}", json);
        }
    }

    /** Factory should not return adapter for base class {@code java.lang.Record}. */
    @Test
    void testRecordBaseClass() {
        var typeToken = TypeToken.get(Record.class);
        var adapter = RecordTypeAdapterFactory.DEFAULT.create(new Gson(), typeToken);
        assertNull(adapter);
    }

    @JsonAdapter(RecordTypeAdapterFactory.class)
    record WithJsonAdapterAnnotation(int i) { }

    /** Test usage of RecordTypeAdapterFactory as @JsonAdapter value */
    @Test
    void testJsonAdapterAnnotation() {
        Gson gson = new Gson();
        String json = gson.toJson(new WithJsonAdapterAnnotation(1));
        assertEquals("{\"i\":1}", json);

        WithJsonAdapterAnnotation actual = gson.fromJson("{\"i\":1}", WithJsonAdapterAnnotation.class);
        assertEquals(new WithJsonAdapterAnnotation(1), actual);
    }
}
