# M13-T01 Frontend Resource Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Maven deterministically install, test, and build the Vue frontend, then copy its index and hashed assets into `tensor-app/target/generated-resources/static`.

**Architecture:** `frontend-maven-plugin` owns a project-local Node/npm toolchain and runs the lockfile-based frontend pipeline during `generate-resources`. A following `maven-resources-plugin` execution copies the fresh Vite output, while one filesystem-only JUnit contract verifies the copied entry point and its hashed JS/CSS references.

**Tech Stack:** Maven, frontend-maven-plugin 1.15.4, maven-resources-plugin 3.4.0, Node.js v24.15.0, npm 11.12.1, Vite 8.2.2, Vitest 4.1.11, Java 21, JUnit 5, AssertJ.

## Global Constraints

- Work only on board task `M13-T01` from `docs/task-handoffs/tensor-v1-task-board.md` and follow `docs/task-designs/M13-T01-design.md`.
- Modify only `data-plane/tensor-app/pom.xml` and create only `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`.
- Use frontend-maven-plugin 1.15.4 with Node v24.15.0 and npm 11.12.1 installed under `${project.build.directory}/frontend`; never fall back to system Node/npm.
- Run `npm ci`, all frontend unit tests, Vite build, and resource copy in that order during `generate-resources`; any failed step stops the lifecycle.
- Copy unfiltered output from `control-plane/dist` to `${project.build.directory}/generated-resources/static` with maven-resources-plugin 3.4.0.
- Do not read Git branch, commit, status, repository directory, or Git environment metadata from Maven or Java code.
- Do not change or commit `control-plane/dist`, `node_modules`, Maven `target`, package metadata, frontend source/configuration, the root POM, JAR packaging, production Web behavior, or runbook content.
- Preserve unrelated existing `.idea` and generated `target` changes; stage only the two implementation files.
- Do not commit the failing RED checkpoint. The single implementation commit message is exactly `build: integrate frontend assets into app`.

---

### Task 1: Generate and verify frontend resources during the Maven lifecycle

**Files:**
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`
- Modify: `data-plane/tensor-app/pom.xml`
- Test: `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`

**Interfaces:**
- Consumes: committed `control-plane/package-lock.json`; npm scripts `test:unit` and `build`; Vite output at `control-plane/dist`.
- Produces: `data-plane/tensor-app/target/generated-resources/static/index.html` and copied files below `static/assets/`; `FrontendResourceBuildTest` proves that index references at least one hashed JS and one hashed CSS asset.

- [ ] **Step 1: Reconfirm the clean task boundary and RED precondition**

Run from the repository root:

```bash
git status --short --untracked-files=all
test ! -e data-plane/tensor-app/target/generated-resources/static
git diff -- data-plane/tensor-app/pom.xml control-plane/package.json \
  control-plane/package-lock.json control-plane/vite.config.js \
  control-plane/vitest.config.js control-plane/src data-plane/pom.xml
