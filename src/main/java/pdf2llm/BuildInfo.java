package pdf2llm;

import java.io.IOException;
import java.util.Properties;

/**
 * Values injected at build time by Gradle ({@code processResources} expands {@code pdf2llm.properties}).
 */
final class BuildInfo {
    static final String VERSION;
    static final String PDFBOX_VERSION;

    static {
        var props = new Properties();
        try (var in = BuildInfo.class.getResourceAsStream("/pdf2llm.properties")) {
            if (in != null) props.load(in);
        } catch (IOException _) {
            // fall back to the defaults below
        }
        VERSION = props.getProperty("version", "dev");
        PDFBOX_VERSION = props.getProperty("pdfbox", "unknown");
    }

    private BuildInfo() {
    }
}
