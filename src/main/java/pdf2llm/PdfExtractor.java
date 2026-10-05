package pdf2llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import pdf2llm.Manifest.AnnotationRecord;
import pdf2llm.Manifest.AttachmentRecord;
import pdf2llm.Manifest.ImageAsset;
import pdf2llm.Manifest.PageEntry;

/**
 * The PDF to corpus pipeline.
 */
final class PdfExtractor {
    /**
     * Names pdf2llm writes into the output directory; only these are ever removed by {@code --force}.
     */
    private static final List<String> GENERATED_ENTRIES = List.of(
            "document.md", "all-text.txt", "annotations.md", "manifest.json", "MANIFEST.md", "xmp.xml",
            "pages", "images", "rendered-pages", "attachments");
    private final Options options;
    private final Progress progress;
    private final Path outDir;
    private final Path pagesDir;
    private final Path imagesDir;
    private final Path renderedDir;
    private final Path attachmentsDir;
    private final List<String> warnings = new ArrayList<>();
    PdfExtractor(Options options) {
        this(options, (done, total) -> {
        });
    }

    PdfExtractor(Options options, Progress progress) {
        this.options = options;
        this.progress = progress;
        this.outDir = options.outputDir();
        this.pagesDir = outDir.resolve("pages");
        this.imagesDir = outDir.resolve("images");
        this.renderedDir = outDir.resolve("rendered-pages");
        this.attachmentsDir = outDir.resolve("attachments");
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    /**
     * Deletes a file or directory tree without following symbolic links.
     */
    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void meta(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- **").append(label).append(":** ").append(Text.escapeMarkdown(value)).append('\n');
        }
    }

    // ------------------------------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------------------------------

