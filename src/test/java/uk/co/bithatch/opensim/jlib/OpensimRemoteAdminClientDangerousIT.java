package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class OpensimRemoteAdminClientDangerousIT {

	@Test
	void broadcastRestartAndShutdownCanBeInvokedWhenDangerousModeIsEnabled() {
		assumeTrue(Boolean.getBoolean("opensim.remoteadmin.allowDangerous"),
				"Set -Dopensim.remoteadmin.allowDangerous=true to run dangerous integration tests.");

		var client = createClientFromProperties();
		client.broadcast("Integration test broadcast");

		var restartRegionId = System.getProperty("opensim.remoteadmin.dangerous.regionId");
		if (restartRegionId != null && !restartRegionId.isBlank()) {
			var rebooting = client.restart(restartRegionId);
			assertTrue(rebooting || !rebooting);
		}

		var shutdownRequest = OpensimRemoteAdminClient.shutdownRequestBuilder()
				.delayed(120000)
				.noticeTypeNone()
				.build();
		client.shutdown(shutdownRequest);
		assertNotNull(shutdownRequest);
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
}
