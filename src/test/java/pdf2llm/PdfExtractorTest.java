package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfExtractorTest {
    /**
     * Three A4 pages: text plus one shared image on each, a bookmark to page 2, and a URI link on page 1.
     */
    private static void writeSamplePdf(Path target) throws IOException {
        try (var doc = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var image = LosslessFactory.createFromImage(doc, new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB));
            List<PDPage> pages = new ArrayList<>();
            for (int n = 1; n <= 3; n++) {
                var page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (var content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(font, 12f);
                    content.newLineAtOffset(72f, 720f);
                    content.showText("Hello from page " + n);
                    content.endText();
                    content.drawImage(image, 72f, 600f, 64f, 64f);
                }
                pages.add(page);
            }

            var outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);
            var destination = new PDPageFitWidthDestination();
            destination.setPage(pages.get(1));
            var chapter = new PDOutlineItem();
            chapter.setTitle("Chapter Two");
            chapter.setDestination(destination);
            outline.addLast(chapter);

            var action = new PDActionURI();
            action.setURI("https://example.com/docs");
            var link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(72f, 700f, 200f, 24f));
            link.setAction(action);
            pages.get(0).getAnnotations().add(link);

            doc.save(target.toFile());
        }
    }

    @Test
    void extractsTextOutlineLinksAndSharedImages(@TempDir Path dir) throws IOException {
        var pdf = dir.resolve("sample.pdf");
        writeSamplePdf(pdf);
        var out = dir.resolve("sample-llm");

        var manifest = new PdfExtractor(Options.of(pdf, out)).run();

        assertEquals(3, manifest.pages().size());
        var page1 = Files.readString(out.resolve("pages/page-0001.md"));
        assertTrue(page1.contains("Hello from page 1"));
        assertTrue(page1.contains("total_pages: 3"));
        assertTrue(Files.readString(out.resolve("all-text.txt")).contains("===== PAGE 3 / 3 ====="));

        var document = Files.readString(out.resolve("document.md"));
        assertTrue(document.contains("Chapter Two"));
        assertTrue(document.contains("page 2"));
        assertTrue(Files.readString(out.resolve("annotations.md")).contains("https://example.com/docs"));

        // The shared image is written once and referenced from every page.
        try (var images = Files.list(out.resolve("images"))) {
            assertEquals(1L, images.count());
        }
        assertTrue(manifest.pages().stream()
                .allMatch(p -> p.images().equals(List.of("images/page-0001-image-001.png"))));
        assertEquals(1, manifest.images().size());

        var json = Files.readString(out.resolve("manifest.json"));
        assertEquals(manifest.toJson(), json);
        assertTrue(json.contains("\"pageCount\": 3"));
    }

    @Test
    void rendersOnlySelectedPages(@TempDir Path dir) throws IOException {
        var pdf = dir.resolve("sample.pdf");
        writeSamplePdf(pdf);
        var out = dir.resolve("out");

        new PdfExtractor(Options.of(pdf, out)
                .withRenderPages(true)
                .withDpi(50f)
                .withPages(PageSelection.parse("2")))
                .run();

        assertTrue(Files.exists(out.resolve("rendered-pages/page-0002.png")));
        assertTrue(Files.exists(out.resolve("pages/page-0002.md")));
        assertFalse(Files.exists(out.resolve("pages/page-0001.md")));
    }

    @Test
    void refusesNonEmptyOutputUnlessForced(@TempDir Path dir) throws IOException {
        var pdf = dir.resolve("sample.pdf");
        writeSamplePdf(pdf);
        var out = dir.resolve("out");
        Files.createDirectories(out);
        Files.writeString(out.resolve("keep.txt"), "mine");

        assertThrows(ExtractionException.class, () -> new PdfExtractor(Options.of(pdf, out)).run());

        new PdfExtractor(Options.of(pdf, out).withForce(true)).run();
        assertTrue(Files.exists(out.resolve("document.md")));
        assertEquals("mine", Files.readString(out.resolve("keep.txt")));
    }

    @Test
    void reportsMissingInput(@TempDir Path dir) {
        assertThrows(ExtractionException.class,
                () -> new PdfExtractor(Options.of(dir.resolve("nope.pdf"), dir.resolve("o"))).run());
    }
}
