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
package org.newsclub.net.unix.demo.rmi;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.registry.Registry;

import org.newsclub.net.unix.demo.rmi.services.HelloWorld;
import org.newsclub.net.unix.rmi.AFUNIXNaming;
import org.newsclub.net.unix.rmi.RemotePeerInfo;

/**
 * A simple RMI client. Locates the RMI registry via AF_UNIX sockets and calls
 * {@link HelloWorld#hello()}.
 *
 * @author Christian Kohlschütter
 */
public final class SimpleRMIClient {
  public static void main(String[] args) throws IOException, NotBoundException {
    AFUNIXNaming naming = AFUNIXNaming.getInstance();

    System.out.println("Locating registry...");
    final Registry registry = naming.getRegistry();
    System.out.println(registry);
    System.out.println();

    HelloWorld obj = (HelloWorld) registry.lookup("helloWorld");
    System.out.println("HelloWorld instance:");
    System.out.println("    " + obj);
    System.out.println("    " + RemotePeerInfo.remotePeerCredentials(obj));
    System.out.println();

    System.out.println("Calling HelloWorld...");
    System.out.println(obj.hello() + " " + obj.world());
  }
}
