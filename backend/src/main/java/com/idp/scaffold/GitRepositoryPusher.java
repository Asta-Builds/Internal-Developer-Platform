package com.idp.scaffold;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * Turns a rendered project directory into an initial commit on a remote.
 *
 * <p>Split out from {@link GitHubRepositoryPublisher} so the git half can be tested
 * against a local bare repository over {@code file://} — the push path is the part
 * most likely to break, and it should not need a network or a token to verify.
 */
@Component
@Slf4j
public class GitRepositoryPusher {

    /** Result of publishing a tree, for the step log and the audit entry. */
    public record PushResult(String commitId, String branch) {
    }

    /**
     * Initialises {@code projectDir} as a repository, commits everything in it, and
     * pushes to {@code remoteUrl}.
     *
     * <p>Author identity is set explicitly rather than inherited from the host's git
     * configuration — the platform commits as itself, and a server with no global
     * {@code user.email} would otherwise fail the commit outright.
     */
    public PushResult publish(Path projectDir, String remoteUrl, String branch,
                              String commitMessage, CredentialsProvider credentials)
            throws GitAPIException, IOException, URISyntaxException {

        try (Git git = Git.init()
                .setDirectory(projectDir.toFile())
                .setInitialBranch(branch)
                .call()) {

            git.add().addFilepattern(".").call();

            RevCommit commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor("IDP Platform", "platform@company.internal")
                    .setCommitter("IDP Platform", "platform@company.internal")
                    .call();

            git.remoteAdd().setName("origin").setUri(new URIish(remoteUrl)).call();

            PushCommand push = git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(branch + ":refs/heads/" + branch));
            if (credentials != null) {
                push.setCredentialsProvider(credentials);
            }

            // JGit reports a rejected push in the result rather than by throwing, so
            // a non-OK status here would otherwise look like success.
            for (var result : push.call()) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() != RemoteRefUpdate.Status.OK
                            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw new IllegalStateException("Push rejected: " + update.getStatus()
                                + (update.getMessage() != null ? " — " + update.getMessage() : ""));
                    }
                }
            }

            log.info("[SCAFFOLD] Pushed {} to {} on branch {}", commit.getName(), sanitize(remoteUrl), branch);
            return new PushResult(commit.getName(), branch);
        }
    }

    /** Credentials for GitHub over HTTPS: the token goes in the username slot. */
    public CredentialsProvider tokenCredentials(String token) {
        return new UsernamePasswordCredentialsProvider(token, "");
    }

    /**
     * Strips any embedded credentials before a URL reaches the logs. Remotes are
     * built from configuration here, but this is the kind of thing that leaks the
     * moment someone passes a URL with a token in it.
     */
    private String sanitize(String remoteUrl) {
        int at = remoteUrl.indexOf('@');
        int scheme = remoteUrl.indexOf("//");
        return at > scheme && scheme >= 0
                ? remoteUrl.substring(0, scheme + 2) + "***" + remoteUrl.substring(at)
                : remoteUrl;
    }
}
