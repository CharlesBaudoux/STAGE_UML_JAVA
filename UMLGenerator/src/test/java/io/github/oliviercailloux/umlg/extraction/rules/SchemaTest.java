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

// Does the JSON schema delivered with the library still say what the reader accepts?
//
// WHY THIS TEST EXISTS
//
// The schema is a second description of the format, written in a language other than the reader.
// Two descriptions of the same thing diverge: a twelfth key is added to RulesReader, and
// the schema silently refuses it — in the user's editor, under a red line that
// points at a fault that does not exist. That is the worst divergence, the one blaming the right file.
//
// D'OÙ LE TEST TIRE LA VÉRITÉ
//
// From refusal messages, and not from a list written here. "Known rules: strategy, types…" is already
// the list of keys, kept by the reader itself: re-reading it is comparing the schema to the code
// and not to a third copy that would have to be kept up to date in turn. The day a key
// is added, the message changes all by itself, and it is the schema that must follow.
//
// What this test does not do: validate a file against the schema. That would need a validator,
// hence one more dependency to check a resource the library never executes.
// Vocabulary divergence is the real risk; the conformance of the schema to the meta-schema is
// the business of the editor loading it.
class SchemaTest {

    private static final String Resource = "/umlg-schema.json";

    private static JsonNode schema() throws IOException {
        try (InputStream stream = SchemaTest.class.getResourceAsStream(Resource)) {
            assertNotNull(stream, "Schema not found on the classpath: " + Resource);
            return JsonMapper.builder().build()
                .readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // The keys the schema declares at a given place, in the order it writes them.
    private static Set<String> properties(JsonNode object) {
        Set<String> names = new LinkedHashSet<>();
        object.required("properties").properties().forEach(field -> names.add(field.getKey()));
        return names;
    }

    // The message a refusal returns, without which there is nothing to compare.
    private static String refusal(String json) {
        return assertThrows(IllegalArgumentException.class, () -> Rules.read(json, "umlg.json"))
            .getMessage();
    }

    // "Known rules: strategy, types, hide. A key…" read between its marker and its end.
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

    // The quoted words of a message, up to its "got \"…\"" part — which quotes what
    // the file wrote, and not what the format accepts.
    private static Set<String> quotesBeforeGot(String message) {
        int end = message.indexOf("got \"");
        assertTrue(end >= 0, "No \u00ab got \u00bb in: " + message);

        return quotes(message.substring(0, end));
    }

    // The quoted words of a message fragment.
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

    // The values of a schema enum, or of its const — a single value being written that way.
    private static Set<String> values(JsonNode node) {
        Set<String> read = new LinkedHashSet<>();
        if (node.has("const")) {
            read.add(node.get("const").textValue());
            return read;
        }
        node.required("enum").forEach(value -> read.add(value.textValue()));
        return read;
    }

    // --- The keys ---

    @Test
    void theSchemaKeys_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = properties(schema());
        // The reference to the schema is not a rule: the reader therefore does not quote it among them,
        // and the schema must nevertheless accept it. That is the only expected difference between the two
        // lists, and writing it here is what keeps another from slipping through.
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

    // --- The words each key accepts ---

    @Test
    void theStrategies_areThoseTheReaderKnows() throws IOException {
        Set<String> declared =
            values(schema().required("properties").required("strategy"));
        // "project" is the reference @UMLClass writes to this key: the reader refuses it here,
        // and the schema must therefore not offer it.
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

    // --- The kinds of selector ---

    @Test
    void theObjectSelectors_areThoseTheReaderKnows() throws IOException {
        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode form : schema().required("$defs").required("selector").required("oneOf")) {
            // The string names a class and carries no key; the other two kinds are
            // objects of a single key, which names it.
            if (form.has("properties")) {
                declared.addAll(properties(form));
            }
        }

        // The refusal of an unknown selector first quotes the one written, then the two kinds
        // that exist; the sentence enumerating them starts with the third, the one having no
        // key of the whole — a string names a class.
        String message =
            refusal("{ \"views\": [ { \"name\": \"V\", \"retain\": [{ \"pres\": \"A\" }] } ] }");
        int enumeration = message.indexOf("A string names a class");
        assertTrue(enumeration >= 0, "The enumeration of kinds has changed: " + message);

        assertEquals(quotes(message.substring(enumeration)), declared);
    }

    // --- What the schema says of itself ---

    @Test
    void theSchema_announcesItsMetaSchema() throws IOException {
        // Without this line, an editor does not know which dialect to read, and the 2020-12 keywords
        // the file uses — unevaluatedProperties, $defs — no longer mean anything.
        assertEquals("https://json-schema.org/draft/2020-12/schema",
            schema().required("$schema").textValue());
    }
}
