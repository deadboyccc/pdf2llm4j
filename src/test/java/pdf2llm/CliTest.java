package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import pdf2llm.Cli.ParsedArgs;
import pdf2llm.Cli.UsageException;

class CliTest {
    private static Options options(Map<String, String> env, String... args) {
        return assertInstanceOf(ParsedArgs.Run.class, Cli.parse(List.of(args), env)).options();
    }

    private static Options options(String... args) {
        return options(Map.of(), args);
    }

    @Test
    void defaults() {
        var o = options("book.pdf");
        assertEquals("book-llm", o.outputDir().getFileName().toString());
        assertEquals(o.input().getParent(), o.outputDir().getParent());
        assertFalse(o.renderPages());
        assertTrue(o.extractImages());
        assertTrue(o.sortByPosition());
        assertFalse(o.force());
        assertSame(PageSelection.ALL, o.pages());
        assertEquals(Options.DEFAULT_DPI, o.dpi());
    }

    @Test
    void flagsAndValues() {
        var o = options("book.pdf", "out", "--render-pages", "--dpi=150", "--no-images", "--pages", "2-4", "--force");
        assertEquals("out", o.outputDir().getFileName().toString());
        assertTrue(o.renderPages());
        assertFalse(o.extractImages());
        assertTrue(o.force());
        assertEquals(150f, o.dpi());
        assertEquals(List.of(2, 3, 4), o.pages().resolve(10));
    }

    @Test
    void passwordFromEnvironmentOrFlag() {
        assertEquals("secret", options(Map.of(Options.PASSWORD_ENV, "secret"), "book.pdf").password());
        assertEquals("cli", options(Map.of(Options.PASSWORD_ENV, "env"), "book.pdf", "--password", "cli").password());
    }

    @Test
    void doubleDashEndsOptions() {
        var o = options("--", "-weird.pdf");
        assertEquals("-weird.pdf", o.input().getFileName().toString());
    }

    @Test
    void helpAndVersion() {
        assertInstanceOf(ParsedArgs.Help.class, Cli.parse(List.of("--help"), Map.of()));
        assertInstanceOf(ParsedArgs.Help.class, Cli.parse(List.of("-h"), Map.of()));
        assertInstanceOf(ParsedArgs.Version.class, Cli.parse(List.of("--version"), Map.of()));
    }

    @Test
    void usageErrors() {
        for (var args : List.of(
                List.<String>of(),
                List.of("a.pdf", "b", "c"),
                List.of("a.pdf", "--bogus"),
                List.of("a.pdf", "-x"),
                List.of("a.pdf", "--dpi", "5"),
                List.of("a.pdf", "--dpi", "NaN"),
                List.of("a.pdf", "--dpi"),
                List.of("a.pdf", "--pages", "0"),
                List.of("a.pdf", "--force=yes"))) {
            assertThrows(UsageException.class, () -> Cli.parse(args, Map.of()), args.toString());
        }
    }

    @Test
    void withersReturnModifiedCopies() {
        var base = Options.of(java.nio.file.Path.of("a.pdf"), java.nio.file.Path.of("out"));
        var changed = base.withRenderPages(true).withDpi(200f).withForce(true);
        assertTrue(changed.renderPages());
        assertEquals(200f, changed.dpi());
        assertTrue(changed.force());
        assertFalse(base.renderPages());
    }
}
