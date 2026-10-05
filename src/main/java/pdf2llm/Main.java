package pdf2llm;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import pdf2llm.Cli.ParsedArgs;
import pdf2llm.Cli.UsageException;

/**
 * Command-line entry point.
 */
public final class Main {
    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_USAGE = 2;

    // java.util.logging only keeps weak references to loggers, so hold on to ours to keep their levels.
    private static List<Logger> quietLoggers = List.of();

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.exit(execute(List.of(args), System.out, System.err, System.getenv()));
    }

    static int execute(List<String> args, PrintStream out, PrintStream err, Map<String, String> env) {
        ParsedArgs command;
        try {
            command = Cli.parse(args, env);
        } catch (UsageException e) {
            err.println("Error: " + e.getMessage());
            err.println();
            err.println(Cli.USAGE);
            return EXIT_USAGE;
        }

        return switch (command) {
            case ParsedArgs.Help _ -> {
                out.println(Cli.USAGE);
                yield EXIT_OK;
            }
            case ParsedArgs.Version _ -> {
                out.println("pdf2llm " + BuildInfo.VERSION + " (Apache PDFBox " + BuildInfo.PDFBOX_VERSION + ")");
                yield EXIT_OK;
            }
            case ParsedArgs.Run run -> runExtraction(run.options(), out, err);
        };
    }

    private static int runExtraction(Options options, PrintStream out, PrintStream err) {
        if (!options.verbose()) silencePdfBoxWarnings();
        try {
            var extractor = new PdfExtractor(options, (done, total) -> {
                err.print("\rProcessing page " + done + "/" + total + "...");
                if (done == total) err.println();
            });
            printSummary(options, extractor.run(), out, err);
            return EXIT_OK;
        } catch (ExtractionException e) {
            err.println();
            err.println("Error: " + e.getMessage());
            if (options.verbose() && e.getCause() != null) e.getCause().printStackTrace(err);
            return EXIT_FAILURE;
        } catch (OutOfMemoryError _) {
            err.println();
            err.println("Error: out of memory. Raise the heap, e.g. "
                    + "PDF2LLM_OPTS=\"-Xmx4g\" (start script) or java -Xmx4g -jar ...");
            return EXIT_FAILURE;
        } catch (Exception e) {
            err.println();
            err.println("Error: " + Describe.of(e));
            if (options.verbose()) e.printStackTrace(err);
            return EXIT_FAILURE;
        }
    }

    private static void silencePdfBoxWarnings() {
        quietLoggers = List.of("org.apache.pdfbox", "org.apache.fontbox").stream()
                .map(name -> {
                    var logger = Logger.getLogger(name);
                    logger.setLevel(Level.SEVERE);
                    return logger;
                })
                .toList();
    }

    private static void printSummary(Options options, Manifest manifest, PrintStream out, PrintStream err) {
        out.println("Done.");
        out.println("Output: " + options.outputDir());
        out.println("  document.md      main entry point (metadata, bookmarks, page index)");
        out.println("  pages/           " + manifest.pages().size() + " Markdown file(s), one per page");
        out.println("  all-text.txt     plain text with page separators");
        out.println("  annotations.md   " + manifest.annotationCount() + " link(s)/annotation(s)");
        out.println("  manifest.json    machine-readable index");
        if (!manifest.images().isEmpty()) {
            out.println("  images/          " + manifest.images().size() + " extracted image(s)");
        }
        if (options.renderPages()) {
            out.println("  rendered-pages/  page renderings at " + (int) options.dpi() + " DPI");
        }
        if (!manifest.attachments().isEmpty()) {
            out.println("  attachments/     " + manifest.attachments().size() + " embedded file(s)");
        }

        var warnings = manifest.warnings();
        if (!warnings.isEmpty()) {
            err.println();
            err.println(warnings.size() + " warning(s):");
            warnings.stream().limit(20).forEach(w -> err.println("  - " + w));
            if (warnings.size() > 20) err.println("  ... and " + (warnings.size() - 20) + " more (see manifest.json)");
        }
    }
}
