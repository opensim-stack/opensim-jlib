package uk.co.bithatch.opensim.jlib;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Thin XML-RPC client for OpenSimulator RemoteAdmin ({@code admin_*}) calls.
 *
 * <p>
 * This implementation intentionally uses JDK-only APIs (HTTP client and DOM parser)
 * so callers do not need an additional transport dependency.
 */
public class OpensimRemoteAdminClient {

	private static final String METHOD_PREFIX = "admin_";

	private final URI endpoint;
	private final HttpClient httpClient;
	private final String password;
	private final Duration requestTimeout;
	private final boolean debugEnabled;

	public OpensimRemoteAdminClient(String endpoint, String password) {
		this(endpoint, password, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
				Duration.ofSeconds(75), Boolean.parseBoolean(System.getProperty("opensim.debug", "false")));
	}

	public OpensimRemoteAdminClient(String endpoint, String password, HttpClient httpClient, Duration requestTimeout,
			boolean debugEnabled) {
		this.endpoint = URI.create(Objects.requireNonNull(endpoint, "endpoint must not be null"));
		this.password = Objects.requireNonNull(password, "password must not be null");
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
		this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(75) : requestTimeout;
		this.debugEnabled = debugEnabled;
	}
	
	public Optional<AgentLocation> findAgent(String name, String uuid, Map<String, String> regions) {
		for (var regionEn : regions.entrySet()) {
			var agents = getAgents(null, regionEn.getKey(), false);
			if (!agents.isEmpty()) {
				return agents.get(0).agents().stream()
						.filter(a -> (name == null ? (a.name().equals(name)) : a.uuid().equals(uuid)))
						.map(a -> new AgentLocation(regionEn.getKey(), regionEn.getValue(), a)).findFirst();
			}
		}

		return Optional.empty();
	}

	public List<Region> getAgents(String regionName, String regionId) {
		return getAgents(regionName, regionId, false);
	}

	public List<Region> getAgents(String regionName, String regionId, boolean includeChildren) {
		var params = new LinkedHashMap<String, Object>();
		putIfPresent(params, "region_name", regionName);
		putIfPresent(params, "region_id", regionId);
		if (includeChildren) {
			params.put("include_children", true);
		}
		var response = callAdminForStruct("get_agents", params);
		return parseRegions(response, "admin_get_agents");
	}

	public void teleportAgent(TeleportRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		callAdmin("teleport_agent", request.toParams());
	}

	public void broadcast(String message) {
		var params = new LinkedHashMap<String, Object>();
		params.put("message", requireNonBlank(message, "message"));
		callAdmin("broadcast", params);
	}

	public String closeRegionByName(String regionName) {
		var response = callAdminForStruct("close_region", Map.of("region_name", requireNonBlank(regionName, "regionName")));
		return requireStringField(response, "region_name", "admin_close_region");
	}

	public String closeRegionById(String regionId) {
		var response = callAdminForStruct("close_region", Map.of("region_id", requireNonBlank(regionId, "regionId")));
		return firstNonBlankField(response, "admin_close_region", "region_uuid", "region_id");
	}

	public CreateRegionResponse createRegion(CreateRegionRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		var response = callAdminForStruct("create_region", request.toParams());
		return new CreateRegionResponse(
				requireStringField(response, "region_name", "admin_create_region"),
				firstNonBlankField(response, "admin_create_region", "region_uuid", "region_id"));
	}

	public String deleteRegion(String regionName) {
		var response = callAdminForStruct("delete_region", Map.of("region_name", requireNonBlank(regionName, "regionName")));
		return firstNonBlankField(response, "admin_delete_region", "region_id", "region_uuid", "region_name");
	}

	public String modifyRegion(boolean enableVoice, boolean isPublic) {
		var params = new LinkedHashMap<String, Object>();
		params.put("enable_voice", String.valueOf(enableVoice));
		params.put("public", String.valueOf(isPublic));
		var response = callAdminForStruct("modify_region", params);
		return firstNonBlankField(response, "admin_modify_region", "region_id", "region_uuid", "region_name");
	}

