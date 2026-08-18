package io.github.oliviercailloux.umlg.extraction.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaTest {

    private static final String Resource = "/umlg-schema.json";

    private static JsonNode schema() throws IOException {
        try (InputStream stream = SchemaTest.class.getResourceAsStream(Resource)) {
            assertNotNull(stream, "Schema not found on the classpath: " + Resource);
            return JsonMapper.builder().build()
                .readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Set<String> properties(JsonNode object) {
        Set<String> names = new LinkedHashSet<>();
        object.required("properties").properties().forEach(field -> names.add(field.getKey()));
        return names;
    }

    private static String refusal(String json) {
        return assertThrows(IllegalArgumentException.class, () -> Rules.read(json, "umlg.json"))
            .getMessage();
    }

    private static Set<String> listAfter(String message, String marker, String end) {
        int start = message.indexOf(marker);
        assertTrue(start >= 0, "Marker \"" + marker + "\" missing from: " + message);
        start += marker.length();
        int stop = message.indexOf(end, start);
        assertTrue(stop >= 0, "End « " + end + " » missing from: " + message);

        Set<String> words = new LinkedHashSet<>();
        for (String word : message.substring(start, stop).split(",")) {
            words.add(word.strip());
        }
        return words;
    }

    private static Set<String> quotesBeforeGot(String message) {
        int end = message.indexOf("got \"");
        assertTrue(end >= 0, "No \u00ab got \u00bb in: " + message);

        return quotes(message.substring(0, end));
    }

    private static Set<String> quotes(String start) {
        List<String> words = new ArrayList<>();
        int quote = start.indexOf('"');
        while (quote >= 0) {
            int closed = start.indexOf('"', quote + 1);
            if (closed < 0) {
                break;
            }
            words.add(start.substring(quote + 1, closed));
            quote = start.indexOf('"', closed + 1);
        }
        return new LinkedHashSet<>(words);
    }

    private static Set<String> values(JsonNode node) {
        Set<String> read = new LinkedHashSet<>();
        if (node.has("const")) {
            read.add(node.get("const").textValue());
            return read;
        }
        node.required("enum").forEach(value -> read.add(value.textValue()));
        return read;
    }

    @Test
    void theSchemaKeys_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = properties(schema());
        assertTrue(declared.remove("$schema"),
            "The schema must accept \"$schema\", which the reader ignores: " + declared);

        Set<String> known = listAfter(refusal("{ \"unknown\": 1 }"), "Known rules: ", ".");

        assertEquals(known, declared);
    }

    @Test
    void theKeysOfAView_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = properties(schema().required("$defs").required("view"));
        Set<String> known = listAfter(
            refusal("{ \"views\": [ { \"name\": \"V\", \"retain\": [\"A\"], \"unknown\": 1 } ] }"),
            "Known keys: ", ".");

        assertEquals(known, declared);
    }

    @Test
    void theStrategies_areThoseTheReaderKnows() throws IOException {
        Set<String> declared =
            values(schema().required("properties").required("strategy"));
        Set<String> known =
            listAfter(refusal("{ \"strategy\": \"publik\" }"), "Known strategies: ", " —");

        assertEquals(known, declared);
    }

    @Test
    void theOmissions_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = values(
            schema().required("properties").required("omit").required("items"));
        Set<String> known = quotesBeforeGot(refusal("{ \"omit\": [\"visibilities\"] }"));

        assertEquals(known, declared);
    }

    @Test
    void theDirections_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = values(schema().required("$defs").required("direction"));
        Set<String> known = quotesBeforeGot(refusal("{ \"direction\": \"bottom-to-top\" }"));

        assertEquals(known, declared);
    }

    @Test
    void theOrders_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = values(schema().required("properties").required("order"));
        Set<String> known = quotesBeforeGot(refusal("{ \"order\": \"declare\" }"));

        assertEquals(known, declared);
    }

    @Test
    void theMultiplicity_isTheOneTheReaderKnows() throws IOException {
        Set<String> declared = values(schema().required("properties").required("multiplicity"));
        Set<String> known = quotesBeforeGot(refusal("{ \"multiplicity\": \"crochets\" }"));

        assertEquals(known, declared);
    }

    @Test
    void theObjectSelectors_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode form : schema().required("$defs").required("selector").required("oneOf")) {
            if (form.has("properties")) {
                declared.addAll(properties(form));
            }
        }

        String message =
            refusal("{ \"views\": [ { \"name\": \"V\", \"retain\": [{ \"pres\": \"A\" }] } ] }");
        int enumeration = message.indexOf("A string names a class");
        assertTrue(enumeration >= 0, "The enumeration of kinds has changed: " + message);

        assertEquals(quotes(message.substring(enumeration)), declared);
    }

    @Test
    void theSchema_announcesItsMetaSchema() throws IOException {
        assertEquals("https://json-schema.org/draft/2020-12/schema",
            schema().required("$schema").textValue());
    }
}
