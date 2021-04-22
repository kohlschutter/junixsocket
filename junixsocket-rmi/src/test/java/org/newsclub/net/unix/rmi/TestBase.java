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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

// must remain public until SUREFIRE-1909 is fixed
public class TestBase {
  private static final String TEST_SERVICE_NAME = TestService.class.getName();
  private static AFUNIXNaming naming;
  private static Registry registry;
  private static TestServiceImpl testService;

  protected TestBase() {
  }

  @SuppressWarnings("resource")
  @BeforeAll
  public static void setupClass() throws IOException, AlreadyBoundException {
    // NOTE: for testing. You'd probably want to use AFUNIXNaming.getInstance()
    naming = AFUNIXNaming.newPrivateInstance();

    // Create registry
    registry = naming.createRegistry();

    // Create and bind service
    testService = new TestServiceImpl(naming.getSocketFactory());
    registry.bind(TEST_SERVICE_NAME, RemoteObject.toStub(testService));
  }

  @AfterAll
  public static void tearDownClass() throws IOException, NotBoundException {
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
