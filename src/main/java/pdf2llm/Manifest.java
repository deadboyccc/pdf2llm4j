package pdf2llm;

import java.util.List;

/**
 * The machine-readable index written to {@code manifest.json}. Field order is the JSON order.
 */
record Manifest(
        String tool,
        String toolVersion,
        String pdfboxVersion,
        Source source,
        Settings options,
        List<PageEntry> pages,
        List<ImageAsset> images,
        List<AttachmentRecord> attachments,
        int annotationCount,
        List<String> warnings) {

    Manifest {
        pages = List.copyOf(pages);
        images = List.copyOf(images);
        attachments = List.copyOf(attachments);
        warnings = List.copyOf(warnings);
    }

    String toJson() {
        return Json.toJson(this);
    }

    record Source(String fileName, int pageCount, String pdfVersion, boolean encrypted, String title, String author) {
    }

    record Settings(String pages, boolean extractImages, boolean renderPages, Float dpi, boolean sortByPosition) {
    }

    record PageEntry(
            int page,
            String label,
            String file,
            int textChars,
            boolean hasText,
            List<String> images,
            String rendered,
            int annotations) {
    }

    record ImageAsset(String path, int firstPage, String name, int width, int height) {
    }

    record AnnotationRecord(int page, String subtype, String contents, String uri, Integer destinationPage) {
    }

    record AttachmentRecord(String originalName, String path, String description, Long size) {
    }
}
