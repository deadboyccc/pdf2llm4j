package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MainTest {
    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int exit;
        try (var o = new PrintStream(out, true, StandardCharsets.UTF_8);
             var e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            exit = Main.execute(List.of(args), o, e, Map.of());
        }
        return new Result(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpPrintsUsage() {
        var result = run("--help");
        assertEquals(Main.EXIT_OK, result.exit());
        assertTrue(result.out().contains("Usage:"));
    }

    @Test
    void versionPrintsVersion() {
        var result = run("--version");
        assertEquals(Main.EXIT_OK, result.exit());
        assertTrue(result.out().startsWith("pdf2llm "));
    }

    @Test
    void usageErrorsExitWithTwo() {
        var result = run("--bogus");
        assertEquals(Main.EXIT_USAGE, result.exit());
        assertTrue(result.err().contains("Unknown option: --bogus"));
    }

    @Test
    void missingInputExitsWithOne() {
        var result = run("definitely-not-here.pdf");
        assertEquals(Main.EXIT_FAILURE, result.exit());
        assertTrue(result.err().contains("PDF not found"));
    }

    private record Result(int exit, String out, String err) {
    }
}
