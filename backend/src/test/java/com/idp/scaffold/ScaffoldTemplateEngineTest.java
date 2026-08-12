package com.idp.scaffold;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTemplateEngineTest {

    private final ScaffoldTemplateEngine engine = new ScaffoldTemplateEngine();

    private Map<String, String> baseVariables(String name, boolean enablePostgres) {
        Map<String, String> variables = new LinkedHashMap<>();
        String className = "PaymentGateway";
        String packageName = "paymentgateway";
        String repoName = "payment-gateway";
        variables.put("projectName", className);
        variables.put("packageName", packageName);
        variables.put("repoName", repoName);
        variables.put("description", "Enterprise Payment Gateway Service");
        variables.put("ownerTeam", "Equipe Paiement");

        if (enablePostgres) {
            variables.put("postgresBlock", "\nDATABASE_URL=postgresql://app:change-me@localhost:5432/" + repoName);
            variables.put("postgresConfig", "\n  datasource:\n    url: jdbc:postgresql://localhost:5432/" + repoName);
            variables.put("postgresDependency", "\n    <dependency>\n      <groupId>org.postgresql</groupId>\n      <artifactId>postgresql</artifactId>\n    </dependency>\n");
        } else {
            variables.put("postgresBlock", "");
            variables.put("postgresConfig", "");
            variables.put("postgresDependency", "");
        }
        return variables;
    }

    @Test
    @DisplayName("availableTemplates returns the 4 standardized golden path stacks")
    void availableTemplates() {
        var templates = engine.availableTemplates();
        assertThat(templates).hasSize(4);
        assertThat(templates).extracting(ScaffoldTemplateEngine.TemplateInfo::id)
                .containsExactly("SPRING_BOOT", "ANGULAR", "GO", "PYTHON");
    }

    @Test
    @DisplayName("renders SPRING_BOOT stack with build, linter, Dockerfile, CI/CD, and passes quality gate")
    void rendersSpringBootStack(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("PaymentGateway", true);
        Path targetDir = tempDir.resolve("spring-service");

        var result = engine.render("SPRING_BOOT", vars, true, targetDir);

        assertThat(result.fileCount()).isGreaterThanOrEqualTo(6);
        assertThat(targetDir.resolve("pom.xml")).exists();
        assertThat(targetDir.resolve("checkstyle.xml")).exists();
        assertThat(targetDir.resolve("Dockerfile")).exists();
        assertThat(targetDir.resolve(".env.example")).exists();
        assertThat(targetDir.resolve(".github/workflows/deploy.yml")).exists();
        assertThat(targetDir.resolve("src/main/resources/application.yml")).exists();
        assertThat(targetDir.resolve("src/main/java/com/paymentgateway/PaymentGatewayApplication.java")).exists();

        // Check variable substitution
        String pomContent = Files.readString(targetDir.resolve("pom.xml"));
        assertThat(pomContent).contains("<artifactId>payment-gateway</artifactId>");
        assertThat(pomContent).doesNotContain("{{repoName}}");

        String javaContent = Files.readString(targetDir.resolve("src/main/java/com/paymentgateway/PaymentGatewayApplication.java"));
        assertThat(javaContent).contains("package com.paymentgateway;");
        assertThat(javaContent).contains("public class PaymentGatewayApplication");

        // Validate quality gate
        var validation = engine.validate("SPRING_BOOT", targetDir, true, vars);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    @DisplayName("renders ANGULAR stack with build, ESLint, Dockerfile, and passes quality gate")
    void rendersAngularStack(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("PaymentPortal", false);
        Path targetDir = tempDir.resolve("angular-app");

        var result = engine.render("ANGULAR", vars, true, targetDir);

        assertThat(result.fileCount()).isGreaterThanOrEqualTo(6);
        assertThat(targetDir.resolve("package.json")).exists();
        assertThat(targetDir.resolve("angular.json")).exists();
        assertThat(targetDir.resolve("eslint.config.mjs")).exists();
        assertThat(targetDir.resolve("Dockerfile")).exists();
        assertThat(targetDir.resolve(".env.example")).exists();
        assertThat(targetDir.resolve("src/app/app.component.ts")).exists();
        assertThat(targetDir.resolve(".github/workflows/deploy.yml")).exists();

        var validation = engine.validate("ANGULAR", targetDir, true, vars);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    @DisplayName("renders GO stack with go.mod, golangci-lint, Dockerfile, and passes quality gate")
    void rendersGoStack(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("PaymentGo", false);
        Path targetDir = tempDir.resolve("go-service");

        var result = engine.render("GO", vars, true, targetDir);

        assertThat(result.fileCount()).isGreaterThanOrEqualTo(6);
        assertThat(targetDir.resolve("go.mod")).exists();
        assertThat(targetDir.resolve(".golangci.yml")).exists();
        assertThat(targetDir.resolve("Dockerfile")).exists();
        assertThat(targetDir.resolve(".env.example")).exists();
        assertThat(targetDir.resolve("cmd/api/main.go")).exists();
        assertThat(targetDir.resolve(".github/workflows/deploy.yml")).exists();

        String goMod = Files.readString(targetDir.resolve("go.mod"));
        assertThat(goMod).contains("module github.com/payment-gateway");

        var validation = engine.validate("GO", targetDir, true, vars);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    @DisplayName("renders PYTHON stack with pyproject.toml, ruff.toml, pytest tests, and passes quality gate")
    void rendersPythonStack(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("PaymentAI", false);
        Path targetDir = tempDir.resolve("python-service");

        var result = engine.render("PYTHON", vars, true, targetDir);

        assertThat(result.fileCount()).isGreaterThanOrEqualTo(6);
        assertThat(targetDir.resolve("pyproject.toml")).exists();
        assertThat(targetDir.resolve("ruff.toml")).exists();
        assertThat(targetDir.resolve("Dockerfile")).exists();
        assertThat(targetDir.resolve(".env.example")).exists();
        assertThat(targetDir.resolve("src/paymentgateway/main.py")).exists();
        assertThat(targetDir.resolve("tests/test_health.py")).exists();
        assertThat(targetDir.resolve(".github/workflows/deploy.yml")).exists();

        var validation = engine.validate("PYTHON", targetDir, true, vars);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    @DisplayName("CI/CD pipeline files are omitted when enableCiCd is false")
    void ciCdDisabledOmission(@TempDir Path tempDir) {
        Map<String, String> vars = baseVariables("SimpleApp", false);
        Path targetDir = tempDir.resolve("no-cicd-app");

        engine.render("SPRING_BOOT", vars, false, targetDir);

        assertThat(targetDir.resolve(".github")).doesNotExist();

        // Validation with enableCiCd=false should pass
        var validation = engine.validate("SPRING_BOOT", targetDir, false, vars);
        assertThat(validation.valid()).isTrue();

        // Validation expecting CI/CD should report missing deploy.yml
        var validationStrict = engine.validate("SPRING_BOOT", targetDir, true, vars);
        assertThat(validationStrict.valid()).isFalse();
        assertThat(validationStrict.issues()).contains("missing .github/workflows/deploy.yml");
    }

    @Test
    @DisplayName("output quality gate fails if required linter or directory is missing")
    void outputQualityGateFailsOnTamperedTree(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("TamperedApp", false);
        Path targetDir = tempDir.resolve("tampered");

        engine.render("SPRING_BOOT", vars, true, targetDir);

        // Delete checkstyle.xml
        Files.delete(targetDir.resolve("checkstyle.xml"));

        var validation = engine.validate("SPRING_BOOT", targetDir, true, vars);
        assertThat(validation.valid()).isFalse();
        assertThat(validation.issues()).contains("missing checkstyle.xml");
    }

    @Test
    @DisplayName("zip packages project tree into a valid archive")
    void zipPackagesDirectory(@TempDir Path tempDir) throws IOException {
        Map<String, String> vars = baseVariables("ZipApp", true);
        Path targetDir = tempDir.resolve("zip-project");
        engine.render("SPRING_BOOT", vars, true, targetDir);

        Path zipFile = tempDir.resolve("artifact.zip");
        Path resultZip = engine.zip(targetDir, zipFile);

        assertThat(resultZip).exists();
        assertThat(Files.size(resultZip)).isGreaterThan(0);

        try (ZipFile zf = new ZipFile(resultZip.toFile())) {
            assertThat(zf.getEntry("pom.xml")).isNotNull();
            assertThat(zf.getEntry("checkstyle.xml")).isNotNull();
            assertThat(zf.getEntry(".github/workflows/deploy.yml")).isNotNull();
        }
    }

    @Test
    @DisplayName("rendering unknown stack throws IllegalArgumentException")
    void unknownStackThrows(@TempDir Path tempDir) {
        assertThatThrownBy(() -> engine.render("UNKNOWN_STACK", Map.of(), true, tempDir.resolve("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown stack");
    }
}
