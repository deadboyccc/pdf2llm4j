package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void writesRecordsInDeclarationOrder() {
        var value = new Outer("a \"quoted\"\nline", true, 120.0f, List.of("x", "y"), List.of(new Inner("n", null)));
        var expected = """
                {
                  "title": "a \\"quoted\\"\\nline",
                  "flag": true,
                  "ratio": 120.0,
                  "tags": ["x", "y"],
                  "inner": [
                    {
                      "name": "n",
                      "count": null
                    }
                  ]
                }
                """;
        assertEquals(expected, Json.toJson(value));
    }

    @Test
    void writesEmptyCollections() {
        var map = new LinkedHashMap<String, Object>();
        map.put("list", List.of());
        map.put("map", new LinkedHashMap<>());
        assertEquals("{\n  \"list\": [],\n  \"map\": {}\n}\n", Json.toJson(map));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"\\u0001\\t\"\n", Json.toJson("\u0001\t"));
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThrows(IllegalArgumentException.class, () -> Json.toJson(new Object()));
    }

    record Inner(String name, Integer count) {
    }

    record Outer(String title, boolean flag, Float ratio, List<String> tags, List<Inner> inner) {
    }
}
