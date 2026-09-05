package com.akkc.tensor.build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptancePackagedJarContractTest {

    private static final Path PRODUCTION_JAR = Path.of("target", "tensor-app-1.0-SNAPSHOT.jar");
    private static final Path ACCEPTANCE_DIRECTORY = Path.of("target", "acceptance");
    private static final Path ACCEPTANCE_JAR =
            ACCEPTANCE_DIRECTORY.resolve("tensor-app-1.0-SNAPSHOT-acceptance.jar");
    private static final Path FIXTURE_JAR =
            Path.of("..", "tensor-plugin-fixture", "target", "tensor-plugin-fixture-1.0-SNAPSHOT.jar");
    private static final Path V6 =
            Path.of("src", "test", "resources", "db", "migration", "V6__create_fixture_tables.sql");
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String CLASSPATH_INDEX = "BOOT-INF/classpath.idx";
    private static final String LAYERS_INDEX = "BOOT-INF/layers.idx";
    private static final String FIXTURE_ENTRY =
            "BOOT-INF/lib/tensor-plugin-fixture-1.0-SNAPSHOT.jar";
    private static final String V6_ENTRY =
            "BOOT-INF/classes/db/migration/V6__create_fixture_tables.sql";
    private static final Set<String> REPLACED_PRODUCTION_ENTRIES =
            Set.of(MANIFEST, CLASSPATH_INDEX, LAYERS_INDEX);

    @Test
    void preservesProductionContentsAndAddsOnlyFixtureAndV6() throws Exception {
        assertThat(PRODUCTION_JAR).isRegularFile();
        assertThat(ACCEPTANCE_JAR).isRegularFile();
        assertThat(FIXTURE_JAR).isRegularFile();
        assertThat(V6).isRegularFile();

        Map<String, byte[]> productionContents = archiveContents(PRODUCTION_JAR);
        Map<String, byte[]> acceptanceContents = archiveContents(ACCEPTANCE_JAR);
        Set<String> expectedEntries = new HashSet<>(productionContents.keySet());
        expectedEntries.removeAll(REPLACED_PRODUCTION_ENTRIES);
        expectedEntries.addAll(Set.of(MANIFEST, FIXTURE_ENTRY, V6_ENTRY));

        assertThat(acceptanceContents.keySet()).containsExactlyInAnyOrderElementsOf(expectedEntries);
        for (Map.Entry<String, byte[]> entry : productionContents.entrySet()) {
            if (!REPLACED_PRODUCTION_ENTRIES.contains(entry.getKey())) {
                assertThat(sha256(acceptanceContents.get(entry.getKey())))
                        .as("preserved production entry %s", entry.getKey())
                        .isEqualTo(sha256(entry.getValue()));
            }
        }
        assertThat(sha256(acceptanceContents.get(FIXTURE_ENTRY)))
                .isEqualTo(sha256(Files.readAllBytes(FIXTURE_JAR)));
        assertThat(sha256(acceptanceContents.get(V6_ENTRY)))
                .isEqualTo(sha256(Files.readAllBytes(V6)));
    }

    @Test
    void retainsRunnableBootLayoutAndOnlyFixtureRuntimeResources() throws Exception {
        assertThat(ACCEPTANCE_JAR).isRegularFile();
        try (JarFile jar = new JarFile(ACCEPTANCE_JAR.toFile())) {
            List<String> entries = entryNames(jar);
            assertThat(entries).doesNotHaveDuplicates();
            Map<String, byte[]> contents = archiveContents(jar, entries);
            Attributes manifest = jar.getManifest().getMainAttributes();

            assertThat(manifest.getValue(Attributes.Name.MANIFEST_VERSION)).isEqualTo("1.0");
            assertThat(manifest.getValue(Attributes.Name.MAIN_CLASS))
                    .isEqualTo("org.springframework.boot.loader.launch.JarLauncher");
            assertThat(manifest.getValue("Start-Class"))
                    .isEqualTo("com.akkc.tensor.TensorApplication");
            assertThat(manifest.getValue("Spring-Boot-Version")).isEqualTo("3.5.16");
            assertThat(manifest.getValue("Spring-Boot-Classes")).isEqualTo("BOOT-INF/classes/");
            assertThat(manifest.getValue("Spring-Boot-Lib")).isEqualTo("BOOT-INF/lib/");
            assertThat(manifest.getValue("Tensor-Artifact-Purpose")).isEqualTo("acceptance");
            assertThat(manifest.getValue("Spring-Boot-Classpath-Index")).isNull();
            assertThat(manifest.getValue("Spring-Boot-Layers-Index")).isNull();
            assertThat(entries).contains(
                    "org/springframework/boot/loader/launch/JarLauncher.class",
                    "BOOT-INF/classes/com/akkc/tensor/TensorApplication.class",
                    "BOOT-INF/classes/application.yml",
                    FIXTURE_ENTRY,
                    V6_ENTRY);
            assertThat(entries).doesNotContain(CLASSPATH_INDEX, LAYERS_INDEX);

            List<JarEntry> libraries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().startsWith("BOOT-INF/lib/"))
                    .filter(entry -> entry.getName().endsWith(".jar"))
                    .toList();
            assertThat(libraries).isNotEmpty().allMatch(entry -> entry.getMethod() == JarEntry.STORED);

            Map<String, byte[]> fixtureContents = nestedArchiveContents(contents.get(FIXTURE_ENTRY));
            assertThat(fixtureContents.keySet()).contains(
                    "com/akkc/tensor/plugin/fixture/FixtureConfiguration.class",
                    "com/akkc/tensor/plugin/fixture/FixturePlugin.class",
                    "com/akkc/tensor/plugin/fixture/FixtureScenario.class",
                    "com/akkc/tensor/plugin/fixture/FixtureEnvelopeFactory.class",
                    "datasets/fixture/fixture_daily.yaml");
            assertThat(fixtureContents.keySet()).noneMatch(AcceptancePackagedJarContractTest::isTestOrSensitive);
            assertThat(entries).doesNotContain("BOOT-INF/classes/datasets/fixture/fixture_daily.yaml");
        }
    }

    @Test
    void keepsAcceptanceOutsideTheProductionArtifactDirectory() throws IOException {
        assertThat(ACCEPTANCE_JAR).isRegularFile();
        assertThat(jarFiles(Path.of("target"))).containsExactly("tensor-app-1.0-SNAPSHOT.jar");
        assertThat(jarFiles(ACCEPTANCE_DIRECTORY))
                .containsExactly("tensor-app-1.0-SNAPSHOT-acceptance.jar");
        assertThat(ACCEPTANCE_DIRECTORY.resolve("tensor-app-1.0-SNAPSHOT-acceptance.jar.tmp"))
                .doesNotExist();

        try (JarFile productionJar = new JarFile(PRODUCTION_JAR.toFile())) {
            List<String> productionEntries = entryNames(productionJar);
            assertThat(productionEntries).noneMatch(name -> name.contains("tensor-plugin-fixture")
                    || name.contains("V6__")
                    || name.contains("test-classes")
                    || name.contains("surefire-reports")
                    || name.contains("failsafe-reports")
                    || name.endsWith("Test.class")
                    || name.endsWith("IT.class"));
        }
    }

    private static Map<String, byte[]> archiveContents(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            List<String> entries = entryNames(jar);
            assertThat(entries).doesNotHaveDuplicates();
            return archiveContents(jar, entries);
        }
    }

    private static Map<String, byte[]> archiveContents(JarFile jar, List<String> entries) throws IOException {
        Map<String, byte[]> contents = new HashMap<>();
        for (String name : entries) {
            JarEntry entry = jar.getJarEntry(name);
            if (!entry.isDirectory()) {
                try (InputStream input = jar.getInputStream(entry)) {
                    contents.put(name, input.readAllBytes());
                }
            }
        }
        return contents;
    }

    private static Map<String, byte[]> nestedArchiveContents(byte[] bytes) throws IOException {
        Map<String, byte[]> contents = new HashMap<>();
        List<String> entries = new ArrayList<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            if (jar.getManifest() != null) {
                entries.add(MANIFEST);
            }
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                entries.add(entry.getName());
                if (!entry.isDirectory()) {
                    contents.put(entry.getName(), jar.readAllBytes());
                }
            }
        }
        assertThat(entries).doesNotHaveDuplicates();
        return contents;
    }

    private static List<String> entryNames(JarFile jar) {
        return jar.stream().map(JarEntry::getName).toList();
    }

    private static List<String> jarFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".jar"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isTestOrSensitive(String name) {
        String lowercase = name.toLowerCase(Locale.ROOT);
        return name.contains("test-classes")
                || name.contains("surefire-reports")
                || name.contains("failsafe-reports")
                || name.endsWith("Test.class")
                || name.endsWith("IT.class")
                || lowercase.endsWith(".env")
                || lowercase.endsWith(".pem")
                || lowercase.endsWith(".key")
                || lowercase.endsWith(".p12")
                || lowercase.endsWith(".pfx")
                || lowercase.endsWith(".jks")
                || lowercase.endsWith(".keystore");
    }

    private static byte[] sha256(byte[] contents) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(contents);
    }
}
