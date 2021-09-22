package marcono1234.gson.recordadapter;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static marcono1234.gson.recordadapter.RecordTypeAdapterFactoryTest.getAdapter;
import static marcono1234.gson.recordadapter.RecordTypeAdapterFactoryTest.getDefaultAdapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for usage of {@link SerializedName @SerializedName} */
class SerializedNameTest {
    record CustomName(
        @SerializedName("test")
        int i
    ) { }

    @Test
    void testToJson() {
        TypeAdapter<CustomName> typeAdapter = getDefaultAdapter(CustomName.class);
        assertEquals("{\"test\":1}", typeAdapter.toJson(new CustomName(1)));
    }

    @Test
    void testFromJson() throws IOException {
        TypeAdapter<CustomName> typeAdapter = getDefaultAdapter(CustomName.class);
        assertEquals(new CustomName(1), typeAdapter.fromJson("{\"test\":1}"));
    }

    /**
     * Should not be able to use component name when @SerializedName is used
     */
    @Test
    void testFromJson_ComponentName() {
        TypeAdapter<CustomName> typeAdapter = getDefaultAdapter(CustomName.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"i\":1}"));
        assertEquals("Unknown property 'i' for " + CustomName.class + " at JSON path $.i", e.getMessage());
    }

    record WithAlternate(
        @SerializedName(value = "test", alternate = "other")
        int i
    ) { }

    @Test
    void testToJson_Alternate() {
        TypeAdapter<WithAlternate> typeAdapter = getDefaultAdapter(WithAlternate.class);
        assertEquals("{\"test\":1}", typeAdapter.toJson(new WithAlternate(1)));
    }

    @Test
    void testFromJson_Alternate() throws IOException {
        TypeAdapter<WithAlternate> typeAdapter = getDefaultAdapter(WithAlternate.class);
        // Uses SerializedName.value
        assertEquals(new WithAlternate(1), typeAdapter.fromJson("{\"test\":1}"));
    }

    @Test
    void testFromJson_AlternateAlternate() throws IOException {
        TypeAdapter<WithAlternate> typeAdapter = getDefaultAdapter(WithAlternate.class);
        // Uses SerializedName.alternate
        assertEquals(new WithAlternate(1), typeAdapter.fromJson("{\"other\":1}"));
    }

    @Test
    void testFromJson_DuplicateValue() {
        TypeAdapter<WithAlternate> typeAdapter = getDefaultAdapter(WithAlternate.class);
        Exception e = assertThrows(JsonParseException.class, () -> typeAdapter.fromJson("{\"test\":1,\"other\":1}"));
        assertEquals("Duplicate value for " + WithAlternate.class.getName() + ".i provided by property 'other' at JSON path $.other", e.getMessage());
    }

    @Test
    void testFromJson_DuplicateValue_Allowed() throws IOException {
        TypeAdapter<WithAlternate> typeAdapter = getAdapter(
            WithAlternate.class,
            RecordTypeAdapterFactory.builder().allowDuplicateComponentValues()
        );
        // Should use value of last occurrence
        WithAlternate actual = typeAdapter.fromJson("{\"test\":1,\"other\":2}");
        assertEquals(new WithAlternate(2), actual);
    }

    record SwitchedNames(
        @SerializedName("i2")
        int i1,
        @SerializedName("i1")
        int i2
    ) { }

    @Test
    void testSwitchedNames() throws IOException {
        TypeAdapter<SwitchedNames> typeAdapter = getDefaultAdapter(SwitchedNames.class);
        String json = typeAdapter.toJson(new SwitchedNames(1, 2));
        assertEquals("{\"i2\":1,\"i1\":2}", json);

        assertEquals(new SwitchedNames(1, 2), typeAdapter.fromJson("{\"i2\":1,\"i1\":2}"));
    }

    record SerializedNameComponentClash(
        @SerializedName("i2")
        int i1,
        int i2
    ) { }

    @Test
    void testSerializedNameComponentClash() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(SerializedNameComponentClash.class));
        assertEquals("Property name 'i2' for " + SerializedNameComponentClash.class.getName() + ".i2 clashes with name of other component", e.getMessage());
    }

    record DuplicateSerializedName(
        @SerializedName("a")
        int i1,
        @SerializedName("a")
        int i2
    ) { }

    @Test
    void testDuplicateSerializedName() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(DuplicateSerializedName.class));
        assertEquals("Property name 'a' for " + DuplicateSerializedName.class.getName() + ".i2 clashes with name of other component", e.getMessage());
    }

    record DuplicateValueAlternate(
        @SerializedName(value = "a", alternate = "a")
        int i
    ) { }

    @Test
    void testDuplicateValueAlternate() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(DuplicateValueAlternate.class));
        assertEquals("Duplicate property name 'a' for " + DuplicateValueAlternate.class.getName() + ".i", e.getMessage());
    }

    record DuplicateAlternates(
        @SerializedName(value = "a", alternate = {"b", "b"})
        int i
    ) { }

    @Test
    void testDuplicateAlternates() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(DuplicateAlternates.class));
        assertEquals("Duplicate property name 'b' for " + DuplicateAlternates.class.getName() + ".i", e.getMessage());
    }

    record AlternateComponentClash(
        @SerializedName(value = "test", alternate = "i2")
        int i1,
        int i2
    ) { }

    @Test
    void testAlternateComponentClash() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(AlternateComponentClash.class));
        assertEquals("Property name 'i2' for " + AlternateComponentClash.class.getName() + ".i2 clashes with name of other component", e.getMessage());
    }

    record DuplicateAlternatesMultipleComponents(
        @SerializedName(value = "a1", alternate = "b")
        int i1,
        @SerializedName(value = "a2", alternate = "b")
        int i2
    ) { }

    @Test
    void testDuplicateAlternatesMultipleComponents() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(DuplicateAlternatesMultipleComponents.class));
        assertEquals("Property name 'b' for " + DuplicateAlternatesMultipleComponents.class.getName() + ".i2 clashes with name of other component", e.getMessage());
    }

    record OverriddenAccessor(
        @SerializedName("test")
        int i
    ) {
        @SuppressWarnings("unused")
        @Override
        public int i() {
            return i;
        }
    }

    /**
     * Even when accessor method is overridden (and @SerializedName annotation not propagated) should
     * still be able to get @SerializedName annotation
     */
    @Test
    void testOverriddenAccessor() {
        TypeAdapter<OverriddenAccessor> typeAdapter = getDefaultAdapter(OverriddenAccessor.class);
        assertEquals("{\"test\":1}", typeAdapter.toJson(new OverriddenAccessor(1)));
    }

    record OverriddenAccessorAnnotated(
        int i
    ) {
        @SuppressWarnings("unused")
        @SerializedName("test")
        @Override
        public int i() {
            return i;
        }
    }

    @Test
    void testOverriddenAccessorAnnotated() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(OverriddenAccessorAnnotated.class));
        assertEquals("@SerializedName on accessor method is not supported; place it on the corresponding record component instead", e.getMessage());
    }

    record OverriddenAccessorAnnotationMismatch(
        @SerializedName("a")
        int i
    ) {
        @SuppressWarnings("unused")
        @SerializedName("b")
        @Override
        public int i() {
            return i;
        }
    }

    @Test
    void testOverriddenAccessorAnnotationMismatch() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> getDefaultAdapter(OverriddenAccessorAnnotationMismatch.class));
        assertEquals("Using different @SerializedName on accessor than on corresponding record component is not supported", e.getMessage());
    }

    record NotAllComponentsWithSerializedName(
        @SerializedName("test")
        int i,
        int other
    ) { }

    @Test
    void testWithNamingStrategy() throws IOException {
        TypeAdapter<NotAllComponentsWithSerializedName> typeAdapter = getAdapter(
            NotAllComponentsWithSerializedName.class,
            RecordTypeAdapterFactory.builder().withComponentNamingStrategy(RecordComponentNamingStrategy.UPPER_CAMEL_CASE)
        );
        // Only component without @SerializedName is affected by naming strategy
        assertEquals("{\"test\":1,\"Other\":2}", typeAdapter.toJson(new NotAllComponentsWithSerializedName(1, 2)));
        assertEquals(new NotAllComponentsWithSerializedName(1, 2), typeAdapter.fromJson("{\"test\":1,\"Other\":2}"));
    }
}
