package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

class TextTest {
    @Test
    void fenceIsLongerThanAnyBacktickRunInText() {
        assertTrue(Text.fenced("plain").startsWith("```text\n"));
        assertTrue(Text.fenced("a ``` b").startsWith("````text\n"));
        assertTrue(Text.fenced("a ```` b").startsWith("`````text\n"));
    }

    @Test
    void sanitizeFileNameBlocksTraversalAndKeepsLetters() {
        assertEquals("etc_passwd", Text.sanitizeFileName("../../etc/passwd", "file"));
        assertEquals("file", Text.sanitizeFileName("..", "file"));
        assertEquals("résumé_final.pdf", Text.sanitizeFileName("résumé final.pdf", "file"));
        assertEquals("دليل.pdf", Text.sanitizeFileName("دليل.pdf", "file"));
        assertFalse(Text.sanitizeFileName("a/b\\c", "file").contains("/"));
    }

    @Test
    void uniqueFileNameIsCaseInsensitive() {
        var used = new HashSet<String>();
        assertEquals("a.txt", Text.uniqueFileName("a.txt", used));
        assertEquals("A-2.TXT", Text.uniqueFileName("A.TXT", used));
        assertEquals("a-3.txt", Text.uniqueFileName("a.txt", used));
    }

    @Test
    void yamlQuoteEscapesSpecials() {
        assertEquals("\"a\\\"b\\\\c\"", Text.yamlQuote("a\"b\\c"));
        assertEquals("\"line1\\nline2\"", Text.yamlQuote("line1\nline2"));
        assertEquals("\"\\u0001\"", Text.yamlQuote("\u0001"));
    }

    @Test
    void escapeMarkdownEscapesAndFlattens() {
        assertEquals("snake\\_case \\[x\\]", Text.escapeMarkdown("snake_case [x]"));
        assertEquals("a b", Text.escapeMarkdown("a\nb"));
    }

    @Test
    void cleanTextNormalisesWhitespace() {
        assertEquals("a\nb", Text.cleanText("a  \r\nb\u0000\n"));
        assertEquals("a\n\n\nb", Text.cleanText("a\n\n\n\n\n\nb"));
    }

    @Test
    void autolinkEncodesUnsafeCharacters() {
        assertEquals("<https://example.com/a%20b>", Text.autolink(" https://example.com/a b "));
    }

    @Test
    void numbersAreNeverLocalised() {
        assertEquals("page-0007.md", Text.pageFileName(7));
        assertEquals("0042", Text.padded(42, 4));
    }
}
