package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientRegionFilesIT {

	@Test
	void saveAndLoadRegionFilesFlowWorksAgainstLiveServer() {
		var client = createClientFromProperties();
		var suffix = randomSuffix();

		var ownerFirst = "FileOwner" + suffix;
		var ownerLast = "User" + suffix;
		client.createUser(ownerFirst, ownerLast, "Pass-" + suffix, 1000, 1000);

		var region = client.createRegion(OpensimRemoteAdminClient
				.createRegionBuilder("ITFileRegion" + suffix, "0.0.0.0", randomPort(), "127.0.0.1", randomGrid(),
						randomGrid(), "IT-FileEstate-" + suffix)
				.regionId(UUID.randomUUID().toString())
				.estateOwnerFirst(ownerFirst)
				.estateOwnerLast(ownerLast)
				.build());

		assertNotNull(region);
		var regionName = region.regionName();
		var regionId = region.regionUuid();
		assertTrue(!regionName.isBlank());
		assertTrue(!regionId.isBlank());

		var basePath = "/tmp/opensim-jlib-it-" + suffix;
		var heightmapFile = basePath + ".r32";
		var oarFile = basePath + ".oar";

		client.saveHeightmapByName(regionName, heightmapFile);
		client.loadHeightmap(OpensimRemoteAdminClient.loadHeightmapById(regionId, heightmapFile)
				.merge(false)
				.skipAssets(true)
				.build());

		client.saveOar(OpensimRemoteAdminClient.saveOarByName(regionName, oarFile).build());
		client.loadOarById(regionId, oarFile);

		var deletedIdentity = client.deleteRegion(regionName);
		assertTrue(!deletedIdentity.isBlank());
	}

	private static OpensimRemoteAdminClient createClientFromProperties() {
		var endpoint = System.getProperty("opensim.remoteadmin.endpoint");
		var password = System.getProperty("opensim.remoteadmin.password");
		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalStateException("System property opensim.remoteadmin.endpoint is required.");
		}
		if (password == null || password.isBlank()) {
			throw new IllegalStateException("System property opensim.remoteadmin.password is required.");
		}
		return new OpensimRemoteAdminClient(endpoint, password);
	}

	private static String randomSuffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	private static int randomPort() {
		return 18000 + (int) (Math.random() * 500);
	}

	private static int randomGrid() {
		return 5000 + (int) (Math.random() * 500);
	}
}
