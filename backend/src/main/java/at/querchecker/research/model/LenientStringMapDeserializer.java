package at.querchecker.research.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Toleranter Deserializer für Map<String, String>: coerciert LLM-Abweichungen vom Schema.
 * - Zahlen  → String.valueOf (z.B. 16 → "16")
 * - Objekte → Werte mit Leerzeichen zusammengefügt (z.B. {"size":14,"res":"1920x1080"} → "14 1920x1080")
 * - Arrays  → Elemente mit ", " zusammengefügt
 * - null / fehlend → Feld wird weggelassen
 */
public class LenientStringMapDeserializer extends StdDeserializer<Map<String, String>> {

    public LenientStringMapDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (!node.isObject()) return new LinkedHashMap<>();

        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String value = nodeToString(entry.getValue());
            if (value != null) result.put(entry.getKey(), value);
        });
        return result;
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isTextual())  return node.asText();
        if (node.isNumber())   return node.asText();
        if (node.isBoolean())  return node.asText();
        if (node.isObject()) {
            return StreamSupport.stream(
                    ((Iterable<Map.Entry<String, JsonNode>>) () -> node.fields()).spliterator(), false)
                .map(e -> nodeToString(e.getValue()))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false)
                .map(this::nodeToString)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        }
        return node.asText();
    }
}
