package uk.co.bithatch.opensim.jlib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Maps {

	public static <K, V> Map<K, V> of(Map<K, V> m1, Map<K, V> m2) {
		return of(List.of(m1, m2));
	}

	public static <K, V> Map<K, V> of(List<Map<K, V>> maps) {
		var map = new LinkedHashMap<K, V>();
		for (var m : maps) {
			map.putAll(m);
		}
		return map;
	}
}
