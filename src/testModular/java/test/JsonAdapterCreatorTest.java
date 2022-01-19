package test;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import marcono1234.gson.recordadapter.JsonAdapterCreator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InaccessibleObjectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class JsonAdapterCreatorTest {
    static class Adapter extends TypeAdapter<String> {
        public Adapter() { }

        @Override
        public void write(JsonWriter out, String value) {
            fail("Not needed for this test");
        }

        @Override
        public String read(JsonReader in) {
            return fail("Not needed for this test");
        }
    }

    @Test
    void testInaccessibleConstructor() {
        // Fails because module does not open package to library
        Exception e = assertThrows(JsonAdapterCreator.AdapterCreationException.class, () -> JsonAdapterCreator.DEFAULT_CONSTRUCTOR_INVOKER.create(Adapter.class));
        assertEquals("Default constructor of " + Adapter.class + " is not accessible; open it to this library or register a custom JsonAdapterCreator", e.getMessage());
        assertTrue(e.getCause() instanceof InaccessibleObjectException);
    }
}
