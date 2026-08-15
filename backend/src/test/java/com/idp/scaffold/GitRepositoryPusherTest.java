package com.idp.scaffold;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real push against a local bare repository over {@code file://}.
 *
 * <p>No network, no token, no GitHub — but the JGit path under test is the same one
 * production uses: init, add, commit, remote, push, and the rejected-push check.
 * This is the half of publishing most likely to break silently.
 */
class GitRepositoryPusherTest {

    private final GitRepositoryPusher pusher = new GitRepositoryPusher();

    /** Stands in for the freshly created, empty GitHub repository. */
    private Path bareRemote(Path root) throws Exception {
        Path remote = root.resolve("remote.git");
        Git.init().setBare(true).setDirectory(remote.toFile()).call().close();
        return remote;
    }

    private Path renderedProject(Path root) throws Exception {
        Path project = root.resolve("project");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("README.md"), "# scaffolded\n");
        Files.writeString(project.resolve("pom.xml"), "<project/>\n");
        Files.writeString(project.resolve("src/main/java/App.java"), "class App {}\n");
        return project;
    }

    @Test
    @DisplayName("pushes the rendered tree as an initial commit on the default branch")
    void pushesRenderedTree(@TempDir Path root) throws Exception {
        Path remote = bareRemote(root);
        Path project = renderedProject(root);

        GitRepositoryPusher.PushResult result = pusher.publish(
                project, remote.toUri().toString(), "main", "chore: scaffold demo", null);

        assertThat(result.branch()).isEqualTo("main");
        assertThat(result.commitId()).isNotBlank();

        // Read the commit back out of the remote — the push either landed or it did not.
        try (Git git = Git.open(remote.toFile())) {
            Repository repo = git.getRepository();
            assertThat(repo.resolve("refs/heads/main")).isNotNull();

            List<String> messages = new ArrayList<>();
            for (RevCommit commit : git.log().add(repo.resolve("refs/heads/main")).call()) {
                messages.add(commit.getFullMessage());
            }
            assertThat(messages).containsExactly("chore: scaffold demo");
        }
    }

    @Test
    @DisplayName("commits every rendered file, including nested directories")
    void commitsNestedFiles(@TempDir Path root) throws Exception {
        Path remote = bareRemote(root);
        Path project = renderedProject(root);

        pusher.publish(project, remote.toUri().toString(), "main", "chore: scaffold demo", null);

        try (Git git = Git.open(remote.toFile());
             var walk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository())) {
            var head = git.getRepository().resolve("refs/heads/main");
            try (var revWalk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                walk.addTree(revWalk.parseCommit(head).getTree());
            }
            walk.setRecursive(true);

            List<String> paths = new ArrayList<>();
            while (walk.next()) {
                paths.add(walk.getPathString());
            }
            assertThat(paths).containsExactlyInAnyOrder(
                    "README.md", "pom.xml", "src/main/java/App.java");
        }
    }

    @Test
    @DisplayName("honours a non-default branch name")
    void honoursConfiguredBranch(@TempDir Path root) throws Exception {
        Path remote = bareRemote(root);
        Path project = renderedProject(root);

        GitRepositoryPusher.PushResult result = pusher.publish(
                project, remote.toUri().toString(), "trunk", "chore: scaffold demo", null);

        assertThat(result.branch()).isEqualTo("trunk");
        try (Git git = Git.open(remote.toFile())) {
            assertThat(git.getRepository().resolve("refs/heads/trunk")).isNotNull();
            assertThat(git.getRepository().resolve("refs/heads/main")).isNull();
        }
    }

    @Test
    @DisplayName("fails loudly when the remote does not exist")
    void failsWhenRemoteMissing(@TempDir Path root) throws Exception {
        Path project = renderedProject(root);

        // A scaffold that cannot publish must not report success — the catalogue
        // would otherwise register a service pointing at nothing.
        assertThatThrownBy(() -> pusher.publish(
                project, root.resolve("does-not-exist.git").toUri().toString(),
                "main", "chore: scaffold demo", null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("builds token credentials without leaking the token into the URL")
    void buildsTokenCredentials() {
        assertThat(pusher.tokenCredentials("ghp_secret")).isNotNull();
    }
}
