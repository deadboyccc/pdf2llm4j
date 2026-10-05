package pdf2llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import pdf2llm.Manifest.AnnotationRecord;
import pdf2llm.Manifest.ImageAsset;

/**
 * Formatting of the per-page Markdown files and annotation lists.
 */
final class Markdown {
    private Markdown() {
    }

    static String page(String sourceName, PageContent page, Set<Integer> selected) {
        int number = page.pageNumber();
        var sb = new StringBuilder();

        sb.append("---\n");
        sb.append("source: ").append(Text.yamlQuote(sourceName)).append('\n');
        sb.append("page: ").append(number).append('\n');
        if (page.label() != null) sb.append("page_label: ").append(Text.yamlQuote(page.label())).append('\n');
        sb.append("total_pages: ").append(page.totalPages()).append('\n');
        sb.append("---\n\n");

        sb.append("# Page ").append(number).append("\n\n");

        var nav = new ArrayList<String>();
        if (selected.contains(number - 1)) nav.add("[previous](" + Text.pageFileName(number - 1) + ")");
        nav.add("[document index](../document.md)");
        if (selected.contains(number + 1)) nav.add("[next](" + Text.pageFileName(number + 1) + ")");
        sb.append(String.join(" · ", nav)).append("\n\n");

        if (page.label() != null) {
            sb.append("**PDF page label:** ").append(Text.escapeMarkdown(page.label())).append("\n\n");
        }

        sb.append("## Text\n\n");
        if (page.text().isBlank()) {
            sb.append("[No selectable text extracted. This page may be scanned/image-only or use an encoding PDFBox cannot decode.]\n\n");
        } else {
            sb.append(Text.fenced(page.text())).append("\n\n");
        }

        if (!page.images().isEmpty()) {
            sb.append("## Embedded images\n\n");
            for (var image : page.images()) {
                var name = Text.escapeMarkdown(image.name());
                sb.append("### ").append(name).append(" (").append(image.width()).append(" × ").append(image.height()).append(")\n\n");
                sb.append("![").append(name).append("](../").append(image.path()).append(")\n\n");
            }
        }

        if (page.rendered() != null) {
            sb.append("## Rendered page\n\n");
            sb.append("![Rendered PDF page ").append(number).append("](../").append(page.rendered()).append(")\n\n");
        }

        if (!page.annotations().isEmpty()) {
            sb.append("## Annotations and links\n\n");
            for (var annotation : page.annotations()) {
                sb.append(annotationLine(annotation, "", selected, false)).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * The document-wide {@code annotations.md}.
     */
    static String annotations(List<AnnotationRecord> all, Set<Integer> selected) {
        var sb = new StringBuilder("# Annotations and links\n\n");
        if (all.isEmpty()) {
            sb.append("No page annotations were found.\n");
        } else {
            for (var annotation : all) {
                sb.append(annotationLine(annotation, "pages/", selected, true)).append('\n');
            }
        }
        return sb.toString();
    }

    private static String annotationLine(AnnotationRecord a, String pagePrefix, Set<Integer> selected, boolean includePage) {
        var sb = new StringBuilder("- ");
        if (includePage) sb.append("Page ").append(a.page()).append(": ");
        sb.append("**").append(Text.escapeMarkdown(a.subtype())).append("**");
        if (a.contents() != null) sb.append(": ").append(Text.escapeMarkdown(a.contents()));
        if (a.uri() != null) sb.append(" — ").append(Text.autolink(a.uri()));

        Integer destination = a.destinationPage();
        if (destination != null) {
            sb.append(" — destination page ");
            if (selected.contains(destination)) {
                sb.append('[').append(destination).append("](").append(pagePrefix)
                        .append(Text.pageFileName(destination)).append(')');
            } else {
                sb.append(destination);
            }
        }
        return sb.toString();
    }

    /**
     * Everything that goes into one page file.
     */
    record PageContent(
            int pageNumber,
            int totalPages,
            String label,
            String text,
            List<ImageAsset> images,
            String rendered,
            List<AnnotationRecord> annotations) {
    }
}
