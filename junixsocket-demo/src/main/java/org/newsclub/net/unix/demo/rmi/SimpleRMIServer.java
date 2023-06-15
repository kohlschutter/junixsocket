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
import java.rmi.AlreadyBoundException;

import org.newsclub.net.unix.demo.rmi.services.HelloWorld;
import org.newsclub.net.unix.rmi.AFUNIXNaming;

/**
 * A very simple RMI server. Provides a registry and the implementation of the {@link HelloWorld}
 * service.
 *
 * @author Christian Kohlschütter
 */
public final class SimpleRMIServer {
  public static void main(String[] args) throws IOException, AlreadyBoundException {
    AFUNIXNaming naming = AFUNIXNaming.getInstance();
    naming.createRegistry();
    // naming.setRemoteShutdownAllowed(false);
    System.out.println("Using " + naming.getSocketFactory());

    HelloWorldImpl obj = new HelloWorldImpl(naming);
    System.out.println("Binding " + obj.toString() + " to \"helloWorld\"...");
    naming.exportAndBind("helloWorld", obj);

    System.out.println("Ready to accept connections!");
  }
}
