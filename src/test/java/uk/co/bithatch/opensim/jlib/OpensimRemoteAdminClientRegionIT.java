package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientRegionIT {

	@Test
	void createModifyQueryDeleteRegionFlowWorksAgainstLiveServer() {
		var client = createClientFromProperties();
		var suffix = randomSuffix();
		var ownerFirst = "Estate" + suffix;
		var ownerLast = "Owner" + suffix;

		client.createUser(ownerFirst, ownerLast, "Pass-" + suffix, 1000, 1000);

		var request = OpensimRemoteAdminClient
				.createRegionBuilder("ITRegion" + suffix, "0.0.0.0", randomPort(), "127.0.0.1", randomGrid(),
						randomGrid(), "IT-Estate-" + suffix)
				.regionId(UUID.randomUUID().toString())
				.estateOwnerFirst(ownerFirst)
				.estateOwnerLast(ownerLast)
				.build();

		var created = client.createRegion(request);
		assertNotNull(created);
		assertTrue(!created.regionName().isBlank());
		assertTrue(!created.regionUuid().isBlank());

		var health = client.regionQueryByName(created.regionName());
		assertTrue(health >= 0);

		var modifiedIdentity = client.modifyRegionByName(created.regionName(), true, true);
		assertTrue(!modifiedIdentity.isBlank());

		var deletedIdentity = client.deleteRegion(created.regionName());
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
		return 14000 + (int) (Math.random() * 1000);
	}

	private static int randomGrid() {
		return 3000 + (int) (Math.random() * 1000);
	}
}
