package pdf2llm;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

/**
 * Renders the PDF's bookmarks as a nested Markdown list.
 */
final class Outlines {
    private static final int MAX_ITEMS = 200_000;

    private Outlines() {
    }

    static Optional<String> markdown(PDDocument doc, PageIndex pages) {
        var root = doc.getDocumentCatalog().getDocumentOutline();
        if (root == null) return Optional.empty();
        var first = root.getFirstChild();
        if (first == null) return Optional.empty();

        var sb = new StringBuilder();
        Set<COSDictionary> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        append(sb, doc, first, 0, pages, visited);
        return sb.toString().isBlank() ? Optional.empty() : Optional.of(sb.toString());
    }

    private static void append(
            StringBuilder sb, PDDocument doc, PDOutlineItem first, int depth, PageIndex pages, Set<COSDictionary> visited) {
        for (var current = first; current != null && visited.size() < MAX_ITEMS; current = current.getNextSibling()) {
            // Malformed files can contain cycles; never visit the same item twice.
            if (!visited.add(current.getCOSObject())) return;

            var title = Text.blankToNull(current.getTitle());
            sb.append("  ".repeat(depth)).append("- ").append(Text.escapeMarkdown(title == null ? "Untitled" : title));
            var page = pageOf(doc, current, pages);
            if (page.isPresent()) sb.append(" — page ").append(page.getAsInt());
            sb.append('\n');

            var child = current.getFirstChild();
            if (child != null) append(sb, doc, child, depth + 1, pages, visited);
        }
    }

    private static OptionalInt pageOf(PDDocument doc, PDOutlineItem item, PageIndex pages) {
        try {
            return pages.numberOf(item.findDestinationPage(doc));
        } catch (Exception _) {
            return OptionalInt.empty();
        }
    }
}
