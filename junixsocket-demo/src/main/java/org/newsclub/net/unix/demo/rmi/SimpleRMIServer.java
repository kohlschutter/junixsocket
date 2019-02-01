/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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

import java.rmi.Remote;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Semaphore;

import org.newsclub.net.unix.demo.rmi.services.HelloWorld;
import org.newsclub.net.unix.rmi.AFUNIXNaming;

/**
 * A very simple RMI server. Provides a registry and the implementation of the {@link HelloWorld}
 * service.
 * 
 * @author Christian Kohlschütter
 */
public final class SimpleRMIServer {
  public static void main(String[] args) throws Exception {
    AFUNIXNaming naming = AFUNIXNaming.getInstance();
    System.out.println("Socket directory: " + naming.getSocketFactory().getSocketDir());

    System.out.println("Creating registry...");
    final Registry registry = naming.createRegistry();
    System.out.println(registry);
    System.out.println();

    HelloWorldImpl obj = new HelloWorldImpl();

    System.out.println("Binding " + obj.toString() + " to \"helloWorld\"...");
    final Remote remote = UnicastRemoteObject.exportObject(obj, 0, naming.getSocketFactory(), naming
        .getSocketFactory());
    registry.bind("helloWorld", remote);

    System.out.println("Ready to accept connections!");

    new Semaphore(0).acquire(); // keep running
  }
}
