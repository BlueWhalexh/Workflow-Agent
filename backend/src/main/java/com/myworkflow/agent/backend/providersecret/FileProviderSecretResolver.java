package com.myworkflow.agent.backend.providersecret;

import com.myworkflow.agent.backend.config.BackendProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "my-workflow.backend.provider-secrets.file-root")
public class FileProviderSecretResolver implements ProviderSecretResolver {

  private static final String FILE_SECRET_REF_PREFIX = "file://";
  private static final long MAX_SECRET_FILE_BYTES = 16 * 1024;

  private final Path fileRoot;

  @Autowired
  public FileProviderSecretResolver(BackendProperties properties) {
    this(properties.providerSecretFileRoot()
        .orElseThrow(() -> new IllegalArgumentException("Provider secret file root is required")));
  }

  FileProviderSecretResolver(Path fileRoot) {
    this.fileRoot = fileRoot.toAbsolutePath().normalize();
  }

  @Override
  public Optional<String> resolveSecretValue(String secretRef) {
    if (secretRef == null || !secretRef.startsWith(FILE_SECRET_REF_PREFIX)) {
      return Optional.empty();
    }
    String relativePath = secretRef.substring(FILE_SECRET_REF_PREFIX.length()).trim();
    if (relativePath.isEmpty()) {
      return Optional.empty();
    }
    try {
      Path requestedPath = Path.of(relativePath);
      if (requestedPath.isAbsolute()) {
        return Optional.empty();
      }
      Path resolvedPath = fileRoot.resolve(requestedPath).normalize();
      if (!resolvedPath.startsWith(fileRoot) || !Files.isRegularFile(resolvedPath)) {
        return Optional.empty();
      }
      if (Files.size(resolvedPath) > MAX_SECRET_FILE_BYTES) {
        return Optional.empty();
      }
      String secretValue = Files.readString(resolvedPath, StandardCharsets.UTF_8).trim();
      if (secretValue.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(secretValue);
    } catch (IOException | InvalidPathException | SecurityException exception) {
      return Optional.empty();
    }
  }
}
