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
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

class AncillaryDataSupport implements Closeable {
  private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

  protected final Map<FileDescriptor, Integer> openReceivedFileDescriptors = Collections
      .synchronizedMap(new HashMap<FileDescriptor, Integer>());

  private final List<FileDescriptor[]> receivedFileDescriptors = Collections.synchronizedList(
      new LinkedList<FileDescriptor[]>());

  // referenced from native code
  protected ByteBuffer ancillaryReceiveBuffer = EMPTY_BUFFER;

  // referenced from native code
  @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  int[] pendingFileDescriptors = null;

  int getAncillaryReceiveBufferSize() {
    return ancillaryReceiveBuffer.capacity();
  }

  void setAncillaryReceiveBufferSize(int size) {
    if (size == ancillaryReceiveBuffer.capacity()) {
      return;
    } else if (size <= 0) {
      this.ancillaryReceiveBuffer = EMPTY_BUFFER;
    } else {
      setAncillaryReceiveBufferSize0(Math.max(256, size));
    }
  }

  void setAncillaryReceiveBufferSize0(int size) {
    this.ancillaryReceiveBuffer = ByteBuffer.allocateDirect(size);
  }

  public final void ensureAncillaryReceiveBufferSize(int minSize) {
    if (minSize <= 0) {
      return;
    }
    if (ancillaryReceiveBuffer.capacity() < minSize) {
      setAncillaryReceiveBufferSize(minSize);
    }
  }

  // called from native code
  void receiveFileDescriptors(int[] fds) throws IOException {
    if (fds == null || fds.length == 0) {
      return;
    }
    final int fdsLength = fds.length;
    FileDescriptor[] descriptors = new FileDescriptor[fdsLength];
    for (int i = 0; i < fdsLength; i++) {
      final FileDescriptor fdesc = new FileDescriptor();
      NativeUnixSocket.initFD(fdesc, fds[i]);
      descriptors[i] = fdesc;

      openReceivedFileDescriptors.put(fdesc, fds[i]);

      final Closeable cleanup = new Closeable() {

        @Override
        public void close() throws IOException {
          openReceivedFileDescriptors.remove(fdesc);
        }
      };
      NativeUnixSocket.attachCloseable(fdesc, cleanup);
    }

    this.receivedFileDescriptors.add(descriptors);
  }

  final void clearReceivedFileDescriptors() {
    receivedFileDescriptors.clear();
  }

  FileDescriptor[] getReceivedFileDescriptors() {
    if (receivedFileDescriptors.isEmpty()) {
      return null;
    }
    List<FileDescriptor[]> copy = new ArrayList<>(receivedFileDescriptors);
    if (copy.isEmpty()) {
      return null;
    }
    receivedFileDescriptors.removeAll(copy);
    int count = 0;
    for (FileDescriptor[] fds : copy) {
      count += fds.length;
    }
    if (count == 0) {
      return null;
    }
    FileDescriptor[] oneArray = new FileDescriptor[count];
    int offset = 0;
    for (FileDescriptor[] fds : copy) {
      System.arraycopy(fds, 0, oneArray, offset, fds.length);
      offset += fds.length;
    }
    return oneArray;
  }

  void setOutboundFileDescriptors(int[] fds) {
    this.pendingFileDescriptors = (fds == null || fds.length == 0) ? null : fds;
  }

  boolean hasOutboundFileDescriptors() {
    return this.pendingFileDescriptors != null;
  }

  void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    final int[] fds;
    if (fdescs == null || fdescs.length == 0) {
      fds = null;
    } else {
      final int numFdescs = fdescs.length;
      fds = new int[numFdescs];
      for (int i = 0; i < numFdescs; i++) {
        FileDescriptor fdesc = fdescs[i];
        fds[i] = NativeUnixSocket.getFD(fdesc);
      }
    }
    this.setOutboundFileDescriptors(fds);
  }

  @Override
  public void close() {
    synchronized (openReceivedFileDescriptors) {
      for (FileDescriptor desc : openReceivedFileDescriptors.keySet()) {
        try {
          NativeUnixSocket.close(desc);
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }
}
