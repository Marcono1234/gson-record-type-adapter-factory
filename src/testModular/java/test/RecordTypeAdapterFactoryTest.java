package test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import marcono1234.gson.recordadapter.RecordTypeAdapterException;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InaccessibleObjectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecordTypeAdapterFactoryTest {
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapterFactory(RecordTypeAdapterFactory.DEFAULT)
        .create();

    public record Accessible(int i) { }

    @Test
    void testAccessible() {
        String json = gson.toJson(new Accessible(1));
        assertEquals("{\"i\":1}", json);

        Accessible actual = gson.fromJson("{\"i\":1}", Accessible.class);
        assertEquals(new Accessible(1), actual);
    }

    private record Inaccessible(int i) { }

    @Test
    void testInaccessibleConstructor() {
        Exception e = assertThrows(RecordTypeAdapterException.class, () -> gson.getAdapter(Inaccessible.class));
        assertEquals("Cannot access canonical constructor of class test.RecordTypeAdapterFactoryTest$Inaccessible; either change the visibility of the record class to `public` or open it to this library", e.getMessage());
        assertTrue(e.getCause() instanceof InaccessibleObjectException);
    }
}
