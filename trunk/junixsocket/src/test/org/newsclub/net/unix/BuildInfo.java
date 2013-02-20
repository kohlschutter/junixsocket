package org.newsclub.net.unix;

import org.junit.Test;


public class BuildInfo {
	@Test
	public void junixInfo() {
		System.out.println("NativeUnixSocket#isLoaded: "+NativeUnixSocket.isLoaded());
		System.out.println("Loaded library from: "+System.getProperty("org.newsclub.net.unix.library.loaded"));
	}
}
