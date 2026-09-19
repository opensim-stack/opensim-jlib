package uk.co.bithatch.opensim.jlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

public class IO {

	public static String getFilename(HttpURLConnection connection) {
		String contentDisposition = connection.getHeaderField("Content-Disposition");
		if (contentDisposition != null && contentDisposition.contains("filename=")) {
			String filename = contentDisposition.split("filename=")[1].trim();
			if (filename.startsWith("\"") && filename.endsWith("\"")) {
				filename = filename.substring(1, filename.length() - 1);
			}
			return filename;
		}
		return null;
	}

	public static void deleteDirectoryQuietly(Path directory) {
		try (var walk = Files.walk(directory)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// Best-effort cleanup.
				}
			});
		} catch (IOException ignored) {
			// Best-effort cleanup.
		}
	}
}
