package uk.co.bithatch.opensim.jlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.Test;

class IOTest {

	@Test
	void getFilenamePrefersRfc5987FilenameStar() {
		var header = "attachment; filename=\"=?UTF-8?Q?User-Avatars-ALL-1.0.0.iar?=\"; filename*=UTF-8''User-Avatars-ALL-1.0.0.iar";
		assertEquals("User-Avatars-ALL-1.0.0.iar", IO.getFilename(connectionWithContentDisposition(header)));
	}

	@Test
	void getFilenameFallsBackToPlainFilename() {
		var header = "attachment; filename=\"IAR-FULLAVATARS.iar\"";
		assertEquals("IAR-FULLAVATARS.iar", IO.getFilename(connectionWithContentDisposition(header)));
	}

	@Test
	void getFilenameDecodesPercentEncodedFilenameStar() {
		var header = "attachment; filename*=UTF-8''hello%20world%2Bavatars.iar";
		assertEquals("hello world+avatars.iar", IO.getFilename(connectionWithContentDisposition(header)));
	}

	@Test
	void getFilenameReturnsNullWithoutContentDisposition() {
		assertNull(IO.getFilename(connectionWithContentDisposition(null)));
	}

	private static HttpURLConnection connectionWithContentDisposition(String value) {
		return new HttpURLConnection((URL) null) {
			@Override
			public String getHeaderField(String name) {
				if ("Content-Disposition".equalsIgnoreCase(name)) {
					return value;
				}
				return null;
			}

			@Override
			public void disconnect() {
			}

			@Override
			public boolean usingProxy() {
				return false;
			}

			@Override
			public void connect() {
			}
		};
	}
}
