package org.newsclub.net.unix;

import org.junit.Test;


public class BuildInfo {
	@Test
	public void junixInfo() {
		System.out.println("NativeUnixSocket#isSupported: "+NativeUnixSocket.isSupported());
		System.out.println("Loaded library from: "+System.getProperty("org.newsclub.net.unix.library.loaded"));
	}
}
