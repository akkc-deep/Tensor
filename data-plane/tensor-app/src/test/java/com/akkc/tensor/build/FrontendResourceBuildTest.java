package com.akkc.tensor.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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
        Path javascript = requireAsset(assetFiles, HASHED_JAVASCRIPT, "hashed JavaScript");
        Path stylesheet = requireAsset(assetFiles, HASHED_STYLESHEET, "hashed stylesheet");
        String indexHtml = Files.readString(index, StandardCharsets.UTF_8);

        assertThat(indexHtml).contains(
                "assets/" + javascript.getFileName(),
                "assets/" + stylesheet.getFileName());
    }

    private static Path requireAsset(List<Path> assets, Pattern pattern, String description) {
        return assets.stream()
                .filter(path -> pattern.matcher(path.getFileName().toString()).matches())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + description + " asset"));
    }
}
