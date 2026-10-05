package pdf2llm;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import pdf2llm.Manifest.ImageAsset;

/**
 * Extracts image XObjects to PNG. An image used on several pages is written once (on the first page it
 * is seen) and reported for every page that references it.
 */
final class ImageExtractor {
    private final Path dir;
    private final Consumer<String> warn;
    private final Map<Object, Optional<ImageAsset>> seen = new IdentityHashMap<>();
    private final Map<Integer, Integer> perPageCount = new HashMap<>();
    private final List<ImageAsset> assets = new ArrayList<>();

    ImageExtractor(Path dir, Consumer<String> warn) {
        this.dir = dir;
        this.warn = warn;
    }

    /**
     * Every distinct image written so far.
     */
    List<ImageAsset> extracted() {
        return List.copyOf(assets);
    }

    /**
     * Images referenced by the page, including ones first extracted on an earlier page.
     */
    List<ImageAsset> extractFrom(PDResources resources, int pageNumber) {
        var found = new LinkedHashSet<ImageAsset>();
        try {
            walk(resources, pageNumber, found, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (Exception e) {
            warn.accept("Page " + pageNumber + ": image scan failed (" + Describe.of(e) + ").");
        }
        return List.copyOf(found);
    }

    private void walk(PDResources resources, int pageNumber, Set<ImageAsset> found, Set<Object> visitedForms)
            throws IOException {
        if (resources == null) return;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject;
            try {
                xObject = resources.getXObject(name);
            } catch (Exception _) {
                continue;
            }
            switch (xObject) {
                case PDImageXObject image -> {
                    var asset = extractImage(image, name.getName(), pageNumber);
                    if (asset != null) found.add(asset);
                }
                case PDFormXObject form -> {
                    if (visitedForms.add(form.getCOSObject())) {
                        walk(form.getResources(), pageNumber, found, visitedForms);
                    }
                }
                case null, default -> {
                    // other XObject kinds carry no extractable image
                }
            }
        }
    }

    private ImageAsset extractImage(PDImageXObject image, String pdfName, int pageNumber) {
        Object key = image.getCOSObject();
        var cached = seen.get(key);
        if (cached != null) return cached.orElse(null);

        int ordinal = perPageCount.merge(pageNumber, 1, Integer::sum);
        var fileName = "page-" + Text.padded(pageNumber, 4) + "-image-" + Text.padded(ordinal, 3) + ".png";

        ImageAsset asset;
        try {
            BufferedImage bitmap = image.getImage();
            Files.createDirectories(dir);
            if (!ImageIO.write(bitmap, "png", dir.resolve(fileName).toFile())) {
                throw new IOException("no PNG writer available");
            }
            asset = new ImageAsset("images/" + fileName, pageNumber, pdfName, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            warn.accept("Could not extract image " + pdfName + " on page " + pageNumber + " (" + Describe.of(e) + ").");
            asset = null;
        }

        seen.put(key, Optional.ofNullable(asset));
        if (asset != null) assets.add(asset);
        return asset;
    }
}
