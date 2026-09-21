package uk.co.bithatch.opensim.jlib;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class IO {

	public static String getFilename(HttpURLConnection connection) {
		String contentDisposition = connection.getHeaderField("Content-Disposition");
		if (contentDisposition == null || contentDisposition.isBlank()) {
			return null;
		}

		Map<String, String> parameters = parseContentDispositionParameters(contentDisposition);

		String encodedFilename = parameters.get("filename*");
		if (encodedFilename != null && !encodedFilename.isBlank()) {
			String decoded = decodeRfc5987Value(encodedFilename);
			if (decoded != null && !decoded.isBlank()) {
				return decoded;
			}
		}

		String filename = parameters.get("filename");
		if (filename != null && !filename.isBlank()) {
			return stripQuotes(filename).trim();
		}

		return null;
	}

	private static Map<String, String> parseContentDispositionParameters(String header) {
		Map<String, String> parameters = new LinkedHashMap<>();
		int start = 0;
		boolean inQuotes = false;
		for (int i = 0; i <= header.length(); i++) {
			boolean atEnd = i == header.length();
			char ch = atEnd ? ';' : header.charAt(i);
			if (ch == '"') {
				inQuotes = !inQuotes;
			}
			if (!inQuotes && (ch == ';' || atEnd)) {
				String segment = header.substring(start, i).trim();
				start = i + 1;
				int equalsIndex = segment.indexOf('=');
				if (equalsIndex > 0) {
					String key = segment.substring(0, equalsIndex).trim().toLowerCase();
					String value = segment.substring(equalsIndex + 1).trim();
					parameters.put(key, value);
				}
			}
		}
		return parameters;
	}

	private static String decodeRfc5987Value(String value) {
		String cleaned = stripQuotes(value).trim();
		int firstTick = cleaned.indexOf('\'');
		int secondTick = firstTick >= 0 ? cleaned.indexOf('\'', firstTick + 1) : -1;

		if (firstTick <= 0 || secondTick < 0) {
			return percentDecode(cleaned, StandardCharsets.UTF_8);
		}

		String charsetName = cleaned.substring(0, firstTick).trim();
		String encodedSection = cleaned.substring(secondTick + 1);
		Charset charset;
		try {
			charset = Charset.forName(charsetName);
		} catch (Exception ignored) {
			charset = StandardCharsets.UTF_8;
		}
		return percentDecode(encodedSection, charset);
	}

	private static String percentDecode(String input, Charset charset) {
		try {
			return URLDecoder.decode(input.replace("+", "%2B"), charset);
		} catch (IllegalArgumentException ignored) {
			return input;
		}
	}

	private static String stripQuotes(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
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
