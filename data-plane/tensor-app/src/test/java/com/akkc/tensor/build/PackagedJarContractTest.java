package com.akkc.tensor.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackagedJarContractTest {

    private static final Path APP_JAR = Path.of("target", "tensor-app-1.0-SNAPSHOT.jar");
    private static final String TUSHARE_PREFIX = "datasets/tushare_pro/";
    private static final Pattern HASHED_ASSET =
            Pattern.compile("^.+-[A-Za-z0-9_-]+[.](?:js|css)$");
    private static final List<String> PRODUCTION_MIGRATIONS = List.of(
            "BOOT-INF/classes/db/migration/V1__create_basic_and_organization_tables.sql",
            "BOOT-INF/classes/db/migration/V2__create_market_and_trading_tables.sql",
            "BOOT-INF/classes/db/migration/V3__create_connect_and_slb_tables.sql",
            "BOOT-INF/classes/db/migration/V4__create_financial_tables.sql",
            "BOOT-INF/classes/db/migration/V5__create_corporate_and_governance_tables.sql");
    private static final List<String> TENSOR_MODULE_JARS = List.of(
            "BOOT-INF/lib/tensor-plugin-api-1.0-SNAPSHOT.jar",
            "BOOT-INF/lib/tensor-core-1.0-SNAPSHOT.jar",
            "BOOT-INF/lib/tensor-plugin-tushare-1.0-SNAPSHOT.jar");
    private static final List<String> FORBIDDEN_ENTRIES = List.of(
            "datasets/fixture/fixture_daily.yaml",
            "datasets/invalid-duplicate-column.yaml",
            "datasets/valid-daily.yaml");

    @Test
    void packagesOnlyTheProductionExecutableJarAndItsContractedContents() throws IOException {
        List<String> appJars;
        try (Stream<Path> paths = Files.list(APP_JAR.getParent())) {
            appJars = paths.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".jar"))
                    .toList();
        }
        assertThat(appJars).containsExactly("tensor-app-1.0-SNAPSHOT.jar");
        assertThat(APP_JAR).isRegularFile();

        try (JarFile jarFile = new JarFile(APP_JAR.toFile())) {
            List<String> outerEntries = entryNames(jarFile);
            assertThat(jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS))
                    .isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
            assertThat(jarFile.getManifest().getMainAttributes().getValue("Start-Class"))
                    .isEqualTo("com.akkc.tensor.TensorApplication");
            assertThat(outerEntries).contains(
                    "org/springframework/boot/loader/launch/JarLauncher.class",
                    "BOOT-INF/classes/com/akkc/tensor/TensorApplication.class");

            assertFrontend(outerEntries, jarFile);
            assertThat(outerEntries.stream()
                    .filter(name -> name.startsWith("BOOT-INF/classes/db/migration/"))
                    .filter(name -> name.endsWith(".sql"))
                    .toList()).containsExactlyInAnyOrderElementsOf(PRODUCTION_MIGRATIONS);
            assertThat(outerEntries.stream()
                    .filter(name -> name.startsWith("BOOT-INF/lib/tensor-"))
                    .filter(name -> name.endsWith(".jar"))
                    .toList()).containsExactlyInAnyOrderElementsOf(TENSOR_MODULE_JARS);
            assertThat(outerEntries).noneMatch(name -> name.startsWith("BOOT-INF/lib/tensor-plugin-fixture-"));

            List<String> tensorEntries = new ArrayList<>();
            for (String moduleJar : TENSOR_MODULE_JARS) {
                tensorEntries.addAll(innerJarEntryNames(jarFile, moduleJar));
            }
            assertTushareResources(outerEntries, tensorEntries);
            assertExcludedResources(outerEntries);
            assertExcludedResources(tensorEntries);
            assertNoSensitiveFiles(outerEntries);
            assertNoSensitiveFiles(tensorEntries);
            assertEnvironmentPlaceholders(jarFile);
        }
    }

    private static void assertFrontend(List<String> entries, JarFile jarFile) throws IOException {
        String indexEntry = "BOOT-INF/classes/static/index.html";
        assertThat(entries).contains(indexEntry);
        String indexHtml;
        try (InputStream input = jarFile.getInputStream(jarFile.getJarEntry(indexEntry))) {
            indexHtml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> staticAssets = entries.stream()
                .filter(name -> name.startsWith("BOOT-INF/classes/static/assets/"))
                .map(name -> name.substring("BOOT-INF/classes/static/assets/".length()))
                .filter(name -> !name.contains("/"))
                .filter(name -> HASHED_ASSET.matcher(name).matches())
                .toList();
        String javascript = requireAsset(staticAssets, indexHtml, ".js");
        String stylesheet = requireAsset(staticAssets, indexHtml, ".css");
        assertThat(indexHtml).contains("assets/" + javascript, "assets/" + stylesheet);
    }

    private static String requireAsset(List<String> assets, String indexHtml, String suffix) {
        return assets.stream()
                .filter(name -> name.endsWith(suffix))
                .filter(name -> indexHtml.contains("assets/" + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing hashed " + suffix + " asset referenced by index"));
    }

    private static void assertTushareResources(List<String> outerEntries, List<String> tensorEntries) {
        List<String> outerTushare = outerEntries.stream()
                .filter(name -> name.startsWith("BOOT-INF/classes/" + TUSHARE_PREFIX))
                .filter(name -> name.endsWith(".yaml"))
                .toList();
        List<String> allTushare = new ArrayList<>(outerTushare);
        tensorEntries.stream()
                .filter(name -> name.startsWith(TUSHARE_PREFIX))
                .filter(name -> name.endsWith(".yaml"))
                .forEach(allTushare::add);

        assertThat(outerTushare).isEmpty();
        assertThat(allTushare).hasSize(49);
        assertThat(new HashSet<>(allTushare)).hasSize(49);
    }

    private static void assertExcludedResources(List<String> entries) {
        assertThat(entries).noneMatch(name -> FORBIDDEN_ENTRIES.stream().anyMatch(name::endsWith));
        assertThat(entries).noneMatch(name -> name.contains("V6__"));
        assertThat(entries).noneMatch(name -> name.contains("test-classes")
                || name.contains("surefire-reports")
                || name.contains("failsafe-reports")
                || name.endsWith("Test.class")
                || name.endsWith("IT.class"));
    }

    private static void assertNoSensitiveFiles(List<String> entries) {
        assertThat(entries).noneMatch(name -> {
            String lowercase = name.toLowerCase(Locale.ROOT);
            return lowercase.endsWith(".env")
                    || lowercase.endsWith(".pem")
                    || lowercase.endsWith(".key")
                    || lowercase.endsWith(".p12")
                    || lowercase.endsWith(".pfx")
                    || lowercase.endsWith(".jks")
                    || lowercase.endsWith(".keystore");
        });
    }

    private static void assertEnvironmentPlaceholders(JarFile jarFile) throws IOException {
        JarEntry application = jarFile.getJarEntry("BOOT-INF/classes/application.yml");
        assertThat(application).isNotNull();
        try (InputStream input = jarFile.getInputStream(application)) {
            String configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(configuration).contains(
                    "password: ${TENSOR_DB_PASSWORD}",
                    "token: ${TENSOR_TUSHARE_TOKEN:}");
        }
    }

    private static List<String> entryNames(JarFile jarFile) {
        return jarFile.stream().map(JarEntry::getName).toList();
    }

    private static List<String> innerJarEntryNames(JarFile outerJar, String innerJarName) throws IOException {
        JarEntry innerJar = outerJar.getJarEntry(innerJarName);
        assertThat(innerJar).isNotNull();
        try (InputStream input = outerJar.getInputStream(innerJar);
                JarInputStream nestedJar = new JarInputStream(input)) {
            List<String> names = new ArrayList<>();
            JarEntry entry;
            while ((entry = nestedJar.getNextJarEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        }
    }
}
