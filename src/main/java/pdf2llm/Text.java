package pdf2llm;

import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Text, Markdown and file-name helpers.
 */
final class Text {
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+\\n");
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{4,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B-\\u001F\\u007F]");
    private static final Pattern BACKTICK_RUN = Pattern.compile("`+");
    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[^\\p{L}\\p{N}._-]+");
    private static final String MARKDOWN_SPECIAL = "\\`*_[]<>|";
    private static final int MAX_FILE_NAME_CODE_POINTS = 150;

    private Text() {
    }

    /**
     * Normalises line endings, strips control characters and collapses runs of blank lines.
     */
    static String cleanText(String text) {
        var s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = CONTROL_CHARS.matcher(s).replaceAll("");
        s = TRAILING_SPACES.matcher(s).replaceAll("\n");
        s = EXCESS_BLANK_LINES.matcher(s).replaceAll("\n\n\n");
        return s.strip();
    }

    /**
     * Escapes inline Markdown syntax and flattens whitespace so the value is safe in a list item or link text.
     */
    static String escapeMarkdown(String value) {
        var sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else if (MARKDOWN_SPECIAL.indexOf(ch) >= 0) {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static String fenced(String text) {
        return fenced(text, "text");
    }

    /**
     * Wraps {@code text} in a code fence that is longer than any backtick run inside it.
     */
    static String fenced(String text, String info) {
        int longest = BACKTICK_RUN.matcher(text).results().mapToInt(r -> r.end() - r.start()).max().orElse(0);
        var fence = "`".repeat(Math.max(3, longest + 1));
        return fence + info + "\n" + text + "\n" + fence;
    }

    /**
     * Quotes a value as a YAML (JSON-compatible) double-quoted scalar.
     */
    static String yamlQuote(String value) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        sb.append("\\u").append(padded(ch, 4, 16));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Turns an untrusted name (for example a PDF attachment name) into a safe single path segment.
     * Letters and digits from any script are kept; separators, leading dots and everything else are replaced.
     */
    static String sanitizeFileName(String value, String fallback) {
        var cleaned = trimChars(UNSAFE_FILE_CHARS.matcher(value).replaceAll("_"), "_.-");
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_FILE_NAME_CODE_POINTS) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_FILE_NAME_CODE_POINTS));
        }
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static String trimChars(String s, String chars) {
        int start = 0;
        int end = s.length();
        while (start < end && chars.indexOf(s.charAt(start)) >= 0) start++;
        while (end > start && chars.indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(start, end);
    }

    /**
     * Returns {@code candidate}, or {@code stem-2.ext}, {@code stem-3.ext}... if a name differing only
     * by case is taken.
     */
    static String uniqueFileName(String candidate, Set<String> used) {
        if (used.add(candidate.toLowerCase(Locale.ROOT))) return candidate;
        int dot = candidate.lastIndexOf('.');
        var stem = dot > 0 ? candidate.substring(0, dot) : candidate;
        var ext = dot > 0 ? candidate.substring(dot) : "";
        for (int counter = 2; ; counter++) {
            var next = stem + "-" + counter + ext;
            if (used.add(next.toLowerCase(Locale.ROOT))) return next;
        }
    }

    /**
     * Locale-independent zero padding (String.format can emit Arabic-Indic digits under some locales).
     */
    static String padded(int number, int width) {
        return padded(number, width, 10);
    }

    static String padded(int number, int width, int radix) {
        var digits = Integer.toString(number, radix);
        return digits.length() >= width ? digits : "0".repeat(width - digits.length()) + digits;
    }

    static String pageFileName(int page) {
        return "page-" + padded(page, 4) + ".md";
    }

    /**
     * Formats a URI as a Markdown autolink.
     */
    static String autolink(String uri) {
        var safe = uri.strip()
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "%20")
                .replace("<", "%3C")
                .replace(">", "%3E");
        return "<" + safe + ">";
    }

    /**
     * ISO-8601 date with offset, or {@code null} when the PDF has no (valid) date.
     */
    static String formatDate(Calendar date) {
        if (date == null) return null;
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(date.toInstant().atZone(date.getTimeZone().toZoneId()));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