	public String modifyRegionByName(String regionName, boolean enableVoice, boolean isPublic) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_name", requireNonBlank(regionName, "regionName"));
		params.put("enable_voice", String.valueOf(enableVoice));
		params.put("public", String.valueOf(isPublic));
		var response = callAdminForStruct("modify_region", params);
		return firstNonBlankField(response, "admin_modify_region", "region_id", "region_uuid", "region_name");
	}

	public String modifyRegionById(String regionId, boolean enableVoice, boolean isPublic) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_id", requireNonBlank(regionId, "regionId"));
		params.put("enable_voice", String.valueOf(enableVoice));
		params.put("public", String.valueOf(isPublic));
		var response = callAdminForStruct("modify_region", params);
		return firstNonBlankField(response, "admin_modify_region", "region_id", "region_uuid", "region_name");
	}

	public int regionQueryByName(String regionName) {
		var response = callAdminForStruct("region_query", Map.of("region_name", requireNonBlank(regionName, "regionName")));
		return requireIntField(response, "health", "admin_region_query");
	}

	public int regionQueryById(String regionId) {
		var response = callAdminForStruct("region_query", Map.of("region_id", requireNonBlank(regionId, "regionId")));
		return requireIntField(response, "health", "admin_region_query");
	}

	public boolean restart(String regionId) {
		var response = callAdminForStruct("restart", Map.of("region_id", requireNonBlank(regionId, "regionId")));
		throwIfNotAccepted("admin_restart", response);
		return requireBooleanField(response, "rebooting", "admin_restart");
	}

	public void shutdown() {
		shutdown(new ShutdownRequest(null, null, null));
	}

	public void shutdown(ShutdownRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		var response = callAdminForStruct("shutdown", request.toParams());
		throwIfNotAccepted("admin_shutdown", response);
	}

	public void estateReload() {
		callAdmin("estate_reload", Map.of());
	}

	public void consoleCommand(String command) {
		callAdmin("console_command", Map.of("command", requireNonBlank(command, "command")));
	}

	public void dialog() {
		callAdmin("dialog", Map.of());
	}

	public void resetLand() {
		callAdmin("reset_land", Map.of());
	}

	public void refreshSearch() {
		callAdmin("refresh_search", Map.of());
	}

	public void refreshMap() {
		callAdmin("refresh_map", Map.of());
	}

	public String getOpenSimVersion() {
		var response = callAdminForStruct("get_opensim_version", Map.of());
		return requireStringField(response, "version", "admin_get_opensim_version");
	}

	public int getAgentCount() {
		var response = callAdminForStruct("get_agent_count", Map.of());
		return requireIntField(response, "count", "admin_get_agent_count");
	}

	public void loadHeightmap(LoadHeightmapRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		callAdminForStruct("load_heightmap", request.toParams());
	}

	public void loadOarByName(String regionName, String filename) {
		var response = callAdminForStruct("load_oar",
				Map.of("region_name", requireNonBlank(regionName, "regionName"), "filename",
						requireNonBlank(filename, "filename")));
		requireTrueField(response, "loaded", "admin_load_oar");
	}

	public void loadOarById(String regionId, String filename) {
		var response = callAdminForStruct("load_oar",
				Map.of("region_id", requireNonBlank(regionId, "regionId"), "filename",
						requireNonBlank(filename, "filename")));
		requireTrueField(response, "loaded", "admin_load_oar");
	}

	public void loadXmlByName(String regionName, String filename) {
		loadXmlByName(regionName, filename, 1);
	}

	public void loadXmlByName(String regionName, String filename, int xmlVersion) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_name", requireNonBlank(regionName, "regionName"));
		params.put("filename", requireNonBlank(filename, "filename"));
		params.put("xml_version", validateXmlVersion(xmlVersion));
		var response = callAdminForStruct("load_xml", params);
		requireTrueField(response, "loaded", "admin_load_xml");
		requireTrueField(response, "switched", "admin_load_xml");
	}

	public void loadXmlById(String regionId, String filename) {
		loadXmlById(regionId, filename, 1);
	}

	public void loadXmlById(String regionId, String filename, int xmlVersion) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_id", requireNonBlank(regionId, "regionId"));
		params.put("filename", requireNonBlank(filename, "filename"));
		params.put("xml_version", validateXmlVersion(xmlVersion));
		var response = callAdminForStruct("load_xml", params);
		requireTrueField(response, "loaded", "admin_load_xml");
		requireTrueField(response, "switched", "admin_load_xml");
	}

	public void saveHeightmapByName(String regionName, String filename) {
		callAdminForStruct("save_heightmap",
				Map.of("region_name", requireNonBlank(regionName, "regionName"), "filename",
						requireNonBlank(filename, "filename")));
	}

	public void saveHeightmapById(String regionId, String filename) {
		callAdminForStruct("save_heightmap",
				Map.of("region_id", requireNonBlank(regionId, "regionId"), "filename",
						requireNonBlank(filename, "filename")));
	}

	public void saveOar(SaveOarRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		var response = callAdminForStruct("save_oar", request.toParams());
		requireTrueField(response, "saved", "admin_save_oar");
	}

	public void saveXml(SaveXmlRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request must not be null");
		}
		var response = callAdminForStruct("save_xml", request.toParams());
		requireTrueField(response, "saved", "admin_save_xml");
		requireTrueField(response, "switched", "admin_save_xml");
	}

	public List<String> aclListByName(String regionName) {
		var response = callAdminForStruct("acl_list", Map.of("region_name", requireNonBlank(regionName, "regionName")));
		return requireStringListField(response, "users", "admin_acl_list");
	}

	public List<String> aclListById(String regionId) {
		var response = callAdminForStruct("acl_list", Map.of("region_id", requireNonBlank(regionId, "regionId")));
		return requireStringListField(response, "users", "admin_acl_list");
	}

	public void aclClearByName(String regionName) {
		callAdminForStruct("acl_clear", Map.of("region_name", requireNonBlank(regionName, "regionName")));
	}

	public void aclClearById(String regionId) {
		callAdminForStruct("acl_clear", Map.of("region_id", requireNonBlank(regionId, "regionId")));
	}

	public void aclAddByName(String regionName, Collection<String> users) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_name", requireNonBlank(regionName, "regionName"));
		params.put("users", normalizeUsers(users));
		callAdminForStruct("acl_add", params);
	}

	public void aclAddByName(String regionName, String... users) {
		aclAddByName(regionName, users == null ? List.of() : List.of(users));
	}

	public void aclAddById(String regionId, Collection<String> users) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_id", requireNonBlank(regionId, "regionId"));
		params.put("users", normalizeUsers(users));
		callAdminForStruct("acl_add", params);
	}

	public void aclAddById(String regionId, String... users) {
		aclAddById(regionId, users == null ? List.of() : List.of(users));
	}

	public int aclRemoveByName(String regionName, Collection<String> users) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_name", requireNonBlank(regionName, "regionName"));
		params.put("users", normalizeUsers(users));
		var response = callAdminForStruct("acl_remove", params);
		return requireIntField(response, "removed", "admin_acl_remove");
	}

	public int aclRemoveByName(String regionName, String... users) {
		return aclRemoveByName(regionName, users == null ? List.of() : List.of(users));
	}

	public int aclRemoveById(String regionId, Collection<String> users) {
		var params = new LinkedHashMap<String, Object>();
		params.put("region_id", requireNonBlank(regionId, "regionId"));
		params.put("users", normalizeUsers(users));
		var response = callAdminForStruct("acl_remove", params);
		return requireIntField(response, "removed", "admin_acl_remove");
	}

	public int aclRemoveById(String regionId, String... users) {
		return aclRemoveById(regionId, users == null ? List.of() : List.of(users));
	}

	public String createUser(String userFirstName, String userLastName, String userPassword, int startRegionX,
			int startRegionY) {
		var params = baseUserParams(userFirstName, userLastName);
		params.put("user_password", requireNonBlank(userPassword, "userPassword"));
		params.put("start_region_x", startRegionX);
		params.put("start_region_y", startRegionY);
		var response = callAdminForStruct("create_user", params);
		return requireStringField(response, "avatar_uuid", "admin_create_user");
	}

	public String createUser(String userFirstName, String userLastName, String userPassword, int startRegionX,
			int startRegionY, String userEmail) {
		var params = baseUserParams(userFirstName, userLastName);
		params.put("user_password", requireNonBlank(userPassword, "userPassword"));
		params.put("start_region_x", startRegionX);
		params.put("start_region_y", startRegionY);
		params.put("user_email", requireNonBlank(userEmail, "userEmail"));
		var response = callAdminForStruct("create_user", params);
		return requireStringField(response, "avatar_uuid", "admin_create_user");
	}

	public Instant existsUser(String userFirstName, String userLastName) {
		var response = callAdminForStruct("exists_user", baseUserParams(userFirstName, userLastName));
		var epochSeconds = requireLongField(response, "lastlogin", "admin_exists_user");
		return Instant.ofEpochSecond(epochSeconds);
	}

	public String updateUserPassword(String userFirstName, String userLastName, String password) {
		var params = baseUserParams(userFirstName, userLastName);
		params.put("user_password", requireNonBlank(password, "password"));
		var response = callAdminForStruct("update_user", params);
		return requireStringField(response, "avatar_uuid", "admin_update_user");
	}

	public String updateUserStart(String userFirstName, String userLastName, int startRegionX, int startRegionY) {
		var params = baseUserParams(userFirstName, userLastName);
		params.put("start_region_x", startRegionX);
		params.put("start_region_y", startRegionY);
		var response = callAdminForStruct("update_user", params);
		return requireStringField(response, "avatar_uuid", "admin_update_user");
	}

	public String updateUser(String userFirstName, String userLastName, String password, int startRegionX,
			int startRegionY) {
		var params = baseUserParams(userFirstName, userLastName);
		params.put("user_password", requireNonBlank(password, "password"));
		params.put("start_region_x", startRegionX);
		params.put("start_region_y", startRegionY);
		var response = callAdminForStruct("update_user", params);
		return requireStringField(response, "avatar_uuid", "admin_update_user");
	}

	public String authenticateUser(String userFirstName, String userLastName, char[] userPassword,
			int tokenLifetimeSeconds) {
		if (tokenLifetimeSeconds < 1 || tokenLifetimeSeconds > 30) {
			throw new IllegalArgumentException("tokenLifetimeSeconds must be between 1 and 30.");
		}
		var params = baseUserParams(userFirstName, userLastName);
		params.put("user_password", md5Hex(userPassword));
		params.put("token_lifetime", String.valueOf(tokenLifetimeSeconds));
		var response = callAdminForStruct("authenticate_user", params);
		return requireStringField(response, "token", "admin_authenticate_user");
	}

	public Map<String, Object> callAdminForStruct(String methodSuffix, Map<String, ?> parameters) {
		var response = callAdmin(methodSuffix, parameters);
		if (response instanceof Map<?, ?> map) {
			var typed = new LinkedHashMap<String, Object>();
			for (var entry : map.entrySet()) {
				typed.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return typed;
		}
		throw new IllegalStateException("Expected XML-RPC struct response but got " + typeName(response));
	}

	public Object callAdmin(String methodSuffix, Map<String, ?> parameters) {
		if (methodSuffix == null || methodSuffix.isBlank()) {
			throw new IllegalArgumentException("methodSuffix must not be blank");
		}

		var methodName = methodSuffix.startsWith(METHOD_PREFIX) ? methodSuffix : METHOD_PREFIX + methodSuffix;
		var payload = new LinkedHashMap<String, Object>();
		payload.put("password", password);
		if (parameters != null) {
			for (var entry : parameters.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isBlank()) {
					continue;
				}
				payload.put(entry.getKey(), entry.getValue());
			}
		}

		var xml = buildMethodCall(methodName, payload);
		debug("HTTP", "POST " + endpoint + " body=" + sanitize(xml));
		var responseBody = postXml(xml);
		debug("HTTP", "XML-RPC response=" + sanitize(responseBody));
		var parsed = parseMethodResponse(responseBody);
		throwIfRemoteAdminError(methodName, parsed);
		return parsed;
	}

	private String postXml(String body) {
		try {
			var request = HttpRequest.newBuilder(endpoint)
					.timeout(requestTimeout)
					.header("Content-Type", "text/xml")
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();

			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() > 299) {
				throw new IllegalStateException("HTTP " + response.statusCode() + " from " + endpoint);
			}
			return response.body();
		} catch (IOException e) {
			throw new IllegalStateException("XML-RPC call failed: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("XML-RPC call interrupted.", e);
		}
	}

	private static String buildMethodCall(String methodName, Map<String, ?> parameters) {
		var xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\"?>");
		xml.append("<methodCall>");
		xml.append("<methodName>").append(escapeXml(methodName)).append("</methodName>");
		xml.append("<params><param><value>");
		appendXmlValue(xml, parameters == null ? Map.of() : parameters);
		xml.append("</value></param></params>");
		xml.append("</methodCall>");
		return xml.toString();
	}

	private static Object parseMethodResponse(String xml) {
		var document = parseXml(xml);
		var faultNodes = document.getElementsByTagName("fault");
		if (faultNodes.getLength() > 0) {
			var faultValue = firstChildElementByTag((Element) faultNodes.item(0), "value")
					.map(OpensimRemoteAdminClient::parseValue)
					.orElse("Unknown XML-RPC fault");
			throw new IllegalStateException("XML-RPC fault: " + faultValue);
		}

		var paramsNodes = document.getElementsByTagName("params");
		if (paramsNodes.getLength() == 0) {
			throw new IllegalStateException("Invalid XML-RPC response: <params> not found.");
		}
		var params = (Element) paramsNodes.item(0);
		var param = firstChildElementByTag(params, "param")
				.orElseThrow(() -> new IllegalStateException("Invalid XML-RPC response: <param> not found."));
		var value = firstChildElementByTag(param, "value")
				.orElseThrow(() -> new IllegalStateException("Invalid XML-RPC response: <value> not found."));
		return parseValue(value);
	}

	private static Object parseValue(Element valueElement) {
		var child = firstElementChild(valueElement);
		if (child == null) {
			return valueElement.getTextContent() == null ? "" : valueElement.getTextContent();
		}

		return switch (child.getTagName()) {
		case "string" -> child.getTextContent() == null ? "" : child.getTextContent();
		case "int", "i4" -> Integer.parseInt(child.getTextContent().trim());
		case "double" -> Double.parseDouble(child.getTextContent().trim());
		case "boolean" -> "1".equals(child.getTextContent().trim()) || "true".equalsIgnoreCase(child.getTextContent().trim());
		case "array" -> parseArray(child);
		case "struct" -> parseStruct(child);
		case "nil" -> null;
		case "base64" -> Base64.getDecoder().decode(child.getTextContent().trim());
		default -> child.getTextContent();
		};
	}

	private static List<Object> parseArray(Element arrayElement) {
		var data = firstChildElementByTag(arrayElement, "data")
				.orElseThrow(() -> new IllegalStateException("Invalid XML-RPC array: <data> not found."));
		var items = new ArrayList<Object>();
		for (var valueElement : childElementsByTag(data, "value")) {
			items.add(parseValue(valueElement));
		}
		return items;
	}

	private static Map<String, Object> parseStruct(Element structElement) {
		var map = new LinkedHashMap<String, Object>();
		for (var member : childElementsByTag(structElement, "member")) {
			var name = firstChildElementByTag(member, "name")
					.map(Element::getTextContent)
					.orElse("");
			var value = firstChildElementByTag(member, "value")
					.map(OpensimRemoteAdminClient::parseValue)
					.orElse(null);
			map.put(name, value);
		}
		return map;
	}

	private static void appendXmlValue(StringBuilder xml, Object value) {
		if (value == null) {
			xml.append("<nil/>");
			return;
		}
		if (value instanceof Boolean bool) {
			xml.append("<boolean>").append(bool ? "1" : "0").append("</boolean>");
			return;
		}
		if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			xml.append("<int>").append(value).append("</int>");
			return;
		}
		if (value instanceof Float || value instanceof Double) {
			xml.append("<double>").append(value).append("</double>");
			return;
		}
		if (value instanceof byte[] bytes) {
			xml.append("<base64>").append(Base64.getEncoder().encodeToString(bytes)).append("</base64>");
			return;
		}
		if (value instanceof Map<?, ?> map) {
			xml.append("<struct>");
			for (var entry : map.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				xml.append("<member><name>")
						.append(escapeXml(String.valueOf(entry.getKey())))
						.append("</name><value>");
				appendXmlValue(xml, entry.getValue());
				xml.append("</value></member>");
			}
			xml.append("</struct>");
			return;
		}
		if (value instanceof List<?> list) {
			xml.append("<array><data>");
			for (var item : list) {
				xml.append("<value>");
				appendXmlValue(xml, item);
				xml.append("</value>");
			}
			xml.append("</data></array>");
			return;
		}
		if (value.getClass().isArray()) {
			xml.append("<array><data>");
			var length = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < length; i++) {
				xml.append("<value>");
				appendXmlValue(xml, java.lang.reflect.Array.get(value, i));
				xml.append("</value>");
			}
			xml.append("</data></array>");
			return;
		}

		xml.append("<string>").append(escapeXml(String.valueOf(value))).append("</string>");
	}

	private static Optional<Element> firstChildElementByTag(Element element, String tagName) {
		var children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			var node = children.item(i);
			if (node instanceof Element child && tagName.equals(child.getTagName())) {
				return Optional.of(child);
			}
		}
		return Optional.empty();
	}

	private static List<Element> childElementsByTag(Element element, String tagName) {
		var result = new ArrayList<Element>();
		var children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			var node = children.item(i);
			if (node instanceof Element child && tagName.equals(child.getTagName())) {
				result.add(child);
			}
		}
		return result;
	}

	private static Element firstElementChild(Element element) {
		var children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			var node = children.item(i);
			if (node instanceof Element child) {
				return child;
			}
		}
		return null;
	}

	private static Document parseXml(String xml) {
		try {
			if (xml == null || xml.isBlank()) {
				throw new IllegalStateException("Failed to parse XML: empty response body.");
			}
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			var builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse XML: " + sanitize(xml), e);
		}
	}

	private static String escapeXml(String value) {
		if (value == null) {
			return "";
		}
		var out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			var c = value.charAt(i);
			switch (c) {
			case '&' -> out.append("&amp;");
			case '<' -> out.append("&lt;");
			case '>' -> out.append("&gt;");
			case '\'' -> out.append("&apos;");
			case '"' -> out.append("&quot;");
			default -> out.append(c);
			}
		}
		return out.toString();
	}

	private static String typeName(Object value) {
		return value == null ? "null" : value.getClass().getSimpleName();
	}

	private static void putIfPresent(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isBlank()) {
			map.put(key, value);
		}
	}

	private static LinkedHashMap<String, Object> baseUserParams(String userFirstName, String userLastName) {
		var params = new LinkedHashMap<String, Object>();
		params.put("user_firstname", requireNonBlank(userFirstName, "userFirstName"));
		params.put("user_lastname", requireNonBlank(userLastName, "userLastName"));
		return params;
	}

	private static String requireNonBlank(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value;
	}

	private static String requireStringField(Map<String, Object> response, String fieldName, String methodName) {
		var value = getFieldIgnoreCase(response, fieldName)
				.orElseThrow(() -> new IllegalStateException(methodName + " response missing '" + fieldName + "'."));
		var text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			throw new IllegalStateException(methodName + " response field '" + fieldName + "' is empty.");
		}
		return text;
	}

	private static long requireLongField(Map<String, Object> response, String fieldName, String methodName) {
		var value = getFieldIgnoreCase(response, fieldName)
				.orElseThrow(() -> new IllegalStateException(methodName + " response missing '" + fieldName + "'."));
		if (value instanceof Number number) {
			return number.longValue();
		}
		var text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			throw new IllegalStateException(methodName + " response field '" + fieldName + "' is empty.");
		}
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			throw new IllegalStateException(
					methodName + " response field '" + fieldName + "' is not a valid UNIX timestamp: " + text, e);
		}
	}

	private static int requireIntField(Map<String, Object> response, String fieldName, String methodName) {
		var value = getFieldIgnoreCase(response, fieldName)
				.orElseThrow(() -> new IllegalStateException(methodName + " response missing '" + fieldName + "'."));
		if (value instanceof Number number) {
			return number.intValue();
		}
		var text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			throw new IllegalStateException(methodName + " response field '" + fieldName + "' is empty.");
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new IllegalStateException(
					methodName + " response field '" + fieldName + "' is not a valid integer: " + text, e);
		}
	}

	private static List<String> requireStringListField(Map<String, Object> response, String fieldName,
			String methodName) {
		var value = getFieldIgnoreCase(response, fieldName)
				.orElseThrow(() -> new IllegalStateException(methodName + " response missing '" + fieldName + "'."));
		if (value instanceof Map<?, ?> map) {
			var out = new ArrayList<String>();
			for (var entry : map.entrySet()) {
				if (entry.getValue() == null) {
					continue;
				}
				var text = String.valueOf(entry.getValue()).trim();
				if (!text.isEmpty()) {
					out.add(text);
				}
			}
			return List.copyOf(out);
		}
		if (value instanceof List<?> list) {
			var out = new ArrayList<String>();
			for (var item : list) {
				if (item == null) {
					continue;
				}
				var text = String.valueOf(item).trim();
				if (!text.isEmpty()) {
					out.add(text);
				}
			}
			return List.copyOf(out);
		}
		var text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return List.of();
		}
		return List.of(text);
	}

	private static boolean requireBooleanField(Map<String, Object> response, String fieldName, String methodName) {
		var value = getFieldIgnoreCase(response, fieldName)
				.orElseThrow(() -> new IllegalStateException(methodName + " response missing '" + fieldName + "'."));
		return toBoolean(value)
				.orElseThrow(() -> new IllegalStateException(
						methodName + " response field '" + fieldName + "' is not a valid boolean: " + value));
	}

	private static void requireTrueField(Map<String, Object> response, String fieldName, String methodName) {
		if (!requireBooleanField(response, fieldName, methodName)) {
			throw new IllegalStateException(methodName + " reported " + fieldName + "=false.");
		}
	}

	private static String firstNonBlankField(Map<String, Object> response, String methodName, String... fieldNames) {
		for (var fieldName : fieldNames) {
			var value = getFieldIgnoreCase(response, fieldName);
			if (value.isPresent()) {
				var text = String.valueOf(value.get()).trim();
				if (!text.isEmpty()) {
					return text;
				}
			}
		}
		throw new IllegalStateException(methodName + " response missing expected fields: " + String.join(", ", fieldNames));
	}

	private static String md5Hex(char[] password) {
		if (password == null || password.length == 0) {
			throw new IllegalArgumentException("userPassword must not be empty");
		}
		try {
			var digest = MessageDigest.getInstance("MD5");
			var bytes = new String(password).getBytes(StandardCharsets.UTF_8);
			var hash = digest.digest(bytes);
			var out = new StringBuilder(hash.length * 2);
			for (var b : hash) {
				out.append(Character.forDigit((b >> 4) & 0xF, 16));
				out.append(Character.forDigit(b & 0xF, 16));
			}
			return out.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("MD5 algorithm is not available.", e);
		}
	}

	private static Map<String, String> normalizeUsers(Collection<String> users) {
		if (users == null || users.isEmpty()) {
			throw new IllegalArgumentException("users must not be empty");
		}
		var out = new LinkedHashMap<String, String>();
		for (var user : users) {
			if (user == null) {
				continue;
			}
			var normalized = user.trim();
			if (!normalized.isEmpty()) {
				out.put(normalized, normalized);
			}
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException("users must include at least one non-blank name");
		}
		return Map.copyOf(out);
	}

	private static List<Region> parseRegions(Map<String, Object> response, String methodName) {
		var regionsValue = getFieldIgnoreCase(response, "regions").orElse(List.of());
		var regionItems = objectToItemList(regionsValue);
		var regions = new ArrayList<Region>();
		for (var item : regionItems) {
			if (!(item instanceof Map<?, ?> map)) {
				throw new IllegalStateException(methodName + " response contains invalid region entry: " + item);
			}
			regions.add(parseRegion(map, methodName));
		}
		return List.copyOf(regions);
	}

	private static Region parseRegion(Map<?, ?> regionMap, String methodName) {
		var name = getStringField(regionMap, "name", methodName + " region");
		var uuid = getStringField(regionMap, "uuid", methodName + " region");
		var agentsValue = getFieldIgnoreCase(regionMap, "agents").orElse(List.of());
		var agentItems = objectToItemList(agentsValue);
		var agents = new ArrayList<Agent>();
		for (var item : agentItems) {
			if (!(item instanceof Map<?, ?> map)) {
				throw new IllegalStateException(methodName + " response contains invalid agent entry: " + item);
			}
			agents.add(parseAgent(map));
		}
		return new Region(name, uuid, List.copyOf(agents));
	}

	private static Agent parseAgent(Map<?, ?> agentMap) {
		return new Agent(getOptionalStringField(agentMap, "name"), getOptionalStringField(agentMap, "uuid"),
				getOptionalStringField(agentMap, "type"), getOptionalStringField(agentMap, "current_parcel_uuid"),
				getOptionalDoubleField(agentMap, "pos_x"), getOptionalDoubleField(agentMap, "pos_y"),
				getOptionalDoubleField(agentMap, "pos_z"), getOptionalDoubleField(agentMap, "vel_x"),
				getOptionalDoubleField(agentMap, "vel_y"), getOptionalDoubleField(agentMap, "vel_z"),
				getOptionalDoubleField(agentMap, "lookat_x"), getOptionalDoubleField(agentMap, "lookat_y"),
				getOptionalDoubleField(agentMap, "lookat_z"), getOptionalBooleanField(agentMap, "is_flying"),
				getOptionalBooleanField(agentMap, "is_sat_on_ground"),
				getOptionalBooleanField(agentMap, "is_sat_on_object"));
	}

	private static List<Object> objectToItemList(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof List<?> list) {
			return new ArrayList<>(list);
		}
		if (value instanceof Map<?, ?> map) {
			var out = new ArrayList<Object>();
			for (var entry : map.entrySet()) {
				if (entry.getValue() != null) {
					out.add(entry.getValue());
				}
			}
			return out;
		}
		return List.of(value);
	}

	private static String getStringField(Map<?, ?> map, String key, String context) {
		return getFieldIgnoreCase(map, key)
				.map(String::valueOf)
				.map(String::trim)
				.filter(v -> !v.isEmpty())
				.orElseThrow(() -> new IllegalStateException(context + " is missing required field '" + key + "'."));
	}

	private static String getOptionalStringField(Map<?, ?> map, String key) {
		return getFieldIgnoreCase(map, key)
				.map(String::valueOf)
				.map(String::trim)
				.filter(v -> !v.isEmpty())
				.orElse(null);
	}

	private static Double getOptionalDoubleField(Map<?, ?> map, String key) {
		var raw = getFieldIgnoreCase(map, key).orElse(null);
		if (raw == null) {
			return null;
		}
		if (raw instanceof Number number) {
			return number.doubleValue();
		}
		var text = String.valueOf(raw).trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			throw new IllegalStateException("Invalid decimal value for field '" + key + "': " + raw, e);
		}
	}

	private static Boolean getOptionalBooleanField(Map<?, ?> map, String key) {
		var raw = getFieldIgnoreCase(map, key).orElse(null);
		if (raw == null) {
			return null;
		}
		return toBoolean(raw)
				.orElseThrow(() -> new IllegalStateException("Invalid boolean value for field '" + key + "': " + raw));
	}

	private static int validateXmlVersion(int xmlVersion) {
		if (xmlVersion != 1 && xmlVersion != 2) {
			throw new IllegalArgumentException("xmlVersion must be 1 or 2");
		}
		return xmlVersion;
	}

	private void debug(String channel, String message) {
		if (debugEnabled) {
			System.err.println("[OpensimRemoteAdminClient][" + channel + "] " + message);
		}
	}

	private static String sanitize(String text) {
		if (text == null) {
			return "<null>";
		}
		var compact = text.replace('\n', ' ').replace('\r', ' ').trim();
		if (compact.length() <= 300) {
			return compact;
		}
		return compact.substring(0, 300) + "...";
	}

	static String buildMethodCallForTest(String methodName, Map<String, ?> parameters) {
		return buildMethodCall(methodName, parameters);
	}

	static Object parseMethodResponseForTest(String xml) {
		return parseMethodResponse(xml);
	}

	static void throwIfRemoteAdminErrorForTest(String methodName, Object response) {
		throwIfRemoteAdminError(methodName, response);
	}

	static void throwIfNotAcceptedForTest(String methodName, Map<String, Object> response) {
		throwIfNotAccepted(methodName, response);
	}

	public static TeleportRequestBuilder teleportRequestBuilder(String agentId, String regionNameOrId) {
		return new TeleportRequestBuilder(agentId, regionNameOrId);
	}

	public record TeleportRequest(String agentId, String regionName, String regionUuid, Double localX, Double localY,
			Double localZ, Double lookAtX, Double lookAtY, Double lookAtZ, Boolean noFly,
			Map<String, Object> extraParams) {

		public TeleportRequest {
			if (agentId == null || agentId.isBlank()) {
				throw new IllegalArgumentException("agentId must not be blank");
			}
			if ((regionName == null || regionName.isBlank()) && (regionUuid == null || regionUuid.isBlank())) {
				throw new IllegalArgumentException("either regionName or regionUuid must be provided");
			}
			extraParams = extraParams == null ? Map.of() : Map.copyOf(extraParams);
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			params.put("agent_id", agentId);
			if (regionName != null && !regionName.isBlank()) {
				params.put("region_name", regionName);
			}
			if (regionUuid != null && !regionUuid.isBlank()) {
				params.put("region_id", regionUuid);
			}
			if (localX != null) {
				params.put("position_x", localX);
			}
			if (localY != null) {
				params.put("position_y", localY);
			}
			if (localZ != null) {
				params.put("position_z", localZ);
			}
			if (lookAtX != null) {
				params.put("lookat_x", lookAtX);
			}
			if (lookAtY != null) {
				params.put("lookat_y", lookAtY);
			}
			if (lookAtZ != null) {
				params.put("lookat_z", lookAtZ);
			}
			if (noFly != null) {
				params.put("nofly", noFly);
			}
			params.putAll(extraParams);
			return params;
		}
	}

	public static final class TeleportRequestBuilder {
		private final String agentId;
		private String regionName;
		private String regionUuid;
		private Double localX;
		private Double localY;
		private Double localZ;
		private Double lookAtX;
		private Double lookAtY;
		private Double lookAtZ;
		private Boolean noFly;
		private final Map<String, Object> extraParams = new LinkedHashMap<>();

		private TeleportRequestBuilder(String agentId, String regionNameOrId) {
			this.agentId = agentId;
			if (looksLikeUuid(regionNameOrId)) {
				this.regionUuid = regionNameOrId;
			} else {
				this.regionName = regionNameOrId;
			}
		}

		public TeleportRequestBuilder regionName(String regionName) {
			this.regionName = regionName;
			return this;
		}

		public TeleportRequestBuilder regionUuid(String regionUuid) {
			this.regionUuid = regionUuid;
			return this;
		}

		public TeleportRequestBuilder position(double x, double y, double z) {
			this.localX = x;
			this.localY = y;
			this.localZ = z;
			return this;
		}

		public TeleportRequestBuilder lookAt(double x, double y, double z) {
			this.lookAtX = x;
			this.lookAtY = y;
			this.lookAtZ = z;
			return this;
		}

		public TeleportRequestBuilder noFly(boolean noFly) {
			this.noFly = noFly;
			return this;
		}

		public TeleportRequestBuilder parameter(String name, Object value) {
			if (name != null && !name.isBlank()) {
				extraParams.put(name, value);
			}
			return this;
		}

		public TeleportRequest build() {
			return new TeleportRequest(agentId, regionName, regionUuid, localX, localY, localZ, lookAtX, lookAtY, lookAtZ,
					noFly, extraParams);
		}
	}

	private static boolean looksLikeUuid(String value) {
		if (value == null || value.length() != 36) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			var c = value.charAt(i);
			if (i == 8 || i == 13 || i == 18 || i == 23) {
				if (c != '-') {
					return false;
				}
				continue;
			}
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
				return false;
			}
		}
		return true;
	}

	private static void throwIfRemoteAdminError(String methodName, Object response) {
		if (!(response instanceof Map<?, ?> map)) {
			return;
		}

		var successValue = getFieldIgnoreCase(map, "success");
		if (successValue.isEmpty()) {
			return;
		}

		if (!isExplicitFailure(successValue.get())) {
			return;
		}

		var errorValue = getFieldIgnoreCase(map, "error").orElse(null);
		var errorMessage = errorValue == null ? "" : String.valueOf(errorValue).trim();
		if (!errorMessage.isEmpty()) {
			throw new IllegalStateException(errorMessage);
		}
		throw new IllegalStateException("RemoteAdmin call failed for method '" + methodName + "'.");
	}

	private static Optional<Object> getFieldIgnoreCase(Map<?, ?> map, String key) {
		for (var entry : map.entrySet()) {
			if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
				return Optional.ofNullable(entry.getValue());
			}
		}
		return Optional.empty();
	}

	private static boolean isExplicitFailure(Object successValue) {
		if (successValue == null) {
			return false;
		}
		if (successValue instanceof Boolean bool) {
			return !bool;
		}
		if (successValue instanceof Number number) {
			return number.intValue() == 0;
		}
		var text = String.valueOf(successValue).trim();
		return "0".equals(text) || "false".equalsIgnoreCase(text);
	}

	private static void throwIfNotAccepted(String methodName, Map<String, Object> response) {
		var accepted = getFieldIgnoreCase(response, "accepted");
		if (accepted.isEmpty()) {
			return;
		}
		var acceptedBoolean = toBoolean(accepted.get())
				.orElseThrow(() -> new IllegalStateException(
						methodName + " response field 'accepted' is not a valid boolean: " + accepted.get()));
		if (!acceptedBoolean) {
			var errorText = getFieldIgnoreCase(response, "error").map(String::valueOf).orElse("").trim();
			throw new RequestNotAcceptedException(methodName,
					errorText.isEmpty() ? "Request was not accepted." : errorText);
		}
	}

	private static Optional<Boolean> toBoolean(Object value) {
		if (value == null) {
			return Optional.empty();
		}
		if (value instanceof Boolean bool) {
			return Optional.of(bool);
		}
		if (value instanceof Number number) {
			return Optional.of(number.intValue() != 0);
		}
		var text = String.valueOf(value).trim();
		if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
			return Optional.of(true);
		}
		if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
			return Optional.of(false);
		}
		return Optional.empty();
	}

	public static CreateRegionBuilder createRegionBuilder(String regionName, String listenIp, int listenPort,
			String externalAddress, int regionX, int regionY, String estateName) {
		return new CreateRegionBuilder(regionName, listenIp, listenPort, externalAddress, regionX, regionY, estateName);
	}

	public static ShutdownRequestBuilder shutdownRequestBuilder() {
		return new ShutdownRequestBuilder();
	}

	public static LoadHeightmapBuilder loadHeightmapByName(String regionName, String filename) {
		return new LoadHeightmapBuilder(regionName, null, filename);
	}

	public static LoadHeightmapBuilder loadHeightmapById(String regionId, String filename) {
		return new LoadHeightmapBuilder(null, regionId, filename);
	}

	public static SaveOarBuilder saveOarByName(String regionName, String filename) {
		return new SaveOarBuilder(regionName, null, filename);
	}

	public static SaveOarBuilder saveOarById(String regionId, String filename) {
		return new SaveOarBuilder(null, regionId, filename);
	}

	public static SaveXmlBuilder saveXmlByName(String regionName, String filename) {
		return new SaveXmlBuilder(regionName, null, filename);
	}

	public static SaveXmlBuilder saveXmlById(String regionId, String filename) {
		return new SaveXmlBuilder(null, regionId, filename);
	}
	
	public record AgentLocation(String regionId, String regionName, Agent agent) {}

	public record CreateRegionResponse(String regionName, String regionUuid) {
	}

	public record LoadHeightmapRequest(String regionName, String regionId, String filename, Boolean merge,
			Boolean skipAssets) {

		public LoadHeightmapRequest {
			if ((regionName == null || regionName.isBlank()) && (regionId == null || regionId.isBlank())) {
				throw new IllegalArgumentException("either regionName or regionId must be provided");
			}
			requireNonBlank(filename, "filename");
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			putIfPresent(params, "region_name", regionName);
			putIfPresent(params, "region_id", regionId);
			params.put("filename", filename);
			if (merge != null) {
				params.put("merge", String.valueOf(merge));
			}
			if (skipAssets != null) {
				params.put("skip-assets", String.valueOf(skipAssets));
			}
			return params;
		}
	}

	public static final class LoadHeightmapBuilder {
		private final String regionName;
		private final String regionId;
		private final String filename;
		private Boolean merge;
		private Boolean skipAssets;

		private LoadHeightmapBuilder(String regionName, String regionId, String filename) {
			this.regionName = regionName;
			this.regionId = regionId;
			this.filename = filename;
		}

		public LoadHeightmapBuilder merge(boolean merge) {
			this.merge = merge;
			return this;
		}

		public LoadHeightmapBuilder skipAssets(boolean skipAssets) {
			this.skipAssets = skipAssets;
			return this;
		}

		public LoadHeightmapRequest build() {
			return new LoadHeightmapRequest(regionName, regionId, filename, merge, skipAssets);
		}
	}

	public record SaveOarRequest(String regionName, String regionId, String filename, String profile, String perm) {

		public SaveOarRequest {
			if ((regionName == null || regionName.isBlank()) && (regionId == null || regionId.isBlank())) {
				throw new IllegalArgumentException("either regionName or regionId must be provided");
			}
			requireNonBlank(filename, "filename");
			if (perm != null && !perm.isBlank()) {
				var normalized = perm.trim();
				if (!("C".equals(normalized) || "T".equals(normalized) || "CT".equals(normalized))) {
					throw new IllegalArgumentException("perm must be one of C, T, CT");
				}
			}
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			putIfPresent(params, "region_name", regionName);
			putIfPresent(params, "region_id", regionId);
			params.put("filename", filename);
			putIfPresent(params, "profile", profile);
			putIfPresent(params, "perm", perm);
			return params;
		}
	}

	public static final class SaveOarBuilder {
		private final String regionName;
		private final String regionId;
		private final String filename;
		private String profile;
		private String perm;

		private SaveOarBuilder(String regionName, String regionId, String filename) {
			this.regionName = regionName;
			this.regionId = regionId;
			this.filename = filename;
		}

		public SaveOarBuilder profile(String profile) {
			this.profile = profile;
			return this;
		}

		public SaveOarBuilder perm(String perm) {
			this.perm = perm;
			return this;
		}

		public SaveOarRequest build() {
			return new SaveOarRequest(regionName, regionId, filename, profile, perm);
		}
	}

	public record SaveXmlRequest(String regionName, String regionId, String filename, Integer xmlVersion) {

		public SaveXmlRequest {
			if ((regionName == null || regionName.isBlank()) && (regionId == null || regionId.isBlank())) {
				throw new IllegalArgumentException("either regionName or regionId must be provided");
			}
			requireNonBlank(filename, "filename");
			if (xmlVersion != null) {
				validateXmlVersion(xmlVersion);
			}
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			putIfPresent(params, "region_name", regionName);
			putIfPresent(params, "region_id", regionId);
			params.put("filename", filename);
			params.put("xml_version", xmlVersion == null ? 1 : xmlVersion);
			return params;
		}
	}

	public static final class SaveXmlBuilder {
		private final String regionName;
		private final String regionId;
		private final String filename;
		private Integer xmlVersion;

		private SaveXmlBuilder(String regionName, String regionId, String filename) {
			this.regionName = regionName;
			this.regionId = regionId;
			this.filename = filename;
		}

		public SaveXmlBuilder xmlVersion(int xmlVersion) {
			this.xmlVersion = xmlVersion;
			return this;
		}

		public SaveXmlRequest build() {
			return new SaveXmlRequest(regionName, regionId, filename, xmlVersion);
		}
	}

	public record CreateRegionRequest(String regionName, String listenIp, int listenPort, String externalAddress,
			int regionX, int regionY, String estateName, String regionId, String estateOwnerUuid,
			String estateOwnerFirst, String estateOwnerLast, Boolean persist, String regionFile, Boolean isPublic,
			Boolean enableVoice, String heightmapFile) {

		public CreateRegionRequest {
			requireNonBlank(regionName, "regionName");
			requireNonBlank(listenIp, "listenIp");
			requireNonBlank(externalAddress, "externalAddress");
			requireNonBlank(estateName, "estateName");
			if (listenPort <= 0 || listenPort > 65535) {
				throw new IllegalArgumentException("listenPort must be between 1 and 65535");
			}
			if (Boolean.TRUE.equals(persist) && regionFile != null && regionFile.isBlank()) {
				throw new IllegalArgumentException("regionFile must not be blank when provided.");
			}
			if (!Boolean.TRUE.equals(persist) && regionFile != null && !regionFile.isBlank()) {
				throw new IllegalArgumentException("regionFile requires persist=true.");
			}
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			params.put("region_name", regionName);
			params.put("listen_ip", listenIp);
			params.put("listen_port", listenPort);
			params.put("external_address", externalAddress);
			params.put("region_x", regionX);
			params.put("region_y", regionY);
			params.put("estate_name", estateName);
			putIfPresent(params, "region_id", regionId);
			putIfPresent(params, "estate_owner_uuid", estateOwnerUuid);
			putIfPresent(params, "estate_owner_first", estateOwnerFirst);
			putIfPresent(params, "estate_owner_last", estateOwnerLast);
			if (persist != null) {
				params.put("persist", persist);
			}
			putIfPresent(params, "region_file", regionFile);
			if (isPublic != null) {
				params.put("public", isPublic);
			}
			if (enableVoice != null) {
				params.put("enable_voice", enableVoice);
			}
			putIfPresent(params, "heightmap_file", heightmapFile);
			return params;
		}
	}

	public static final class CreateRegionBuilder {
		private final String regionName;
		private final String listenIp;
		private final int listenPort;
		private final String externalAddress;
		private final int regionX;
		private final int regionY;
		private final String estateName;
		private String regionId;
		private String estateOwnerUuid;
		private String estateOwnerFirst;
		private String estateOwnerLast;
		private Boolean persist;
		private String regionFile;
		private Boolean isPublic;
		private Boolean enableVoice;
		private String heightmapFile;

		private CreateRegionBuilder(String regionName, String listenIp, int listenPort, String externalAddress,
				int regionX, int regionY, String estateName) {
			this.regionName = regionName;
			this.listenIp = listenIp;
			this.listenPort = listenPort;
			this.externalAddress = externalAddress;
			this.regionX = regionX;
			this.regionY = regionY;
			this.estateName = estateName;
		}

		public CreateRegionBuilder regionId(String regionId) {
			this.regionId = regionId;
			return this;
		}

		public CreateRegionBuilder estateOwnerUuid(String estateOwnerUuid) {
			this.estateOwnerUuid = estateOwnerUuid;
			return this;
		}

		public CreateRegionBuilder estateOwnerFirst(String estateOwnerFirst) {
			this.estateOwnerFirst = estateOwnerFirst;
			return this;
		}

		public CreateRegionBuilder estateOwnerLast(String estateOwnerLast) {
			this.estateOwnerLast = estateOwnerLast;
			return this;
		}

		public CreateRegionBuilder persist(boolean persist) {
			this.persist = persist;
			return this;
		}

		public CreateRegionBuilder regionFile(String regionFile) {
			this.regionFile = regionFile;
			return this;
		}

		public CreateRegionBuilder isPublic(boolean isPublic) {
			this.isPublic = isPublic;
			return this;
		}

		public CreateRegionBuilder enableVoice(boolean enableVoice) {
			this.enableVoice = enableVoice;
			return this;
		}

		public CreateRegionBuilder heightmapFile(String heightmapFile) {
			this.heightmapFile = heightmapFile;
			return this;
		}

		public CreateRegionRequest build() {
			return new CreateRegionRequest(regionName, listenIp, listenPort, externalAddress, regionX, regionY,
					estateName, regionId, estateOwnerUuid, estateOwnerFirst, estateOwnerLast, persist, regionFile,
					isPublic, enableVoice, heightmapFile);
		}
	}

	public record ShutdownRequest(String shutdown, Integer milliseconds, String noticeType) {

		public ShutdownRequest {
			if (shutdown != null && shutdown.isBlank()) {
				throw new IllegalArgumentException("shutdown must not be blank when provided.");
			}
			if (noticeType != null && noticeType.isBlank()) {
				throw new IllegalArgumentException("noticeType must not be blank when provided.");
			}
			if (milliseconds != null && milliseconds < 0) {
				throw new IllegalArgumentException("milliseconds must be >= 0 when provided.");
			}
		}

		Map<String, Object> toParams() {
			var params = new LinkedHashMap<String, Object>();
			putIfPresent(params, "shutdown", shutdown);
			if (milliseconds != null) {
				params.put("milliseconds", milliseconds);
			}
			putIfPresent(params, "noticetype", noticeType);
			return params;
		}
	}

	public static final class ShutdownRequestBuilder {
		private String shutdown;
		private Integer milliseconds;
		private String noticeType;

		private ShutdownRequestBuilder() {
		}

		public ShutdownRequestBuilder delayed(int milliseconds) {
			this.shutdown = "delayed";
			this.milliseconds = milliseconds;
			return this;
		}

		public ShutdownRequestBuilder noticeTypeDialog() {
			this.noticeType = "dialog";
			return this;
		}

		public ShutdownRequestBuilder noticeTypeNone() {
			this.noticeType = "none";
			return this;
		}

		public ShutdownRequestBuilder noticeType(String noticeType) {
			this.noticeType = noticeType;
			return this;
		}

		public ShutdownRequest build() {
			return new ShutdownRequest(shutdown, milliseconds, noticeType);
		}
	}

	public static final class RequestNotAcceptedException extends IllegalStateException {
		private static final long serialVersionUID = 1L;

		public RequestNotAcceptedException(String methodName, String message) {
			super(methodName + " not accepted: " + message);
		}
	}

	public record Region(String name, String uuid, List<Agent> agents) {
		public Region {
			agents = agents == null ? List.of() : List.copyOf(agents);
		}
	}

	public record Agent(String name, String uuid, String type, String currentParcelUuid, Double posX, Double posY,
			Double posZ, Double velX, Double velY, Double velZ, Double lookatX, Double lookatY, Double lookatZ,
			Boolean isFlying, Boolean isSatOnGround, Boolean isSatOnObject) {
	}
}
