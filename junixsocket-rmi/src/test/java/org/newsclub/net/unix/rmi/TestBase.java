/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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

import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

// must remain public until SUREFIRE-1909 is fixed
public class TestBase extends ShutdownHookTestBase {
  private static final String TEST_SERVICE_NAME = TestService.class.getName();
  private AFUNIXNaming naming;
  private TestServiceImpl testService;

  protected TestBase() {
    super();
  }

  @BeforeEach
  public void setUp() throws IOException, AlreadyBoundException {
    // NOTE: for testing. You'd probably want to use AFUNIXNaming.getInstance()
    naming = AFUNIXNaming.newPrivateInstance();

    // Create registry
    Registry registry = naming.createRegistry();

    // Create and bind service
    testService = new TestServiceImpl(naming.getSocketFactory());
    registry.bind(TEST_SERVICE_NAME, RemoteObject.toStub(testService));
  }

  @AfterEach
  public void tearDown() throws IOException {
    testService.close();
    naming.shutdownRegistry();
  }

  protected TestService lookupTestService() throws AccessException, RemoteException,
      NotBoundException {
    return (TestService) naming.getRegistry().lookup(TEST_SERVICE_NAME);
  }

  protected AFUNIXRMISocketFactory namingSocketFactory() {
    return naming.getSocketFactory();
  }
}
