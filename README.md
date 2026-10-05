# pdf2llm

Turn a PDF into a **page-aware, LLM-friendly corpus**: Markdown per page, plain text, images, links, bookmarks,
attachments, optional page renderings, and a machine-readable manifest.

Written in **Java 25**, using [Apache PDFBox](https://pdfbox.apache.org/) 3.x and **Gradle (Kotlin DSL)**. Gradle
manages the runtime libraries; no external services are required.

```bash
./gradlew installDist
build/install/pdf2llm/bin/pdf2llm book.pdf ./book-llm --render-pages
```

## Why

PDFs are visual documents, and their text layer often loses layout and context. pdf2llm keeps each **original page
boundary** as the unit of extraction, so retrieval chunks and citations stay aligned. Optional page renderings preserve
visual details that text can miss, such as diagrams, tables, equations, and scans.

## Requirements

- **JDK 25** to run the launcher script or jar.
- For builds, the Gradle wrapper uses the Foojay resolver to provision the required JDK 25 toolchain if it is not
  already installed.
- Internet access on the first build (dependencies and, if needed, the JDK).

## Build and run

| Goal                      | Command                                                                           |
|---------------------------|-----------------------------------------------------------------------------------|
| Run from Gradle           | `./gradlew run --args="/abs/path/book.pdf /abs/path/out --render-pages"`          |
| Install a launcher script | `./gradlew installDist` → `build/install/pdf2llm/bin/pdf2llm` (`.bat` on Windows) |
| Single runnable jar       | `./gradlew shadowJar` → `java -jar build/libs/pdf2llm-1.0.0-all.jar book.pdf`     |
| Distribution archives     | `./gradlew distZip distTar` → `build/distributions/`                              |
| Tests                     | `./gradlew test`                                                                  |
| Everything                | `./gradlew build`                                                                 |

`./gradlew run` executes with the project directory as its working directory. Pass absolute paths to avoid ambiguity, or
use the installed script from any directory.

Tuning the JVM: set `PDF2LLM_OPTS="-Xmx4g"` for the launcher script, or pass `-Xmx4g` to `java` for the jar. Large
scanned books with `--render-pages` benefit from more heap.

## Usage

```text
pdf2llm INPUT.pdf [OUTPUT_DIR] [options]
```

If `OUTPUT_DIR` is omitted, `<INPUT-name>-llm/` is created **next to the PDF**.

| Option                       | Description                                                                                                                                              |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--render-pages`             | Render every selected page to `rendered-pages/page-NNNN.png`. Recommended for technical books and scans.                                                 |
| `--dpi <n>`                  | Resolution for `--render-pages`, 36–1200 (default `120`; `150` is a good choice for small code listings).                                                |
| `--no-images`                | Skip extraction of embedded image objects.                                                                                                               |
| `--pages <spec>`             | Process only some pages: `5`, `1-10`, `1-10,15,20-`, `-3`. Page numbers stay absolute (`page-0127.md` is always PDF page 127).                           |
| `--password <pw>`            | Password for encrypted PDFs. Prefer the `PDF2LLM_PASSWORD` environment variable — command-line arguments are visible to other users in process listings. |
| `--no-sort`                  | Keep text in content-stream order instead of sorting by position. Try this if a multi-column page comes out interleaved.                                 |
| `--force`                    | Allow writing into a non-empty `OUTPUT_DIR`. Replaces pdf2llm's known output paths; unrelated entries at the output root are left untouched.             |
| `--verbose`                  | Show PDFBox warnings (suppressed by default; font warnings are very noisy) and stack traces.                                                             |
| `-h`, `--help` / `--version` | Help / version.                                                                                                                                          |

Both `--opt value` and `--opt=value` work; `--` ends option parsing. Exit codes are `0` for success, `1` for an
extraction failure, and `2` for invalid arguments.

When using `--force`, do not keep unrelated files inside generated paths such as `pages/`, `images/`, or
`rendered-pages/`; those paths are replaced.

### Examples

```bash
pdf2llm book.pdf                                         # text + images + links
pdf2llm book.pdf ./book-llm --render-pages --dpi 150     # full visual fidelity
pdf2llm book.pdf ./excerpt --pages 100-140 --force       # just a chapter
pdf2llm scan.pdf ./scan-llm --render-pages --no-images   # scanned: rely on renderings
PDF2LLM_PASSWORD=secret pdf2llm locked.pdf               # encrypted
```

## Output

```text
book-llm/
├── document.md          entry point: metadata, directory guide, bookmarks, page index, notes, warnings
├── manifest.json        machine-readable index (pages, images, attachments, options, warnings)
├── all-text.txt         all text, separated by "===== PAGE n / N ====="
├── annotations.md       collected links and text-bearing annotations
├── xmp.xml              raw XMP metadata (when present)
├── pages/page-0001.md   one file per page
├── images/              embedded images as PNG (when present)
├── rendered-pages/      full-page PNGs (with --render-pages)
└── attachments/         files embedded in the PDF (when present)
```

`pages/` is always created. The image, rendering, and attachment directories are created as needed based on the PDF
contents and selected options.

### `pages/page-NNNN.md`

````markdown
---
source: "book.pdf"
page: 127
page_label: "xii"
total_pages: 400
---

# Page 127

[previous](page-0126.md) · [document index](../document.md) · [next](page-0128.md)

## Text

```text
Extracted page text…
```

## Embedded images

### Im0 (640 × 480)

![Im0](../images/page-0127-image-001.png)

## Rendered page

![Rendered PDF page 127](../rendered-pages/page-0127.png)

## Annotations and links

- **Link** — <https://example.com>
- **Link** — destination page [12](page-0012.md)
````

Design notes:

- **Front matter** carries the source, page number, printed page label and page count, so a chunk keeps its provenance
  after being embedded.
- **Text is fenced** (with a fence longer than any backtick run in the text), so extracted `#`, `*` or `---` can never
  be mistaken for document structure and code keeps its spacing.
- **Images used on many pages** (logos, headers) are written once and referenced from every page that uses them.
- **Links are resolved**: internal links and named destinations are mapped to page numbers and linked to extracted page
  files when available. Bookmarks include their destination page numbers.
- **Navigation links** let an agent walk the document page by page.

### `manifest.json`

```json
{
  "tool": "pdf2llm",
  "toolVersion": "1.0.0",
  "pdfboxVersion": "3.0.8",
  "source": {
    "fileName": "book.pdf",
    "pageCount": 400,
    "pdfVersion": "1.7",
    "encrypted": false,
    "title": null,
    "author": null
  },
  "options": {
    "pages": "all",
    "extractImages": true,
    "renderPages": true,
    "dpi": 120.0,
    "sortByPosition": true
  },
  "pages": [
    {
      "page": 1,
      "label": null,
      "file": "pages/page-0001.md",
      "textChars": 1830,
      "hasText": true,
      "images": [],
      "rendered": "rendered-pages/page-0001.png",
      "annotations": 2
    }
  ],
  "images": [],
  "attachments": [],
  "annotationCount": 2,
  "warnings": []
}
```

`hasText: false` is a quick way to find pages that may be scans or need OCR. The sample shows one page; a real manifest
contains an entry for each selected page.

## Recommended workflow for technical books

```bash
pdf2llm "Java 8 in Action.pdf" ./java8-llm --render-pages --dpi 150
```

You get two complementary layers:

```text
Semantic layer   pages/*.md, all-text.txt, document.md, manifest.json
Visual layer     rendered-pages/*.png, images/*.png
```

Use the text layer for retrieval and embeddings; when a retrieved page contains a diagram, table or formula, hand the
model the matching `rendered-pages/page-NNNN.png` too.

## Robustness

- Recoverable page-level failures (text, image, annotation, or rendering) are recorded as warnings in
  `manifest.json` and `document.md`; extraction continues.
- Malformed bookmark trees (cycles), form-XObject cycles and name-tree cycles are detected rather than looping.
- Attachment names are sanitised (no path traversal; Unicode letters are kept) and de-duplicated case-insensitively.
- Numbers in file names are never locale-formatted, so `page-0007.md` stays ASCII under any system locale.
- JBIG2-compressed scans are decoded (`jbig2-imageio`). JPEG 2000 images need an extra codec that is not bundled for
  licensing reasons; affected images produce a warning, and the page rendering still works where PDFBox can decode it.

## Limitations

- **No OCR.** Scanned pages without a text layer produce empty text; use `--render-pages` and an OCR or vision tool
  downstream.
- **Layout is not preserved in text.** Multi-column pages, tables and positioned text may read out of order — try
  `--no-sort`, or use the rendering.
- **Images:** only embedded image XObjects are extracted (not inline images or vector art). Use `--render-pages` for
  complete visuals.
- **Link text** is not captured (only the target). Remote (`GoToR`) links are not resolved and may be omitted.

## Development

Run the complete local build and test suite with `./gradlew build`. GitHub Actions runs the same build for pushes and
pull requests.

```text
pdf2llm/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml        # all dependency and plugin versions
├── gradlew, gradlew.bat, gradle/wrapper/
└── src/
    ├── main/java/pdf2llm/
    │   ├── Main.java                # entry point, exit codes, logging
    │   ├── Cli.java                 # argument parsing, usage text, ParsedArgs (sealed)
    │   ├── Options.java             # immutable settings (record)
    │   ├── PageSelection.java       # "1-10,15,20-"
    │   ├── PdfExtractor.java        # the pipeline
    │   ├── Markdown.java            # page / annotation Markdown
    │   ├── Outlines.java            # bookmarks
    │   ├── Links.java               # link and text annotations
    │   ├── Attachments.java         # embedded files
    │   ├── ImageExtractor.java      # embedded images
    │   ├── PageIndex.java           # COS page dictionary -> page number
    │   ├── Manifest.java            # manifest.json model (records)
    │   ├── Json.java                # tiny record-aware JSON writer
    │   ├── Text.java                # escaping, fencing, file-name safety
    │   ├── Describe.java, ExtractionException.java, BuildInfo.java
    ├── main/resources/pdf2llm.properties    # version, expanded by Gradle
    └── test/java/pdf2llm/           # unit tests + end-to-end test on a generated PDF
```

How the code uses modern Java (no preview features, no `--enable-preview`):

- **Records** for settings, the manifest and every JSON-visible value; `Json` serialises them through
  `getRecordComponents()`, so there is no JSON library.
- **Sealed interface + pattern-matching `switch`** for command-line outcomes (`Run` / `Help` / `Version`) and for
  dispatching PDF destinations and XObjects, with `case null` handled explicitly.
- **Unnamed variables (`_`)** for deliberately ignored exceptions and patterns.
- **Text blocks** for the usage text and fixed Markdown.
- `var`, `instanceof` patterns, `Optional`/`OptionalInt` for lookups that may miss, and `List.copyOf` for immutable
  results.

Conventions:

- Versions live in `gradle/libs.versions.toml`; bump them there.
- The build compiles with `--release 25 -Xlint:all`.
- The tests build a small PDF with PDFBox itself (text, bookmark, link, shared image), so no fixtures are committed.
- To update the wrapper: `./gradlew wrapper --gradle-version <x.y.z>`.
- `./gradlew shadowJar` merges `META-INF/services` entries so ImageIO plugins (JBIG2) keep working in the fat jar.

### Migrating from the Kotlin script

The command line is unchanged (`INPUT.pdf [OUTPUT_DIR] [--render-pages] [--no-images]`) and the Kotlin script / Kotlin
Gradle build are gone — the whole program is now plain Java. Differences from the script: the default output directory
is really beside the input; a non-empty output directory needs `--force`; `MANIFEST.md` is replaced by `manifest.json`;
page text is fenced and pages have front matter; there are more options (`--pages`, `--dpi`, `--password`, `--no-sort`,
`--force`, `--verbose`).

## Git hygiene

`.gitignore` excludes `build/`, `.gradle/`, IDE files, `*.pdf` and `*-llm/` so neither source books nor generated
corpora get committed.

## License

This project is licensed under the Apache License 2.0, the same license used by Apache PDFBox. See [LICENSE](LICENSE)
for the full license text. Third-party dependencies retain their respective licenses.
