package com.myworkflow.agent.backend.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class TestWorkspaceCopier {

  private TestWorkspaceCopier() {
  }

  static void copy(Path source, Path target) throws IOException {
    try (var paths = Files.walk(source)) {
      for (Path sourcePath : paths.toList()) {
        Path relative = source.relativize(sourcePath);
        Path targetPath = target.resolve(relative);
        if (Files.isDirectory(sourcePath)) {
          Files.createDirectories(targetPath);
        } else {
          Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
