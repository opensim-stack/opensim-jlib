package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientIT {
	private static final int START_REGION_X = 1000;
	private static final int START_REGION_Y = 1000;

	@Test
	void userManagementCreateExistsUpdateAuthenticateFlowWorksAgainstLiveServer() {
		var client = createClientFromProperties();
		var suffix = randomSuffix();
		var first = "IT" + suffix;
		var last = "User" + suffix;

		var initialPassword = "PassA-" + suffix;
		var createdAvatarUuid = client.createUser(first, last, initialPassword, START_REGION_X, START_REGION_Y,
				"it+" + suffix + "@example.com");
		assertNotNull(createdAvatarUuid);
		assertNotEquals("", createdAvatarUuid.trim());

		Instant lastLogin = client.existsUser(first, last);
		assertNotNull(lastLogin);

		var updatedAvatarUuidByPassword = client.updateUserPassword(first, last, "PassB-" + suffix);
		assertEquals(createdAvatarUuid, updatedAvatarUuidByPassword);

		var tokenAfterPasswordUpdate = client.authenticateUser(first, last, ("PassB-" + suffix).toCharArray(), 30);
		assertNotNull(tokenAfterPasswordUpdate);
		assertNotEquals("", tokenAfterPasswordUpdate.trim());

		var updatedAvatarUuidByStart = client.updateUserStart(first, last, START_REGION_X, START_REGION_Y);
		assertEquals(createdAvatarUuid, updatedAvatarUuidByStart);

		var updatedAvatarUuidByCombined = client.updateUser(first, last, "PassC-" + suffix, START_REGION_X,
				START_REGION_Y);
		assertEquals(createdAvatarUuid, updatedAvatarUuidByCombined);

		var tokenAfterCombinedUpdate = client.authenticateUser(first, last, ("PassC-" + suffix).toCharArray(), 30);
		assertNotNull(tokenAfterCombinedUpdate);
		assertNotEquals("", tokenAfterCombinedUpdate.trim());
	}

	@Test
	void createUserWithoutEmailOverloadWorksAgainstLiveServer() {
		var client = createClientFromProperties();
		var suffix = randomSuffix();
		var first = "ITNoMail" + suffix;
		var last = "User" + suffix;
		var avatarUuid = client.createUser(first, last, "PassD-" + suffix, START_REGION_X, START_REGION_Y);
		assertTrue(!avatarUuid.isBlank());
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
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}
}