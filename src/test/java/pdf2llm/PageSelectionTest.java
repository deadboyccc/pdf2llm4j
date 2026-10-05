package pdf2llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageSelectionTest {
    @Test
    void parsesRangesListsAndOpenEnds() {
        assertEquals(List.of(1, 2, 3, 5, 8, 9, 10), PageSelection.parse("1-3,5,8-").resolve(10));
        assertEquals(List.of(1, 3), PageSelection.parse("3,3,1").resolve(5));
        assertEquals(List.of(1, 2), PageSelection.parse("-2").resolve(10));
        assertEquals(List.of(2, 3, 4), PageSelection.parse(" 2 - 4 ").resolve(10));
    }

    @Test
    void clipsToTheDocument() {
        assertTrue(PageSelection.parse("20-").resolve(10).isEmpty());
        assertEquals(List.of(9, 10), PageSelection.parse("9-99").resolve(10));
    }

    @Test
    void allPages() {
        assertEquals(List.of(1, 2, 3, 4), PageSelection.ALL.resolve(4));
        assertTrue(PageSelection.ALL.isAll());
        assertEquals("all", PageSelection.ALL.toString());
        assertEquals("1-3", PageSelection.parse("1-3").toString());
    }

    @Test
    void rejectsMalformedSpecs() {
        assertThrows(IllegalArgumentException.class, () -> PageSelection.parse("5-2"));
        assertThrows(IllegalArgumentException.class, () -> PageSelection.parse("a"));
        assertThrows(IllegalArgumentException.class, () -> PageSelection.parse("0"));
        assertThrows(IllegalArgumentException.class, () -> PageSelection.parse("1,,2"));
        assertThrows(IllegalArgumentException.class, () -> PageSelection.parse("-"));
    }
}
