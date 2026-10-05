package pdf2llm;

/**
 * Human-readable one-line description of an exception.
 */
final class Describe {
    private Describe() {
    }

    static String of(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
