package uk.co.bithatch.opensim.jlib;

import java.util.ArrayList;
import java.util.List;

public class Strings {

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
