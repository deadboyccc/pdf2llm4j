package pdf2llm;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Command-line parsing.
 */
final class Cli {
    static final String USAGE = """
            pdf2llm - convert a PDF into a page-aware, LLM-friendly corpus
            
            Usage:
              pdf2llm INPUT.pdf [OUTPUT_DIR] [options]
            
            Arguments:
              INPUT.pdf          The PDF to convert.
              OUTPUT_DIR         Where to write the corpus (default: <INPUT>-llm next to the PDF).
            
            Options:
              --render-pages     Render every page to PNG (diagrams, tables, equations, scans).
              --dpi <n>          Resolution for --render-pages, 36-1200 (default: 120).
              --no-images        Skip extraction of embedded image objects.
              --pages <spec>     Only process these pages, e.g. 1-10,15,20- (default: all).
              --password <pw>    Password for encrypted PDFs (or set PDF2LLM_PASSWORD).
              --no-sort          Keep text in content-stream order instead of sorting by position.
              --force            Overwrite files from a previous run in a non-empty OUTPUT_DIR.
              --verbose          Show PDFBox warnings and stack traces.
              -h, --help         Show this help.
              --version          Show version information.
            
            Examples:
              pdf2llm book.pdf
              pdf2llm book.pdf ./book-llm --render-pages --dpi 150
              pdf2llm book.pdf ./excerpt --pages 100-140 --force
            """;

    private Cli() {
    }

    static ParsedArgs parse(List<String> args, Map<String, String> env) {
        var positional = new ArrayList<String>();
        boolean renderPages = false;
        boolean extractImages = true;
        boolean sortByPosition = true;
        boolean force = false;
        boolean verbose = false;
        float dpi = Options.DEFAULT_DPI;
        var pages = PageSelection.ALL;
        String password = null;

        Deque<String> queue = new ArrayDeque<>(args);
        while (!queue.isEmpty()) {
            var arg = queue.removeFirst();
            if (arg.equals("--")) {
                positional.addAll(queue);
                break;
            }
            if (arg.equals("-h")) return new ParsedArgs.Help();
            if (!arg.startsWith("--")) {
                if (arg.startsWith("-") && arg.length() > 1) throw new UsageException("Unknown option: " + arg);
                positional.add(arg);
                continue;
            }

            int eq = arg.indexOf('=');
            var name = eq < 0 ? arg : arg.substring(0, eq);
            var inline = eq < 0 ? null : arg.substring(eq + 1);

            switch (name) {
                case "--help" -> {
                    return new ParsedArgs.Help();
                }
                case "--version" -> {
                    return new ParsedArgs.Version();
                }
                case "--render-pages" -> {
                    requireNoValue(name, inline);
                    renderPages = true;
                }
                case "--no-images" -> {
                    requireNoValue(name, inline);
                    extractImages = false;
                }
                case "--no-sort" -> {
                    requireNoValue(name, inline);
                    sortByPosition = false;
                }
                case "--force" -> {
                    requireNoValue(name, inline);
                    force = true;
                }
                case "--verbose" -> {
                    requireNoValue(name, inline);
                    verbose = true;
                }
                case "--dpi" -> dpi = parseDpi(valueOf(name, inline, queue));
                case "--pages" -> pages = parsePages(valueOf(name, inline, queue));
                case "--password" -> password = valueOf(name, inline, queue);
                default -> throw new UsageException("Unknown option: " + name);
            }
        }

        if (positional.isEmpty()) throw new UsageException("Missing input PDF.");
        if (positional.size() > 2) {
            throw new UsageException("Too many positional arguments: " + String.join(" ", positional.subList(2, positional.size())));
        }

        var input = Path.of(positional.get(0)).toAbsolutePath().normalize();
        var output = positional.size() > 1
                ? Path.of(positional.get(1)).toAbsolutePath().normalize()
                : defaultOutputDir(input);

        return new ParsedArgs.Run(new Options(
                input,
                output,
                renderPages,
                extractImages,
                dpi,
                pages,
                password != null ? password : env.get(Options.PASSWORD_ENV),
                sortByPosition,
                force,
                verbose));
    }

    /**
     * {@code <name>-llm} next to the input PDF.
     */
    private static Path defaultOutputDir(Path input) {
        var fileName = input.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        var stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return input.resolveSibling(stem + "-llm");
    }

    private static void requireNoValue(String name, String inline) {
        if (inline != null) throw new UsageException("Option " + name + " does not take a value.");
    }

    private static String valueOf(String name, String inline, Deque<String> queue) {
        if (inline != null) return inline;
        var next = queue.pollFirst();
        if (next == null) throw new UsageException("Option " + name + " requires a value.");
        return next;
    }

    private static float parseDpi(String text) {
        try {
            float dpi = Float.parseFloat(text.strip());
            if (dpi >= 36f && dpi <= 1200f) return dpi;
        } catch (NumberFormatException _) {
            // reported below
        }
        throw new UsageException("--dpi expects a number between 36 and 1200.");
    }

    private static PageSelection parsePages(String text) {
        try {
            return PageSelection.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UsageException("--pages: " + e.getMessage());
        }
    }

    /**
     * The outcome of parsing: run an extraction, or print help / version.
     */
    sealed interface ParsedArgs {
        record Run(Options options) implements ParsedArgs {
        }

        record Help() implements ParsedArgs {
        }

        record Version() implements ParsedArgs {
        }
    }

    /**
     * Invalid command line; the message is shown to the user together with the usage text.
     */
    static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
