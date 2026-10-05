package pdf2llm;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Everything the extractor needs to know, resolved from the command line.
 */
record Options(
        Path input,
        Path outputDir,
        boolean renderPages,
        boolean extractImages,
        float dpi,
        PageSelection pages,
        String password,
        boolean sortByPosition,
        boolean force,
        boolean verbose) {

    static final float DEFAULT_DPI = 120f;
    static final String PASSWORD_ENV = "PDF2LLM_PASSWORD";

    Options {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(pages, "pages");
    }

    /**
     * Defaults: images on, no renderings, all pages, sorted text.
     */
    static Options of(Path input, Path outputDir) {
        return new Options(input, outputDir, false, true, DEFAULT_DPI, PageSelection.ALL, null, true, false, false);
    }

    Options withRenderPages(boolean value) {
        return new Options(input, outputDir, value, extractImages, dpi, pages, password, sortByPosition, force, verbose);
    }

    Options withExtractImages(boolean value) {
        return new Options(input, outputDir, renderPages, value, dpi, pages, password, sortByPosition, force, verbose);
    }

    Options withDpi(float value) {
        return new Options(input, outputDir, renderPages, extractImages, value, pages, password, sortByPosition, force, verbose);
    }

    Options withPages(PageSelection value) {
        return new Options(input, outputDir, renderPages, extractImages, dpi, value, password, sortByPosition, force, verbose);
    }

    Options withPassword(String value) {
        return new Options(input, outputDir, renderPages, extractImages, dpi, pages, value, sortByPosition, force, verbose);
    }

    Options withSortByPosition(boolean value) {
        return new Options(input, outputDir, renderPages, extractImages, dpi, pages, password, value, force, verbose);
    }

    Options withForce(boolean value) {
        return new Options(input, outputDir, renderPages, extractImages, dpi, pages, password, sortByPosition, value, verbose);
    }

    Options withVerbose(boolean value) {
        return new Options(input, outputDir, renderPages, extractImages, dpi, pages, password, sortByPosition, force, value);
    }
}
