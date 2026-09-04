package com.akkc.tensor.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendResourceBuildTest {

    private static final Pattern HASHED_JAVASCRIPT =
            Pattern.compile("^.+-[A-Za-z0-9_-]+\\.js$");
    private static final Pattern HASHED_STYLESHEET =
            Pattern.compile("^.+-[A-Za-z0-9_-]+\\.css$");

    @Test
    void generatesIndexReferencingHashedJavaScriptAndStylesheet() throws IOException {
        Path staticRoot = Path.of("target", "generated-resources", "static");
        Path index = staticRoot.resolve("index.html");
        Path assets = staticRoot.resolve("assets");

        assertThat(index).isRegularFile();
        assertThat(assets).isDirectory();

        List<Path> assetFiles;
        try (Stream<Path> paths = Files.list(assets)) {
            assetFiles = paths.filter(Files::isRegularFile).toList();
        }
        String indexHtml = Files.readString(index, StandardCharsets.UTF_8);
        Path javascript = requireAsset(assetFiles, indexHtml, HASHED_JAVASCRIPT, "hashed JavaScript");
        Path stylesheet = requireAsset(assetFiles, indexHtml, HASHED_STYLESHEET, "hashed stylesheet");

        assertThat(indexHtml).contains(
                "assets/" + javascript.getFileName(),
                "assets/" + stylesheet.getFileName());
    }

    @Test
    void selectsOnlyAssetsReferencedByIndexWhenOldAndNewHashesCoexist(@TempDir Path tempDirectory)
            throws IOException {
        Path assets = Files.createDirectories(tempDirectory.resolve("assets"));
        Path oldJavascript = Files.createFile(assets.resolve("index-oldhash.js"));
        Path newJavascript = Files.createFile(assets.resolve("index-newhash.js"));
        Path oldStylesheet = Files.createFile(assets.resolve("index-oldhash.css"));
        Path newStylesheet = Files.createFile(assets.resolve("index-newhash.css"));
        String indexHtml = "<script type=\"module\" src=\"assets/index-newhash.js\"></script>"
                + "<link rel=\"stylesheet\" href=\"assets/index-newhash.css\">";

        assertThat(requireAsset(List.of(oldJavascript, newJavascript), indexHtml,
                HASHED_JAVASCRIPT, "hashed JavaScript")).isEqualTo(newJavascript);
        assertThat(requireAsset(List.of(oldStylesheet, newStylesheet), indexHtml,
                HASHED_STYLESHEET, "hashed stylesheet")).isEqualTo(newStylesheet);
    }

    private static Path requireAsset(
            List<Path> assets, String indexHtml, Pattern pattern, String description) {
        return assets.stream()
                .filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                .filter(path -> indexHtml.contains("assets/" + path.getFileName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + description + " asset"));
    }
}
