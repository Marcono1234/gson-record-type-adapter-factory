package test.open;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Open_RecordTypeAdapterFactoryTest {
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapterFactory(RecordTypeAdapterFactory.DEFAULT)
        .create();

    private record PrivateRecord(int i) { }

    /** Should make constructor and accessor methods of private record accessible */
    @Test
    void testPrivateRecord() throws IOException {
        TypeAdapter<PrivateRecord> typeAdapter = gson.getAdapter(PrivateRecord.class);
        String json = typeAdapter.toJson(new PrivateRecord(1));
        assertEquals("{\"i\":1}", json);

        PrivateRecord actual = typeAdapter.fromJson("{\"i\":1}");
        assertEquals(new PrivateRecord(1), actual);
    }
}
