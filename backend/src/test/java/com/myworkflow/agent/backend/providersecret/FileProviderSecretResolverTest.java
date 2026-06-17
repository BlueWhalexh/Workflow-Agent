package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProviderSecretResolverTest {

  @TempDir
  private Path tempDir;

  @Test
  void resolvesConfiguredRootRelativeFileRef() throws Exception {
    Path secretRoot = tempDir.resolve("secrets");
    Files.createDirectories(secretRoot.resolve("mimo"));
    Files.writeString(secretRoot.resolve("mimo/api-key.txt"), "local-file-secret-not-real\n");
    FileProviderSecretResolver resolver = new FileProviderSecretResolver(secretRoot);

    assertThat(resolver.resolveSecretValue("file://mimo/api-key.txt"))
        .contains("local-file-secret-not-real");
  }

  @Test
  void rejectsTraversalOutsideConfiguredRoot() throws Exception {
    Path secretRoot = tempDir.resolve("secrets");
    Files.createDirectories(secretRoot);
    Files.writeString(tempDir.resolve("outside.txt"), "outside-file-secret-not-real");
    FileProviderSecretResolver resolver = new FileProviderSecretResolver(secretRoot);

    assertThat(resolver.resolveSecretValue("file://../outside.txt")).isEmpty();
  }

  @Test
  void ignoresNonFileSecretRefs() {
    FileProviderSecretResolver resolver = new FileProviderSecretResolver(tempDir.resolve("secrets"));

    assertThat(resolver.resolveSecretValue("secret://team/provider/mimo")).isEmpty();
  }
}
