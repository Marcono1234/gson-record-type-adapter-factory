package test.open;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import marcono1234.gson.recordadapter.JsonAdapterCreator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class Open_JsonAdapterCreatorTest {
    private static class PrivateAdapter extends TypeAdapter<String> {
        // This field is used to verify that Unsafe was not used to create instance without calling constructor
        @SuppressWarnings("UnusedAssignment")
        boolean wasConstructorCalled = false;

        public PrivateAdapter() {
            wasConstructorCalled = true;
        }

        @Override
        public void write(JsonWriter out, String value) {
            fail("Not needed for this test");
        }

        @Override
        public String read(JsonReader in) {
            return fail("Not needed for this test");
        }
    }

    /** Should make constructor of private adapter accessible */
    @Test
    void testPrivateAdapter() throws Exception {
        var instance = JsonAdapterCreator.DEFAULT_CONSTRUCTOR_INVOKER.create(PrivateAdapter.class);
        assertTrue(instance.isPresent());
        assertTrue(((PrivateAdapter) instance.get()).wasConstructorCalled);
    }
}
