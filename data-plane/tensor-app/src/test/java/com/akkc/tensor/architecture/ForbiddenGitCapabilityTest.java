package com.akkc.tensor.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenGitCapabilityTest {

    private static final String MAVEN_PROJECT_DIRECTORY = "maven.multiModuleProjectDirectory";
    private static final List<String> PACKAGE_MARKERS = List.of(
            "org.eclipse.jgit",
            "org.kohsuke.github",
            "org.gitlab4j.api",
            "com.cdancy.bitbucket",
            "io.github.cdancy.bitbucket",
            "com.atlassian.bitbucket");
    private static final Set<String> TEXT_SUFFIXES = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".properties", ".json", ".sql", ".sh", ".bat", ".cmd", ".ps1");
    private static final Pattern PROCESS_BUILDER = Pattern.compile(
            "(?s)\\bnew\\s+ProcessBuilder\\s*\\(\\s*(?:List\\.of\\s*\\(\\s*)?\"git\"");
    private static final Pattern RUNTIME_EXEC = Pattern.compile(
            "(?s)\\bRuntime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec\\s*\\(\\s*\"git(?:\\s|\")");
    private static final Pattern SCRIPT_GIT = Pattern.compile(
            "^(?!\\s*(?:#|//|REM\\b|::))\\s*(?:exec\\s+)?git(?:\\s|$)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    @Test
    void rejects_git_capabilities_in_production_sources() throws IOException {
        Path root = scannerRoot();

        assertThat(Files.readString(root.resolve("pom.xml"), StandardCharsets.UTF_8))
                .contains("<artifactId>data-plane</artifactId>");
        assertThat(root.resolve("tensor-app/pom.xml")).isRegularFile();
        assertThat(findViolations(root)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("forbiddenExamples")
    void rejects_each_forbidden_capability(String fileName, String text, @TempDir Path root) throws IOException {
        Path source = root.resolve("module/src/main").resolve(fileName);
        Files.createDirectories(source.getParent());
        Files.writeString(source, text, StandardCharsets.UTF_8);

        assertThat(findViolations(root)).isNotEmpty();
    }

    static Stream<Arguments> forbiddenExamples() {
        return Stream.of(
                Arguments.of("java/Example.java", "org.eclipse.jgit"),
                Arguments.of("java/Example.java", "org.kohsuke.github"),
                Arguments.of("java/Example.java", "org.gitlab4j.api"),
                Arguments.of("java/Example.java", "com.cdancy.bitbucket"),
                Arguments.of("java/Example.java", "io.github.cdancy.bitbucket"),
                Arguments.of("java/Example.java", "com.atlassian.bitbucket"),
                Arguments.of("java/Example.java", "new ProcessBuilder(\"git\", \"status\")"),
                Arguments.of("java/Example.java", "new ProcessBuilder(List.of(\"git\", \"status\"))"),
                Arguments.of("java/Example.java", "Runtime.getRuntime().exec(\"git status\")"),
                Arguments.of("resources/example.sh", "git clone https://example.invalid/repository"));
    }

    @Test
    void allows_ordinary_git_text_and_non_git_processes(@TempDir Path root) throws IOException {
        String projectDirectory = System.getProperty(MAVEN_PROJECT_DIRECTORY);
        Path source = root.resolve("module/src/main/java/Example.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "String description = \"git branch policy\"; new ProcessBuilder(\"java\", \"-version\");", StandardCharsets.UTF_8);
        Path ignoredSource = root.resolve("module/src/main/generated/Example.java");
        Files.createDirectories(ignoredSource.getParent());
        Files.writeString(ignoredSource, "org.eclipse.jgit", StandardCharsets.UTF_8);

        assertThat(findViolations(root)).isEmpty();
        assertThat(System.getProperty(MAVEN_PROJECT_DIRECTORY)).isEqualTo(projectDirectory);
    }

    private static Path scannerRoot() {
        String configuredRoot = System.getProperty(MAVEN_PROJECT_DIRECTORY);
        Path root = configuredRoot == null ? dataPlaneRoot()
                : Path.of(System.getProperty("maven.multiModuleProjectDirectory"));
        if (!isDataPlaneRoot(root)) {
            throw new IllegalStateException("Maven project directory is not the data-plane reactor root");
        }
        return root;
    }

    private static Path dataPlaneRoot() {
        for (Path directory = classOutputDirectory(); directory != null; directory = directory.getParent()) {
            if (isDataPlaneRoot(directory)) {
                return directory;
            }
        }
        throw new IllegalStateException("Cannot locate the data-plane reactor root");
    }

    private static Path classOutputDirectory() {
        try {
            return Path.of(ForbiddenGitCapabilityTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot locate test classes", exception);
        }
    }

    private static boolean isDataPlaneRoot(Path directory) {
        try {
            return Files.readString(directory.resolve("pom.xml"), StandardCharsets.UTF_8)
                    .contains("<artifactId>data-plane</artifactId>")
                    && Files.readString(directory.resolve("tensor-app/pom.xml"), StandardCharsets.UTF_8)
                    .contains("<artifactId>tensor-app</artifactId>");
        } catch (IOException exception) {
            return false;
        }
    }

    private static List<String> findViolations(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(ForbiddenGitCapabilityTest::isProductionText)
                    .flatMap(path -> violations(root, path).stream())
                    .toList();
        }
    }

    private static boolean isProductionText(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/target/")
                && !normalized.contains("/src/test/")
                && (normalized.contains("/src/main/java/") || normalized.contains("/src/main/resources/"))
                && TEXT_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    private static List<String> violations(Path root, Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String suffix = path.toString().substring(path.toString().lastIndexOf('.'));
            String relativePath = root.relativize(path).toString();
            Stream<String> packageViolations = PACKAGE_MARKERS.stream()
                    .filter(text::contains)
                    .map(marker -> relativePath + ": forbidden package marker " + marker);
            Stream<String> javaViolations = ".java".equals(suffix)
                    ? Stream.concat(
                            violation(relativePath, "ProcessBuilder git command", PROCESS_BUILDER.matcher(text).find()),
                            violation(relativePath, "Runtime.exec git command", RUNTIME_EXEC.matcher(text).find()))
                    : Stream.empty();
            Stream<String> scriptViolations = isScript(suffix)
                    ? violation(relativePath, "git script command", SCRIPT_GIT.matcher(text).find())
                    : Stream.empty();
            return Stream.of(packageViolations, javaViolations, scriptViolations)
                    .flatMap(stream -> stream)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + root.relativize(path), exception);
        }
    }

    private static Stream<String> violation(String path, String rule, boolean matched) {
        return matched ? Stream.of(path + ": " + rule) : Stream.empty();
    }

    private static boolean isScript(String suffix) {
        return Set.of(".sh", ".bat", ".cmd", ".ps1").contains(suffix);
    }
}
