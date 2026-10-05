package pdf2llm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import pdf2llm.Manifest.AnnotationRecord;

/**
 * Reads links and text annotations from a page.
 */
final class Links {
    private Links() {
    }

    static List<AnnotationRecord> collect(
            PDDocumentCatalog catalog, PDPage page, int pageNumber, PageIndex pages, Consumer<String> warn) {
        List<PDAnnotation> annotations;
        try {
            annotations = page.getAnnotations();
        } catch (Exception e) {
            warn.accept("Page " + pageNumber + ": could not read annotations (" + Describe.of(e) + ").");
            return List.of();
        }

        var records = new ArrayList<AnnotationRecord>();
        for (var annotation : annotations) {
            try {
                toRecord(catalog, annotation, pageNumber, pages).ifPresent(records::add);
            } catch (Exception _) {
                // A single malformed annotation should not abort the page.
            }
        }
        return records;
    }

    private static Optional<AnnotationRecord> toRecord(
            PDDocumentCatalog catalog, PDAnnotation annotation, int pageNumber, PageIndex pages) throws IOException {
        var subtype = annotation.getSubtype();
        if (subtype == null || subtype.equals("Popup")) return Optional.empty();

        var contents = Text.blankToNull(annotation.getContents());

        if (annotation instanceof PDAnnotationLink link) {
            var action = link.getAction();
            var uri = action instanceof PDActionURI uriAction ? Text.blankToNull(uriAction.getURI()) : null;

            PDDestination destination = link.getDestination();
            if (destination == null && action instanceof PDActionGoTo goTo) {
                destination = goTo.getDestination();
            }
            var destinationPage = destinationPage(catalog, destination, pages);

            if (uri == null && destinationPage.isEmpty() && contents == null) return Optional.empty();
            return Optional.of(new AnnotationRecord(
                    pageNumber,
                    subtype,
                    contents,
                    uri,
                    destinationPage.isPresent() ? destinationPage.getAsInt() : null));
        }

        // Other annotation types are only interesting when they carry text (comments, notes, highlights).
        if (contents == null) return Optional.empty();
        return Optional.of(new AnnotationRecord(pageNumber, subtype, contents, null, null));
    }

    private static OptionalInt destinationPage(PDDocumentCatalog catalog, PDDestination destination, PageIndex pages)
            throws IOException {
        PDPageDestination pageDestination = switch (destination) {
            case PDPageDestination direct -> direct;
            case PDNamedDestination named -> catalog.findNamedDestinationPage(named);
            case null, default -> null;
        };
        return pageDestination == null ? OptionalInt.empty() : pages.numberOf(pageDestination.getPage());
    }
}
