package uk.co.bithatch.opensim.jlib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IO {

    
    public void deleteDirectoryQuietly(Path directory) {
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException ignored) {
                        // Best-effort cleanup.
                    }
                });
        }
        catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