    private static void appendAttachments(StringBuilder head, List<AttachmentRecord> attachments) {
        if (attachments.isEmpty()) return;
        head.append("## Embedded files\n\n");
        for (var a : attachments) {
            head.append("- [").append(Text.escapeMarkdown(a.originalName())).append("](").append(a.path()).append(')');
            if (a.description() != null) head.append(" — ").append(Text.escapeMarkdown(a.description()));
            if (a.size() != null) head.append(" — ").append(a.size()).append(" bytes");
            head.append('\n');
        }
        head.append('\n');
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // PDFBox versions differ in what exportXMPMetadata returns; both shapes are handled.
    private static String toText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String toText(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Extraction
    // ------------------------------------------------------------------------------------------

    /**
     * The printed page label, if the PDF defines one that differs from the plain page number.
     */
    private static String labelFor(List<String> pageLabels, int pageNumber) {
        if (pageLabels == null || pageNumber > pageLabels.size()) return null;
        var label = pageLabels.get(pageNumber - 1);
        if (label == null || label.isBlank() || label.equals(Integer.toString(pageNumber))) return null;
        return label;
    }

    Manifest run() throws IOException {
        if (!Files.isRegularFile(options.input())) {
            throw new ExtractionException("PDF not found: " + options.input());
        }
        try (var doc = openDocument()) {
            prepareOutputDirectory();
            return extract(doc);
        }
    }

    private void warn(String message) {
        warnings.add(message);
    }

    private PDDocument openDocument() {
        var file = options.input().toFile();
        var password = options.password();
        try {
            return password != null ? Loader.loadPDF(file, password) : Loader.loadPDF(file);
        } catch (InvalidPasswordException e) {
            throw new ExtractionException(
                    password == null
                            ? "The PDF is password-protected. Pass --password or set " + Options.PASSWORD_ENV + "."
                            : "The supplied password is incorrect.",
                    e);
        } catch (IOException e) {
            throw new ExtractionException("Could not read " + file.getName() + ": " + Describe.of(e), e);
        }
    }

    private void prepareOutputDirectory() throws IOException {
        if (Files.exists(outDir) && !Files.isDirectory(outDir)) {
            throw new ExtractionException("Output path exists and is not a directory: " + outDir);
        }
        if (Files.isDirectory(outDir) && !isEmpty(outDir)) {
            if (!options.force()) {
                throw new ExtractionException("Output directory is not empty: " + outDir + "\n"
                        + "Use --force to overwrite the files pdf2llm generates there, or choose another directory.");
            }
            for (var name : GENERATED_ENTRIES) deleteTree(outDir.resolve(name));
        }
        Files.createDirectories(pagesDir);
        if (options.renderPages()) Files.createDirectories(renderedDir);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private Manifest extract(PDDocument doc) throws IOException {
        int totalPages = doc.getNumberOfPages();
        List<Integer> selected = options.pages().resolve(totalPages);
        if (selected.isEmpty()) {
            throw new ExtractionException("No pages selected: the document has " + totalPages
                    + " page(s) and --pages was \"" + options.pages() + "\".");
        }
        Set<Integer> selectedSet = new HashSet<>(selected);

        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        PDDocumentInformation info = doc.getDocumentInformation();
        PageIndex pageIndex = new PageIndex(doc);
        String title = Text.blankToNull(info.getTitle());
        if (title == null) title = stem(options.input().getFileName().toString());

        // ---- document-level material --------------------------------------------------------
        var head = documentHead(doc, catalog, info, title, totalPages, selected.size());
        if (writeXmp(catalog)) {
            head.append("## XMP metadata\n\nFull XMP metadata is available in [`xmp.xml`](xmp.xml).\n\n");
        }
        Outlines.markdown(doc, pageIndex).ifPresent(outline ->
                head.append("## Bookmarks / table of contents\n\n").append(outline).append('\n'));

        List<AttachmentRecord> attachments = Attachments.extract(catalog, attachmentsDir, this::warn);
        appendAttachments(head, attachments);

        // ---- per-page processing ------------------------------------------------------------
        var stripper = new PDFTextStripper();
        stripper.setSortByPosition(options.sortByPosition());
        stripper.setAddMoreFormatting(true);

        var imageExtractor = options.extractImages() ? new ImageExtractor(imagesDir, this::warn) : null;
        var renderer = options.renderPages() ? new PDFRenderer(doc) : null;
        List<String> pageLabels = readPageLabels(catalog);

        var allAnnotations = new ArrayList<AnnotationRecord>();
        var entries = new ArrayList<PageEntry>(selected.size());

        try (var allText = Files.newBufferedWriter(outDir.resolve("all-text.txt"), StandardCharsets.UTF_8)) {
            int position = 0;
            for (int pageNumber : selected) {
                progress.update(++position, selected.size());

                var page = doc.getPage(pageNumber - 1);
                var text = pageText(stripper, doc, pageNumber);
                List<ImageAsset> images =
                        imageExtractor == null
                                ? List.of()
                                : imageExtractor.extractFrom(page.getResources(), pageNumber);
                var annotations = Links.collect(catalog, page, pageNumber, pageIndex, this::warn);
                allAnnotations.addAll(annotations);
                String rendered = renderer == null ? null : renderPage(renderer, pageNumber);
                String label = labelFor(pageLabels, pageNumber);

                var file = "pages/" + Text.pageFileName(pageNumber);
                var content = new Markdown.PageContent(
                        pageNumber, totalPages, label, text, images, rendered, annotations);
                Files.writeString(
                        outDir.resolve(file),
                        Markdown.page(options.input().getFileName().toString(), content, selectedSet),
                        StandardCharsets.UTF_8);

                allText.write("\n===== PAGE " + pageNumber + " / " + totalPages + " =====\n\n");
                allText.write(text);
                allText.write("\n");

                entries.add(new PageEntry(
                        pageNumber,
                        label,
                        file,
                        text.length(),
                        !text.isBlank(),
                        images.stream().map(ImageAsset::path).toList(),
                        rendered,
                        annotations.size()));
            }
        }

        // ---- document.md, annotations.md, manifest.json -------------------------------------
        Files.writeString(outDir.resolve("document.md"), documentMarkdown(head, entries), StandardCharsets.UTF_8);
        Files.writeString(
                outDir.resolve("annotations.md"),
                Markdown.annotations(allAnnotations, selectedSet),
                StandardCharsets.UTF_8);

        var manifest = new Manifest(
                "pdf2llm",
                BuildInfo.VERSION,
                BuildInfo.PDFBOX_VERSION,
                new Manifest.Source(
                        options.input().getFileName().toString(),
                        totalPages,
                        String.valueOf(doc.getVersion()),
                        doc.isEncrypted(),
                        Text.blankToNull(info.getTitle()),
                        Text.blankToNull(info.getAuthor())),
                new Manifest.Settings(
                        options.pages().toString(),
                        options.extractImages(),
                        options.renderPages(),
                        options.renderPages() ? Float.valueOf(options.dpi()) : null,
                        options.sortByPosition()),
                entries,
                imageExtractor == null ? List.of() : imageExtractor.extracted(),
                attachments,
                allAnnotations.size(),
                warnings);
        Files.writeString(outDir.resolve("manifest.json"), manifest.toJson(), StandardCharsets.UTF_8);
        return manifest;
    }

    private StringBuilder documentHead(
            PDDocument doc,
            PDDocumentCatalog catalog,
            PDDocumentInformation info,
            String title,
            int totalPages,
            int extracted) {
        var head = new StringBuilder();
        head.append("# ").append(Text.escapeMarkdown(title)).append("\n\n");
        head.append("> PDF extracted by pdf2llm for LLM consumption. Text and assets are split by page ")
                .append("to preserve retrieval boundaries.\n\n");

        head.append("""
                ## Directory guide
                
                - `pages/page-NNNN.md` — one file per PDF page (front matter, text, images, links).
                - `all-text.txt` — all extracted text with `===== PAGE n / N =====` separators.
                - `manifest.json` — machine-readable index of pages, images, attachments and warnings.
                - `annotations.md` — every link and annotation, in page order.
                - `images/`, `rendered-pages/`, `attachments/` — created when the PDF or options produce them.
                
                """);

        head.append("## Metadata\n\n");
        meta(head, "Source filename", options.input().getFileName().toString());
        meta(head, "Page count", String.valueOf(totalPages));
        if (!options.pages().isAll()) meta(head, "Pages extracted", options.pages() + " (" + extracted + " pages)");
        meta(head, "PDF version", String.valueOf(doc.getVersion()));
        meta(head, "Encrypted", String.valueOf(doc.isEncrypted()));
        meta(head, "Title", info.getTitle());
        meta(head, "Author", info.getAuthor());
        meta(head, "Subject", info.getSubject());
        meta(head, "Keywords", info.getKeywords());
        meta(head, "Creator", info.getCreator());
        meta(head, "Producer", info.getProducer());
        meta(head, "Creation date", Text.formatDate(info.getCreationDate()));
        meta(head, "Modification date", Text.formatDate(info.getModificationDate()));
        var layout = catalog.getPageLayout();
        meta(head, "Page layout", layout == null ? null : layout.name());
        var mode = catalog.getPageMode();
        meta(head, "Page mode", mode == null ? null : mode.name());
        meta(head, "Generated by", "pdf2llm " + BuildInfo.VERSION);
        head.append('\n');
        return head;
    }

    private String documentMarkdown(StringBuilder head, List<PageEntry> entries) {
        var body = new StringBuilder(head);
        body.append("## Pages\n\n");
        body.append("Each page has its own Markdown file under `pages/`; use them for page-level retrieval "
                + "and citation.\n\n");
        for (var e : entries) {
            body.append("- [Page ").append(e.page()).append("](").append(e.file()).append(')');
            if (e.label() != null) body.append(" — label ").append(Text.escapeMarkdown(e.label()));
            if (!e.hasText()) body.append(" — no selectable text");
            if (!e.images().isEmpty()) body.append(" — ").append(e.images().size()).append(" image(s)");
            body.append('\n');
        }

        body.append("\n## Extraction notes\n\n");
        body.append("- Text extraction preserves text content but not the full visual layout. Reading order "
                + "can differ on complex multi-column pages, tables, or positioned text.\n");
        body.append("- Embedded image XObjects are decoded to PNG when possible. Inline images and vector "
                + "graphics are not extracted.\n");
        if (options.renderPages()) {
            body.append("- Full-page PNG renderings are in `rendered-pages/` (").append((int) options.dpi())
                    .append(" DPI); consult them for diagrams, tables, equations and scans.\n");
        } else {
            body.append("- Page renderings were not requested. Re-run with `--render-pages` to preserve the "
                    + "visual appearance of every page.\n");
        }
        body.append("- OCR is not performed. Scanned pages have no selectable text unless an OCR layer "
                + "already exists.\n");
        if (!warnings.isEmpty()) {
            body.append("\n## Warnings\n\n");
            for (var w : warnings) body.append("- ").append(Text.escapeMarkdown(w)).append('\n');
        }
        return body.toString();
    }

    private boolean writeXmp(PDDocumentCatalog catalog) {
        try {
            var metadata = catalog.getMetadata();
            if (metadata == null) return false;
            var xmp = toText(metadata.exportXMPMetadata());
            if (xmp.isBlank()) return false;
            Files.writeString(outDir.resolve("xmp.xml"), xmp, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            warn("Could not export XMP metadata (" + Describe.of(e) + ").");
            return false;
        }
    }

    private List<String> readPageLabels(PDDocumentCatalog catalog) {
        try {
            var labels = catalog.getPageLabels();
            return labels == null ? null : Arrays.asList(labels.getLabelsByPageIndices());
        } catch (Exception e) {
            warn("Could not read page labels (" + Describe.of(e) + ").");
            return null;
        }
    }

    private String pageText(PDFTextStripper stripper, PDDocument doc, int pageNumber) {
        try {
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            return Text.cleanText(stripper.getText(doc));
        } catch (Exception e) {
            warn("Page " + pageNumber + ": text extraction failed (" + Describe.of(e) + ").");
            return "";
        }
    }

    private String renderPage(PDFRenderer renderer, int pageNumber) {
        try {
            var image = renderer.renderImageWithDPI(pageNumber - 1, options.dpi(), ImageType.RGB);
            var name = "page-" + Text.padded(pageNumber, 4) + ".png";
            ImageIO.write(image, "png", renderedDir.resolve(name).toFile());
            image.flush();
            return "rendered-pages/" + name;
        } catch (Exception e) {
            warn("Page " + pageNumber + ": rendering failed (" + Describe.of(e) + ").");
            return null;
        }
    }

    /**
     * Called after each page has been started.
     */
    @FunctionalInterface
    interface Progress {
        void update(int done, int total);
    }
}
