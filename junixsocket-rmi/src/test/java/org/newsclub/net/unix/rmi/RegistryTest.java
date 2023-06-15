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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.server.ExportException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
@AFSocketCapabilityRequirement({AFSocketCapability.CAPABILITY_UNIX_DOMAIN})
public class RegistryTest extends ShutdownHookTestBase {
  @Test
  public void testDoubleCreateRegistry() throws IOException {
    AFNaming naming = AFUNIXNaming.newPrivateInstance();
    naming.createRegistry();
    naming.createRegistry();
    naming.shutdownRegistry();
  }

  @Test
  public void testExportAndBind() throws IOException, AlreadyBoundException, NotBoundException {
    AFUNIXNaming naming = AFUNIXNaming.newPrivateInstance();

    assertEquals(naming.getRegistrySocketDir(), naming.getSocketFactory().getSocketDir());

    naming.createRegistry();
    assertEquals(Arrays.asList(AFRMIService.class.getName()), Arrays.asList(naming.list()));

    Hello hello = new HelloImpl();

    assertThrows(NotBoundException.class, () -> naming.lookup("hello", 1, TimeUnit.MILLISECONDS));

    naming.exportAndBind("hello", hello);
    assertThrows(ExportException.class, () -> naming.exportAndBind("hello", hello));
    naming.bind("hello2", hello);
    naming.rebind("hello2", hello);
    assertThrows(AlreadyBoundException.class, () -> naming.bind("hello2", hello));
    assertEquals(new HashSet<>(Arrays.asList(AFRMIService.class.getName(), "hello", "hello2")),
        new HashSet<>(Arrays.asList(naming.list())));

    assertEquals("Hello", ((Hello) naming.lookup("hello2")).hello());
    assertEquals("Hello", ((Hello) naming.lookup("hello2", 1, TimeUnit.MILLISECONDS)).hello());

    naming.exportAndRebind("hello2", new HelloImpl());

    naming.unbind("hello2");
    naming.unexportAndUnbind("hello", hello);
    assertEquals(Arrays.asList(AFRMIService.class.getName()), Arrays.asList(naming.list()));

    naming.shutdownRegistry();

    assertEquals(Arrays.asList(), Arrays.asList(naming.list()));
  }
}
