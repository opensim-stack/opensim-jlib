package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
 
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientTest {

	@Test
	void buildMethodCallIncludesStructPayload() {
		var params = new LinkedHashMap<String, Object>();
		params.put("password", "changeme");
		params.put("region_name", "Sandbox");
		params.put("include_children", true);
		var xml = OpensimRemoteAdminClient.buildMethodCallForTest("admin_get_agents", params);

		assertTrue(xml.contains("<methodName>admin_get_agents</methodName>"));
		assertTrue(xml.contains("<name>region_name</name>"));
		assertTrue(xml.contains("<string>Sandbox</string>"));
		assertTrue(xml.contains("<name>include_children</name>"));
		assertTrue(xml.contains("<boolean>1</boolean>"));
	}

	@Test
	void parseMethodResponseParsesStructAndArray() {
		var xml = """
				<?xml version=\"1.0\"?>
				<methodResponse>
				  <params>
				    <param>
				      <value>
				        <struct>
				          <member>
				            <name>success</name>
				            <value><boolean>1</boolean></value>
				          </member>
				          <member>
				            <name>agents</name>
				            <value>
				              <array>
				                <data>
				                  <value>
				                    <struct>
				                      <member><name>name</name><value><string>Agent One</string></value></member>
				                    </struct>
				                  </value>
				                </data>
				              </array>
				            </value>
				          </member>
				        </struct>
				      </value>
				    </param>
				  </params>
				</methodResponse>
				""";

		var parsed = OpensimRemoteAdminClient.parseMethodResponseForTest(xml);
		var result = assertInstanceOf(Map.class, parsed);
		assertEquals(Boolean.TRUE, result.get("success"));

		var agents = assertInstanceOf(List.class, result.get("agents"));
		var firstAgent = assertInstanceOf(Map.class, agents.get(0));
		assertEquals("Agent One", firstAgent.get("name"));
	}

	@Test
	void parseMethodResponseThrowsOnFault() {
		var xml = """
				<?xml version=\"1.0\"?>
				<methodResponse>
				  <fault>
				    <value>
				      <struct>
				        <member><name>faultCode</name><value><int>4</int></value></member>
				        <member><name>faultString</name><value><string>Unknown method</string></value></member>
				      </struct>
				    </value>
				  </fault>
				</methodResponse>
				""";

		var ex = assertThrows(IllegalStateException.class,
				() -> OpensimRemoteAdminClient.parseMethodResponseForTest(xml));
		assertTrue(ex.getMessage().contains("fault"));
	}

	@Test
	void teleportBuilderBuildsExpectedRecord() {
		var request = OpensimRemoteAdminClient
				.teleportRequestBuilder("agent-uuid", "Sandbox")
				.position(128.0, 128.0, 25.0)
				.lookAt(0.0, 1.0, 0.0)
				.noFly(true)
				.build();

		assertEquals("agent-uuid", request.agentId());
		assertEquals("Sandbox", request.regionName());
		assertEquals(128.0, request.localX());
		assertEquals(Boolean.TRUE, request.noFly());
	}

	@Test
	void throwIfRemoteAdminErrorThrowsWithErrorFieldMessage() {
		var ex = assertThrows(IllegalStateException.class, () -> OpensimRemoteAdminClient
				.throwIfRemoteAdminErrorForTest("admin_get_agents", Map.of("success", false, "error", "boom")));
		assertEquals("boom", ex.getMessage());
	}

	@Test
	void throwIfRemoteAdminErrorAllowsSuccessOrMissingSuccess() {
		OpensimRemoteAdminClient.throwIfRemoteAdminErrorForTest("admin_get_agents", Map.of("success", true));
		OpensimRemoteAdminClient.throwIfRemoteAdminErrorForTest("admin_get_agents", Map.of("agents", List.of()));
	}

	@Test
	void throwIfRemoteAdminErrorUsesFallbackMessageWhenNoErrorProvided() {
		var ex = assertThrows(IllegalStateException.class,
				() -> OpensimRemoteAdminClient.throwIfRemoteAdminErrorForTest("admin_get_agents", Map.of("success", 0)));
		assertTrue(ex.getMessage().contains("admin_get_agents"));
	}

	@Test
	void throwIfNotAcceptedThrowsDedicatedException() {
		var ex = assertThrows(OpensimRemoteAdminClient.RequestNotAcceptedException.class,
				() -> OpensimRemoteAdminClient.throwIfNotAcceptedForTest("admin_restart",
						new LinkedHashMap<>(Map.of("accepted", false, "error", "busy"))));
		assertTrue(ex.getMessage().contains("admin_restart"));
		assertTrue(ex.getMessage().contains("busy"));
	}

	@Test
	void createRegionBuilderBuildsRequestWithOptionalFlags() {
		var request = OpensimRemoteAdminClient
				.createRegionBuilder("MyRegion", "0.0.0.0", 9000, "127.0.0.1", 1000, 1000, "Main Estate")
				.regionId("11111111-2222-3333-4444-555555555555")
				.persist(true)
				.regionFile("Regions/MyRegion.ini")
				.isPublic(true)
				.enableVoice(false)
				.heightmapFile("terrain.r32")
				.build();

		assertEquals("MyRegion", request.regionName());
		assertEquals("11111111-2222-3333-4444-555555555555", request.regionId());
		assertEquals(Boolean.TRUE, request.persist());
		assertEquals("Regions/MyRegion.ini", request.regionFile());

		var params = request.toParams();
		assertEquals(true, params.get("persist"));
		assertEquals(true, params.get("public"));
		assertEquals(false, params.get("enable_voice"));
	}

	@Test
	void shutdownRequestBuilderBuildsDelayedDialogRequest() {
		var request = OpensimRemoteAdminClient.shutdownRequestBuilder()
				.delayed(2500)
				.noticeTypeDialog()
				.build();

		assertEquals("delayed", request.shutdown());
		assertEquals(2500, request.milliseconds());
		assertEquals("dialog", request.noticeType());
	}

	@Test
	void loadHeightmapBuilderBuildsExpectedRequest() {
		var request = OpensimRemoteAdminClient.loadHeightmapByName("Region", "/tmp/hm.r32")
				.merge(true)
				.skipAssets(false)
				.build();

		assertEquals("Region", request.regionName());
		assertEquals("/tmp/hm.r32", request.filename());
		assertEquals(Boolean.TRUE, request.merge());
	}

	@Test
	void saveOarBuilderBuildsExpectedRequest() {
		var request = OpensimRemoteAdminClient.saveOarById("11111111-2222-3333-4444-555555555555", "/tmp/world.oar")
				.profile("https://profiles.example")
				.perm("CT")
				.build();

		assertEquals("11111111-2222-3333-4444-555555555555", request.regionId());
		assertEquals("CT", request.perm());
	}

	@Test
	void saveXmlBuilderRejectsInvalidVersion() {
		assertThrows(IllegalArgumentException.class,
				() -> OpensimRemoteAdminClient.saveXmlByName("Region", "/tmp/world.xml").xmlVersion(3).build());
	}
}