```

Expected: status may show the user's unrelated `.idea` and generated `target` paths; the static generated-resource precondition exits 0; the protected-path diff is empty. Stop if either implementation file already has an unaccounted-for change.

- [ ] **Step 2: Write the complete failing resource contract test**

Create `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java` with exactly this implementation:

```java
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
```

Do not modify the POM yet and do not stage or commit this RED checkpoint.

- [ ] **Step 3: Run the targeted test and verify the intended RED**

Run:

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=FrontendResourceBuildTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: upstream modules with no matching test continue; `tensor-app` compiles the new test, runs it, and fails because `target/generated-resources/static/index.html` is not a regular file. There must be no compilation, module-resolution, Spring, database, business-network, or wrong-test-selection failure.

- [ ] **Step 4: Add the minimal deterministic frontend pipeline**

Append the following `<build>` block after `</dependencies>` and before `</project>` in `data-plane/tensor-app/pom.xml`:

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>com.github.eirslett</groupId>
                <artifactId>frontend-maven-plugin</artifactId>
                <version>1.15.4</version>
                <configuration>
                    <workingDirectory>${project.basedir}/../../control-plane</workingDirectory>
                    <installDirectory>${project.build.directory}/frontend</installDirectory>
                </configuration>
                <executions>
                    <execution>
                        <id>install-frontend-toolchain</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>install-node-and-npm</goal>
                        </goals>
                        <configuration>
                            <nodeVersion>v24.15.0</nodeVersion>
                            <npmVersion>11.12.1</npmVersion>
                        </configuration>
                    </execution>
                    <execution>
                        <id>npm-ci</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>npm</goal>
                        </goals>
                        <configuration>
                            <arguments>ci</arguments>
                        </configuration>
                    </execution>
                    <execution>
                        <id>frontend-unit-tests</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>npm</goal>
                        </goals>
                        <configuration>
                            <arguments>run test:unit -- --run</arguments>
                        </configuration>
                    </execution>
                    <execution>
                        <id>frontend-production-build</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>npm</goal>
                        </goals>
                        <configuration>
                            <arguments>run build</arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.4.0</version>
                <executions>
                    <execution>
                        <id>copy-frontend-resources</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.directory}/generated-resources/static</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>${project.basedir}/../../control-plane/dist</directory>
                                    <filtering>false</filtering>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

Keep the frontend plugin before the resources plugin. Do not add profiles, skip flags, Git probes, system executable detection, resource filtering, or changes outside this block.

- [ ] **Step 5: Run the targeted test and verify GREEN plus execution order**

Run the same approved command:

```bash
mvn -f data-plane/pom.xml -pl tensor-app -am \
  -Dtest=FrontendResourceBuildTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected Maven log sequence in `tensor-app`:

```text
frontend:1.15.4:install-node-and-npm (install-frontend-toolchain)
frontend:1.15.4:npm (npm-ci)
frontend:1.15.4:npm (frontend-unit-tests)
20 test files / 120 tests passed
frontend:1.15.4:npm (frontend-production-build)
vite v8.2.2 build succeeds
resources:3.4.0:copy-resources (copy-frontend-resources)
FrontendResourceBuildTest: 1 test, 0 failures, 0 errors
BUILD SUCCESS
```

The first run may download the pinned toolchain and lockfile dependencies. If sandboxed network access blocks a required download, request approval for the same Maven command; never change versions, registry semantics, or fall back to system Node/npm.

- [ ] **Step 6: Inspect the generated contract and protected frontend inputs**

Run:

```bash
find data-plane/tensor-app/target/generated-resources/static \
  -maxdepth 2 -type f -print | sort
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js control-plane/src \
  data-plane/pom.xml
```

Expected: output includes `static/index.html`, at least one `static/assets/*-<hash>.js`, and at least one `static/assets/*-<hash>.css`; it may also include `favicon.svg`. The protected-path diff is empty, proving `npm ci` did not modify the lockfile.

- [ ] **Step 7: Run the complete Maven regression**

Run:

```bash
mvn -f data-plane/pom.xml test
```

Expected: the complete reactor exits 0. In `tensor-app`, the pinned frontend pipeline again runs before Java tests; the frontend remains 20 files / 120 tests passing, Vite build succeeds with only the established Element Plus chunk-size warning, resource copy succeeds, and all default backend tests including `FrontendResourceBuildTest` pass.

- [ ] **Step 8: Run formatting, scope, and forbidden-Git gates**

Run:

```bash
git diff --check
git status --short --untracked-files=all -- \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js control-plane/src \
  data-plane/pom.xml
rg -n 'git[[:space:]]+(branch|rev-parse|status|log)|[.]git|GIT_(DIR|COMMON|BRANCH|COMMIT)' \
  data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
```

Expected: `git diff --check` exits 0; scoped status shows exactly one modified POM and one untracked Java test; protected paths have no diff; the forbidden-Git scan has no output and exits 1. Ignore but preserve the user's unrelated `.idea` and generated `target` status.

- [ ] **Step 9: Stage exactly the implementation files and commit**

Run:

```bash
git add data-plane/tensor-app/pom.xml \
  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
git diff --cached --check
git diff --cached --name-status
```

Expected staged names and statuses:

```text
M  data-plane/tensor-app/pom.xml
A  data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java
```

Commit:

```bash
git commit -m "build: integrate frontend assets into app"
```

Expected: one commit containing exactly the two implementation files. Confirm with `git show --stat --oneline HEAD`; do not stage generated output, plan/design/board/handoff files, or unrelated user changes.
