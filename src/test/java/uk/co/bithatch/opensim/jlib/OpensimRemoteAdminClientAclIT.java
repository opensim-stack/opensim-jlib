package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientAclIT {

	@Test
	void aclFlowWorksAgainstLiveServer() {
		var client = createClientFromProperties();
		var suffix = randomSuffix();

		var ownerFirst = "AclOwner" + suffix;
		var ownerLast = "User" + suffix;
		client.createUser(ownerFirst, ownerLast, "Pass-" + suffix, 1000, 1000);

		var aclUser1First = "AclOne" + suffix;
		var aclUser1Last = "User" + suffix;
		client.createUser(aclUser1First, aclUser1Last, "PassA-" + suffix, 1000, 1000);
		var aclUser1 = aclUser1First + " " + aclUser1Last;

		var aclUser2First = "AclTwo" + suffix;
		var aclUser2Last = "User" + suffix;
		client.createUser(aclUser2First, aclUser2Last, "PassB-" + suffix, 1000, 1000);
		var aclUser2 = aclUser2First + " " + aclUser2Last;

		var region = client.createRegion(OpensimRemoteAdminClient
				.createRegionBuilder("ITAclRegion" + suffix, "0.0.0.0", randomPort(), "127.0.0.1", randomGrid(),
						randomGrid(), "IT-AclEstate-" + suffix)
				.regionId(UUID.randomUUID().toString())
				.estateOwnerFirst(ownerFirst)
				.estateOwnerLast(ownerLast)
				.build());

		assertNotNull(region);
		assertTrue(!region.regionName().isBlank());
		assertTrue(!region.regionUuid().isBlank());

		client.aclClearByName(region.regionName());
		client.aclAddByName(region.regionName(), aclUser1, aclUser2);
		var listedByName = client.aclListByName(region.regionName());
		assertTrue(listedByName.contains(aclUser1));
		assertTrue(listedByName.contains(aclUser2));

		client.aclAddById(region.regionUuid(), List.of(aclUser1));
		var listedById = client.aclListById(region.regionUuid());
		assertTrue(listedById.contains(aclUser1));

		var removedById = client.aclRemoveById(region.regionUuid(), aclUser2);
		assertTrue(removedById >= 1);

		var removedByName = client.aclRemoveByName(region.regionName(), List.of(aclUser1));
		assertTrue(removedByName >= 1);

		client.aclClearById(region.regionUuid());
		var listedAfterClear = client.aclListByName(region.regionName());
		assertNotNull(listedAfterClear);
		assertTrue(listedAfterClear.isEmpty());

		var deletedIdentity = client.deleteRegion(region.regionName());
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
		return 15000 + (int) (Math.random() * 1000);
	}

	private static int randomGrid() {
		return 4000 + (int) (Math.random() * 1000);
	}
}
