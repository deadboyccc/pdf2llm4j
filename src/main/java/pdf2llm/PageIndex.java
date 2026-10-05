package pdf2llm;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalInt;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Maps a page's COS dictionary (by identity) to its 1-based page number.
 * PDFBox hands out fresh {@link PDPage} wrappers on every access, so wrappers cannot be compared.
 */
final class PageIndex {
    private final Map<COSDictionary, Integer> numbers = new IdentityHashMap<>();

    PageIndex(PDDocument doc) {
        int number = 1;
        for (PDPage page : doc.getPages()) {
            numbers.put(page.getCOSObject(), number++);
        }
    }

    OptionalInt numberOf(PDPage page) {
        if (page == null) return OptionalInt.empty();
        var number = numbers.get(page.getCOSObject());
        return number == null ? OptionalInt.empty() : OptionalInt.of(number);
    }
}
