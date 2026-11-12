package id.ac.restoflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private Json() {}
    public static ObjectNode object() { return MAPPER.createObjectNode(); }
    public static JsonNode read(String value) {
        try { return MAPPER.readTree(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Respons server bukan JSON yang valid."); }
    }
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Data tidak dapat dikirim."); }
    }
}
