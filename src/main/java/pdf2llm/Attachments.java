package pdf2llm;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import pdf2llm.Manifest.AttachmentRecord;

/**
 * Extracts files embedded in the PDF's EmbeddedFiles name tree.
 */
final class Attachments {
    private static final int MAX_TREE_DEPTH = 32;

    private Attachments() {
    }

    static List<AttachmentRecord> extract(PDDocumentCatalog catalog, Path dir, Consumer<String> warn) {
        var embedded = embeddedFiles(catalog);
        if (embedded == null) return List.of();

        var records = new ArrayList<AttachmentRecord>();
        Set<String> usedNames = new HashSet<>();
        walk(embedded, 0, (treeName, spec) -> save(treeName, spec, dir, usedNames, records, warn));
        return records;
    }

    private static PDNameTreeNode<PDComplexFileSpecification> embeddedFiles(PDDocumentCatalog catalog) {
        try {
            return new PDDocumentNameDictionary(catalog).getEmbeddedFiles();
        } catch (Exception _) {
            return null;
        }
    }

    private static <T extends COSObjectable> void walk(PDNameTreeNode<T> node, int depth, BiConsumer<String, T> visitor) {
        if (node == null || depth > MAX_TREE_DEPTH) return;

        Map<String, T> names = null;
        try {
            names = node.getNames();
        } catch (Exception _) {
            // Some malformed PDFs make an individual names node unreadable.
        }
        if (names != null) names.forEach(visitor);

        List<PDNameTreeNode<T>> kids = null;
        try {
            kids = node.getKids();
        } catch (Exception _) {
            // ditto
        }
        if (kids != null) {
            for (var kid : kids) walk(kid, depth + 1, visitor);
        }
    }

    private static void save(
            String treeName,
            PDComplexFileSpecification spec,
            Path dir,
            Set<String> usedNames,
            List<AttachmentRecord> records,
            Consumer<String> warn) {
        PDEmbeddedFile file;
        try {
            file = spec.getEmbeddedFile();
            if (file == null) file = spec.getEmbeddedFileUnicode();
        } catch (Exception _) {
            return;
        }
        if (file == null) return;

        var originalName = firstNonBlank(spec.getFileUnicode(), spec.getFile(), treeName, "attachment");
        var safeName = Text.uniqueFileName(Text.sanitizeFileName(originalName, "attachment"), usedNames);

        try (InputStream in = file.createInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(safeName));
            Long size = null;
            try {
                int declared = file.getSize();
                if (declared >= 0) size = (long) declared;
            } catch (Exception _) {
                // size is informational only
            }
            records.add(new AttachmentRecord(
                    originalName, "attachments/" + safeName, Text.blankToNull(spec.getFileDescription()), size));
        } catch (Exception e) {
            warn.accept("Could not extract attachment '" + originalName + "' (" + Describe.of(e) + ").");
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return candidates[candidates.length - 1];
    }
}
