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
package org.newsclub.net.unix.darwin.system;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;

import org.newsclub.net.unix.AFDatagramSocket;
import org.newsclub.net.unix.AFSYSTEMSocketAddress;
import org.newsclub.net.unix.AFSYSTEMSocketImplExtensions;
import org.newsclub.net.unix.AFSocketType;

/**
 * A {@link DatagramSocket} implementation that works with {@code AF_SYSTEM} sockets.
 *
 * @author Christian Kohlschütter
 */
public final class AFSYSTEMDatagramSocket extends AFDatagramSocket<AFSYSTEMSocketAddress> implements
    AFSYSTEMSocketExtensions {

  AFSYSTEMDatagramSocket(FileDescriptor fd) throws IOException {
    this(fd, AFSocketType.SOCK_DGRAM);
  }

  AFSYSTEMDatagramSocket(FileDescriptor fd, AFSocketType socketType) throws IOException {
    super(new AFSYSTEMDatagramSocketImpl(fd, socketType));
  }

  @Override
  protected AFSYSTEMDatagramChannel newChannel() {
    return new AFSYSTEMDatagramChannel(this);
  }

  /**
   * Returns a new {@link AFSYSTEMDatagramSocket} instance, using the default
   * {@link AFSocketType#SOCK_DGRAM} socket type.
   *
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFSYSTEMDatagramSocket newInstance() throws IOException {
    return (AFSYSTEMDatagramSocket) newInstance(AFSYSTEMDatagramSocket::new);
  }

  /**
   * Returns a new {@link AFSYSTEMDatagramSocket} instance for the given socket type.
   *
   * @param socketType The socket type.
   * @return The new instance.
   * @throws IOException on error.
   */
  public static AFSYSTEMDatagramSocket newInstance(AFSocketType socketType) throws IOException {
    return (AFSYSTEMDatagramSocket) newInstance((fd) -> {
      return new AFSYSTEMDatagramSocket(fd, socketType);
    });
  }

  @Override
  public AFSYSTEMDatagramChannel getChannel() {
    return (AFSYSTEMDatagramChannel) super.getChannel();
  }

  @Override
  protected AFSYSTEMSocketImplExtensions getImplExtensions() {
    return (AFSYSTEMSocketImplExtensions) super.getImplExtensions();
  }

  /**
   * Retrieves the kernel control ID given a kernel control name.
   *
   * An {@link IOException} is thrown if the ID is invalid or could not be accessed due to access
   * restrictions.
   *
   * @param name The control name
   * @return The control Id.
   * @throws IOException on error.
   */
  public int getNodeIdentity(String name) throws IOException {
    return getImplExtensions().getKernelControlId(getFileDescriptor(), name);
  }

  /**
   * Retrieves the kernel control ID given a kernel control name.
   *
   * An {@link IOException} is thrown if the ID is invalid or could not be accessed due to access
   * restrictions.
   *
   * @param name The control name
   * @return The control Id.
   * @throws IOException on error.
   */
  public int getNodeIdentity(WellKnownKernelControlNames name) throws IOException {
    return getNodeIdentity(name.getControlName());
  }
}
