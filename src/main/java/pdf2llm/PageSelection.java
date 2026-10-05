package pdf2llm;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A 1-based page selection such as {@code 1-10,15,20-}.
 */
final class PageSelection {
    /**
     * Every page.
     */
    static final PageSelection ALL = new PageSelection(null, null);
    private final List<Range> ranges; // null means "all pages"
    private final String spec;
    private PageSelection(List<Range> ranges, String spec) {
        this.ranges = ranges;
        this.spec = spec;
    }

    /**
     * @throws IllegalArgumentException if the specification is malformed
     */
    static PageSelection parse(String spec) {
        var ranges = new ArrayList<Range>();
        for (var part : spec.split(",", -1)) {
            var token = part.strip();
            if (token.isEmpty()) throw new IllegalArgumentException("empty entry in \"" + spec + "\"");

            int dash = token.indexOf('-');
            if (dash < 0) {
                int page = pageNumber(token);
                ranges.add(new Range(page, page));
                continue;
            }
            var startText = token.substring(0, dash).strip();
            var endText = token.substring(dash + 1).strip();
            if (startText.isEmpty() && endText.isEmpty()) {
                throw new IllegalArgumentException("invalid range \"" + token + "\"");
            }
            int start = startText.isEmpty() ? 1 : pageNumber(startText);
            int end = endText.isEmpty() ? Integer.MAX_VALUE : pageNumber(endText);
            if (start > end) throw new IllegalArgumentException("range \"" + token + "\" is reversed");
            ranges.add(new Range(start, end));
        }
        return new PageSelection(List.copyOf(ranges), spec.strip());
    }

    private static int pageNumber(String text) {
        try {
            int page = Integer.parseInt(text);
            if (page >= 1) return page;
        } catch (NumberFormatException _) {
            // fall through to the common error below
        }
        throw new IllegalArgumentException("invalid page number \"" + text + "\"");
    }

    boolean isAll() {
        return ranges == null;
    }

    /**
     * Sorted, de-duplicated page numbers within {@code 1..totalPages}.
     */
    List<Integer> resolve(int totalPages) {
        var selected = new TreeSet<Integer>();
        if (ranges == null) {
            for (int page = 1; page <= totalPages; page++) selected.add(page);
        } else {
            for (var range : ranges) {
                int last = Math.min(range.last(), totalPages);
                for (int page = Math.max(range.first(), 1); page <= last; page++) selected.add(page);
            }
        }
        return List.copyOf(selected);
    }

    @Override
    public String toString() {
        return spec == null ? "all" : spec;
    }

    private record Range(int first, int last) {
    }
}
