/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.rmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.server.RMISocketFactory;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;

import com.kohlschutter.util.IOUtil;

@AFSocketCapabilityRequirement({
    AFSocketCapability.CAPABILITY_UNIX_DOMAIN, AFSocketCapability.CAPABILITY_FILE_DESCRIPTORS})
public class RemoteFileDescriptorTest extends TestBase {
  private static final byte[] HELLO_WORLD = "Hello World :-)\n".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] SMILEY = ":-)\n".getBytes(StandardCharsets.US_ASCII);

  @Test
  public void testServiceProxy() throws IOException, NotBoundException {
    TestService svc = lookupTestService();
    assertTrue(Proxy.isProxyClass(svc.getClass()));
  }

  @Test
  public void testRemoteStdout() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    try (RemoteFileDescriptor stdout = svc.stdout()) {
      try (FileOutputStream fos = new FileOutputStream(stdout.getFileDescriptor())) {
        // fos.write(SMILEY); // uncomment to write a smiley to stdout
        fos.flush();
      }
    }
  }

  @Test
  public void testRemoteStdoutNoop() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    try (RemoteFileDescriptor unused = svc.stdout()) {
      // not doing anything here should trigger descriptor cleanup in RemoteFileDescriptor#close
    }
  }

  @Test
  public void testWriteAndReadHello() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    try (FileOutputStream fos = svc.output().asFileOutputStream()) {
      fos.write(HELLO_WORLD);
    }
    svc.verifyContents(HELLO_WORLD);

    try (FileInputStream fin = svc.input(12).asFileInputStream()) {
      byte[] data = IOUtil.readAllBytes(fin);
      assertArrayEquals(SMILEY, data);
    }

    try (NaiveFileInputStreamRemote rfis = svc.naiveInputStreamRemote();
        FileInputStream fin = rfis.getRemoteFileDescriptor().asFileInputStream()) {
      assertEquals('H', rfis.read());
      assertEquals('e', fin.read());
      assertEquals('l', fin.read());
      assertEquals('l', fin.read());
      fin.close(); // it's OK to close the remote file descriptor we received via RMI
      assertEquals('o', rfis.read());
    }
  }

  @Test
  public void testFindSocketFactory() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    RemotePeerInfo rci = RemotePeerInfo.getConnectionInfo(svc);
    RMISocketFactory factory = rci.getSocketFactory();
    assertNotNull(factory);
    assertEquals(namingSocketFactory(), factory);
  }

  @Test
  public void testReadWrite() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    byte[] expected = new byte[5000];
    for (int i = 0; i < expected.length; i++) {
      expected[i] = (byte) ((i + 123) % 256);
    }

    try (FileOutputStream fos = svc.output().asFileOutputStream()) {
      fos.write(expected);
    }

    byte[] actual;
    try (FileInputStream fin = svc.input().asFileInputStream()) {
      actual = IOUtil.readAllBytes(fin);
    }
    assertArrayEquals(expected, actual);

    try (NaiveFileInputStreamRemote fin = svc.naiveInputStreamRemote()) {
      actual = fin.readAllBytes();
    }
    assertArrayEquals(expected, actual);
  }
}
