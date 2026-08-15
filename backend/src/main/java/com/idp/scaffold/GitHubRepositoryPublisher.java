package com.idp.scaffold;

import com.idp.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the GitHub repository a scaffolded project claims, and pushes the project
 * into it.
 *
 * <p>Before this existed the scaffolder wrote {@code https://github.com/<org>/<name>}
 * into the catalogue without creating anything, so the Golden Path ended at a link to
 * a repository that did not exist. The rendered tree and the downloadable artifact
 * were always real; only the publish step was missing.
 *
 * <p><strong>Opt-in by design.</strong> Creating repositories is an outward-facing,
 * hard-to-undo side effect, so it stays off until {@code idp.scaffold.github.enabled}
 * is set together with a token. When it is off, {@link #publish} returns empty and
 * the caller records no repository URL — the scaffolder says the project was not
 * published rather than pointing at a repository nobody created.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubRepositoryPublisher {

    private final GitRepositoryPusher pusher;
    private final AuditService auditService;

    /** Where a published project ended up. */
    public record PublishedRepository(String htmlUrl, String cloneUrl, String commitId, String branch) {
    }

    @Value("${idp.scaffold.github.enabled:false}")
    private boolean enabled;

    @Value("${idp.scaffold.github.token:}")
    private String token;

    /** Organisation to create under. Blank creates under the authenticated user. */
    @Value("${idp.scaffold.github.org:}")
    private String organisation;

    @Value("${idp.scaffold.github.api-url:https://api.github.com}")
    private String apiUrl;

    /** Scaffolded repositories default to private; a team opts into public. */
    @Value("${idp.scaffold.github.private:true}")
    private boolean privateRepository;

    @Value("${idp.scaffold.github.branch:main}")
    private String defaultBranch;

    /**
     * True only when the feature is switched on <em>and</em> a token is present.
     * Enabling without a credential is a misconfiguration, not a request to try
     * anonymously — GitHub would reject it and the pipeline would fail late.
     */
    public boolean isEnabled() {
        return enabled && token != null && !token.isBlank();
    }

    /** Human-readable reason publishing is unavailable, for the job's step log. */
    public String disabledReason() {
        if (!enabled) {
            return "GitHub publishing is disabled (idp.scaffold.github.enabled=false)";
        }
        return "GitHub publishing is enabled but no token is configured "
                + "(idp.scaffold.github.token)";
    }

    /**
     * @return where the project was published, or empty when publishing is disabled
     * @throws org.springframework.web.client.RestClientException if repository
     *         creation is rejected — a scaffold that cannot publish must fail loudly
     *         rather than silently register a service pointing nowhere
     */
    public Optional<PublishedRepository> publish(Path projectDir, String repoName,
                                                 String description, String actor) {
        if (!isEnabled()) {
            log.info("[SCAFFOLD] Skipping GitHub publish for {} — {}", repoName, disabledReason());
            return Optional.empty();
        }

        Map<String, Object> created = createRepository(repoName, description);
        String cloneUrl = String.valueOf(created.get("clone_url"));
        String htmlUrl = String.valueOf(created.get("html_url"));

        try {
            GitRepositoryPusher.PushResult push = pusher.publish(
                    projectDir,
                    cloneUrl,
                    defaultBranch,
                    "chore: scaffold " + repoName + " from the IDP golden path",
                    pusher.tokenCredentials(token));

            auditService.logAction(actor, "SCAFFOLD_REPOSITORY_PUBLISHED", repoName,
                    "Created " + htmlUrl + " and pushed initial commit " + push.commitId()
                            + " to " + push.branch());

            return Optional.of(new PublishedRepository(htmlUrl, cloneUrl, push.commitId(), push.branch()));
        } catch (Exception e) {
            // The repository now exists but is empty. Say so — the operator needs to
            // know there is a stray repo to clean up before retrying the scaffold.
            auditService.logAction(actor, "SCAFFOLD_REPOSITORY_PUSH_FAILED", repoName,
                    "Created " + htmlUrl + " but the initial push failed: " + e.getMessage());
            throw new IllegalStateException("Created " + htmlUrl
                    + " but failed to push the initial commit; the repository exists and is empty", e);
        }
    }

    private Map<String, Object> createRepository(String repoName, String description) {
        boolean underOrg = organisation != null && !organisation.isBlank();
        String endpoint = underOrg ? apiUrl + "/orgs/" + organisation + "/repos" : apiUrl + "/user/repos";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", repoName);
        body.put("description", description == null || description.isBlank()
                ? "Scaffolded by the IDP golden path" : description);
        body.put("private", privateRepository);
        // The push provides the initial commit; letting GitHub seed a README first
        // would make the remote non-empty and reject a plain push.
        body.put("auto_init", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> created = RestClient.create().post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .body(body)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.value() == 422) {
                        throw new IllegalStateException("GitHub rejected repository '" + repoName
                                + "': it already exists, or the name violates a policy. "
                                + "Refusing to push into a repository this scaffold did not create.");
                    }
                    if (status.isError()) {
                        throw new IllegalStateException("GitHub repository creation failed with HTTP "
                                + status.value() + " — check the token's scopes and the target owner.");
                    }
                    return response.bodyTo(Map.class);
                });

        if (created == null || created.get("clone_url") == null) {
            throw new IllegalStateException("GitHub returned no clone_url for '" + repoName + "'");
        }
        return created;
    }
}
