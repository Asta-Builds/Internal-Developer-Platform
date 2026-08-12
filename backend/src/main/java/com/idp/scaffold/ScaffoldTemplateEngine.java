package com.idp.scaffold;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Golden-path template engine.
 *
 * <p>Templates live under {@code classpath:scaffold-templates/{STACK}/} and are
 * rendered into a real project tree: source skeleton, pre-configured build,
 * linter config, Dockerfile, CI/CD workflow and environment settings. Files
 * ending in {@code .tpl} are rendered with {@code {{variable}}} substitution
 * (also in path segments); files under {@code .github/} are only emitted when
 * CI/CD is enabled.
 *
 * <p>{@link #validate} is the automatic output-quality gate: every rendered
 * project must contain its build definition, linter configuration, container
 * runtime, environment settings and the expected directory structure, or the
 * pipeline fails the job.
 */
@Component
public class ScaffoldTemplateEngine {

    public static final List<String> STACKS = List.of("SPRING_BOOT", "ANGULAR", "GO", "PYTHON");

    /** Linter/build/env markers that every rendered project must ship with. */
    public static final Map<String, List<String>> REQUIRED_FILES = Map.of(
            "SPRING_BOOT", List.of("pom.xml", "checkstyle.xml", "Dockerfile", ".env.example", "src/main/resources/application.yml"),
            "ANGULAR", List.of("package.json", "angular.json", "eslint.config.mjs", "Dockerfile", ".env.example", "src/app/app.component.ts"),
            "GO", List.of("go.mod", ".golangci.yml", "Dockerfile", ".env.example", "cmd/api/main.go"),
            "PYTHON", List.of("pyproject.toml", "ruff.toml", "Dockerfile", ".env.example", "src/{{packageName}}/main.py"));

    /** Directory structure markers per stack (standard layout guarantee). */
    public static final Map<String, List<String>> REQUIRED_DIRS = Map.of(
            "SPRING_BOOT", List.of("src/main/java", "src/main/resources"),
            "ANGULAR", List.of("src/app"),
            "GO", List.of("cmd"),
            "PYTHON", List.of("src", "tests"));

    public record TemplateInfo(String id, String name, String description) {
    }

    public record RenderResult(Path projectDir, int fileCount) {
    }

    public record ValidationResult(boolean valid, List<String> issues) {
    }

    private static final String TEMPLATE_ROOT = "scaffold-templates/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<TemplateInfo> availableTemplates() {
        return STACKS.stream().map(stack -> {
            String name;
            String description;
            switch (stack) {
                case "SPRING_BOOT" -> {
                    name = "Spring Boot + PostgreSQL Microservice";
                    description = "Standard enterprise REST API stack with Maven build, Checkstyle gate, multi-stage Dockerfile and GitHub Actions pipeline";
                }
                case "ANGULAR" -> {
                    name = "Angular SPA Frontend";
                    description = "Standalone Angular 17 web app with ESLint, pre-configured build and containerised deployment";
                }
                case "GO" -> {
                    name = "Go Microservice (stdlib / Gin)";
                    description = "Lightweight HTTP service with golangci-lint, Go modules and multi-stage build";
                }
                case "PYTHON" -> {
                    name = "Python FastAPI + Asyncpg";
                    description = "Async Python REST API with Ruff linting, pytest, and Dockerised runtime";
                }
                default -> throw new IllegalArgumentException("Unknown stack: " + stack);
            }
            return new TemplateInfo(stack, name, description);
        }).toList();
    }

    /**
     * Renders the golden-path template for {@code stack} into {@code targetDir}.
     *
     * @param variables placeholder values, e.g. projectName, packageName, repoName
     * @param enableCiCd   whether {@code .github/} pipeline files are emitted
     */
    public RenderResult render(String stack, Map<String, String> variables, boolean enableCiCd, Path targetDir) {
        if (!STACKS.contains(stack)) {
            throw new IllegalArgumentException("Unknown stack: " + stack);
        }
        try {
            Resource[] resources = resolver.getResources("classpath*:" + TEMPLATE_ROOT + stack + "/**");
            int count = 0;
            String basePrefix = TEMPLATE_ROOT + stack + "/";
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String resourcePath = resource.getURL().toExternalForm();
                int marker = resourcePath.indexOf(basePrefix);
                String relative = resourcePath.substring(marker + basePrefix.length());
                relative = java.net.URLDecoder.decode(relative, StandardCharsets.UTF_8);
                if (relative.isBlank()) {
                    continue;
                }
                if (relative.startsWith(".github/") && !enableCiCd) {
                    continue;
                }
                String renderedPath = render(relative, variables);
                if (renderedPath.endsWith(".tpl")) {
                    renderedPath = renderedPath.substring(0, renderedPath.length() - 4);
                }
                Path target = targetDir.resolve(renderedPath).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IllegalArgumentException("Template path escapes root: " + relative);
                }
                Files.createDirectories(target.getParent());
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(target, render(content, variables), StandardCharsets.UTF_8);
                count++;
            }
            return new RenderResult(targetDir, count);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render template " + stack, e);
        }
    }

    /**
     * Output-quality gate: asserts build definition, linter config, container
     * runtime, environment settings and directory structure are all present.
     */
    public ValidationResult validate(String stack, Path projectDir, boolean enableCiCd, Map<String, String> variables) {
        List<String> issues = new ArrayList<>();
        for (String required : REQUIRED_FILES.get(stack)) {
            String path = render(required, variables);
            if (!Files.exists(projectDir.resolve(path))) {
                issues.add("missing " + path);
            }
        }
        if (enableCiCd && !Files.exists(projectDir.resolve(".github/workflows/deploy.yml"))) {
            issues.add("missing .github/workflows/deploy.yml");
        }
        for (String dir : REQUIRED_DIRS.get(stack)) {
            if (!Files.isDirectory(projectDir.resolve(dir))) {
                issues.add("missing directory " + dir);
            }
        }
        return new ValidationResult(issues.isEmpty(), issues);
    }

    /** Packages the rendered project into a zip artifact. */
    public Path zip(Path projectDir, Path artifactFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(artifactFile);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            try (var walk = Files.walk(projectDir)) {
                var files = walk.filter(Files::isRegularFile).sorted().toList();
                for (Path file : files) {
                    String entryName = projectDir.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }
        return artifactFile;
    }

    private static String render(String template, Map<String, String> variables) {
        String out = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            out = out.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return out;
    }
}