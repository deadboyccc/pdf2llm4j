package pdf2llm;

/**
 * A problem worth reporting to the user as-is (bad input, unwritable output, wrong password...).
 */
final class ExtractionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ExtractionException(String message) {
        super(message);
    }

    ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
