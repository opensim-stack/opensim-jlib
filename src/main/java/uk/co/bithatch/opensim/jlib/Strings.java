package uk.co.bithatch.opensim.jlib;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Strings {
	
	public static String[] mapToEnvVars(Map<String, String> envVars) {
		return envVars.entrySet().stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.toArray(String[]::new);
	}
	
	public static Map<String, String> envVarsToMap(String[] envVars) {
		return envVarsToMap(Arrays.asList(envVars));
	}
	
	public static Map<String, String> envVarsToMap(List<String> envVars) {
        return envVars.stream()
				.map(s -> s.split("=", 2))
				.filter(arr -> arr.length == 2)
				.collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
	}

	public static String primaryName(String[] names) {
        if (names == null || names.length == 0) {
            return null;
        }
        for (var rawName : names) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            return trimLeadingSlash(rawName);
        }
        return null;
    }

    public static String trimLeadingSlash(String name) {
        if (name == null) {
            return "";
        }
        return name.startsWith("/") ? name.substring(1) : name;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static String firstNonBlank(String... values) {
        for (var value : values) {
            var normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    public static String normalize(String value, String fallback) {
        var normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
	public static List<String> parseQuotedString(String command) {
		var args = new ArrayList<String>();
		var escaped = false;
		var quoted = false;
		var word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (escaped) {
				word.append(c);
				escaped = false;
				continue;
			}
			if (c == '\\') {
				escaped = true;
				continue;
			}
			if (c == '"') {
				quoted = !quoted;
				continue;
			}
			if (Character.isWhitespace(c) && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString());
					word.setLength(0);
				}
				continue;
			}
			word.append(c);
		}
		if (escaped) {
			throw new IllegalArgumentException("Invalid escape.");
		}
		if (quoted) {
			throw new IllegalArgumentException("Unbalanced quotes.");
		}
		if (word.length() > 0) {
			args.add(word.toString());
		}
		return args;
	}
}
