package org.newsclub.net.unix.vsock;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFVSOCKSocketAddress;
import org.newsclub.net.unix.SocketTestBase;

public class AFVSOCKExtensionsTest extends SocketTestBase<AFVSOCKSocketAddress> {
  public AFVSOCKExtensionsTest() throws IOException {
    super(AFVSOCKAddressSpecifics.INSTANCE);
  }

  @Test
  public void testGetLocalID() throws Exception {
    int cid = AFVSOCKSocket.getLocalCID();

    if (cid == AFVSOCKSocketAddress.VMADDR_CID_ANY) {
      if (AFSocket.supports(AFSocketCapability.CAPABILITY_VSOCK)) {
        System.out.println("Local CID: " + cid + " (this could mean VSOCK is not supported)");
        System.out.println("Warning: We thought VSOCK was supported but CID is not set correctly");
      }
    } else {
      System.out.println("Local CID: " + cid);
      if (!AFSocket.supports(AFSocketCapability.CAPABILITY_VSOCK)) {
        System.out.println(
            "Warning: We thought VSOCK was not supported but CID returned some value other than -1");
      }
    }
  }
}
