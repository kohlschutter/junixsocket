/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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

import java.io.Externalizable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.newsclub.net.unix.AncillaryFileDescriptors;

/**
 * A wrapper that allows a {@link FileDescriptor} be sent via RMI over AF_UNIX sockets.
 * 
 * 
 * @author Christian Kohlschütter
 * @see FileInput subclass for sending {@link FileInputStream}s.
 * @see FileOutput subclass for sending {@link FileOutputStream}s.
 */
public final class RemoteFileDescriptor implements Externalizable {
  private static final long serialVersionUID = 1L;

  private static final int MAGIC_VALUE_MASK = 0x00FD0000;
  private static final int BIT_READABLE = 1 << 0;
  private static final int BIT_WRITABLE = 1 << 1;

  private int magicValue;
  private FileDescriptor fd;

  /**
   * Creates an uninitialized instance; used for externalization.
   * 
   * @see #readExternal(ObjectInput)
   */
  public RemoteFileDescriptor() {
  }

  /**
   * Creates an instance for the give {@link FileDescriptor}.
   * 
   * @param fd The file descriptor that is to be exposed via RMI.
   */
  public RemoteFileDescriptor(FileDescriptor fd) {
    this(fd, MAGIC_VALUE_MASK | 0);
  }

  RemoteFileDescriptor(FileDescriptor fd, int magicValue) {
    this.fd = fd;
    this.magicValue = magicValue;
  }

  /**
   * A specialized subclass specifically for {@link FileInputStream}s.
   * 
   * @author Christian Kohlschütter
   */
  public static final class FileInput implements Serializable {
    private static final long serialVersionUID = 1L;
    private RemoteFileDescriptor rfd;

    @SuppressWarnings("unused")
    private FileInput() {
      // used for serialization
    }

    public FileInput(FileInputStream fin) throws IOException {
      this.rfd = new RemoteFileDescriptor(fin.getFD(), MAGIC_VALUE_MASK | BIT_READABLE);
    }

    public FileInputStream toFileInputStream() throws IOException {
      if ((rfd.magicValue & BIT_READABLE) == 0) {
        throw new IOException("FileDescriptor is not readable");
      }
      return new FileInputStream(rfd.getFileDescriptor());
    }
  }

  /**
   * A specialized subclass specifically for {@link FileOutputStream}s.
   * 
   * @author Christian Kohlschütter
   */
  public static final class FileOutput implements Serializable {
    private static final long serialVersionUID = 1L;
    private RemoteFileDescriptor rfd;

    @SuppressWarnings("unused")
    private FileOutput() {
      // used for serialization
    }

    public FileOutput(FileOutputStream fin) throws IOException {
      this.rfd = new RemoteFileDescriptor(fin.getFD(), MAGIC_VALUE_MASK | BIT_WRITABLE);
    }

    public FileOutputStream toFileOutputStream() throws IOException {
      if ((rfd.magicValue & BIT_WRITABLE) == 0) {
        throw new IOException("FileDescriptor is not writable");
      }
      return new FileOutputStream(rfd.getFileDescriptor());
    }
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
    if (fd == null || !fd.valid()) {
      throw new IOException("No or invalid file descriptor");
    }

    AncillaryFileDescriptors.Support fds = accessAncillaryFileDescriptors(out);
    fds.setOutboundFileDescriptors(fd);
    // Since ancillary messages can only be sent together with real data, we send a magic value.
    // The value can also be used to verify 
    out.writeInt(magicValue);
    out.flush();
  }

  private AncillaryFileDescriptors.Support accessAncillaryFileDescriptors(ObjectInput in)
      throws IOException {
    in.available();
    final AncillaryFileDescriptors.Support fds = AncillaryFileDescriptors.getAndClearSupportRef();
    if (fds == null) {
      throw new IOException("This connection does not support ancillary file descriptors");
    }
    return fds;
  }

  private AncillaryFileDescriptors.Support accessAncillaryFileDescriptors(ObjectOutput out)
      throws IOException {
    out.flush();
    final AncillaryFileDescriptors.Support fds = AncillaryFileDescriptors.getAndClearSupportRef();
    if (fds == null) {
      throw new IOException("This connection does not support ancillary file descriptors");
    }
    return fds;
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    AncillaryFileDescriptors.Support fds = accessAncillaryFileDescriptors(in);
    fds.ensureAncillaryReceiveBufferSize(128);

    // Since ancillary messages can only be read in combination with real data, we read and verify a
    // magic value
    this.magicValue = in.readInt();
    if ((magicValue & MAGIC_VALUE_MASK) != MAGIC_VALUE_MASK) {
      throw new IOException("Unexpected magic value: " + Integer.toHexString(magicValue));
    }
    FileDescriptor[] descriptors = fds.getReceivedFileDescriptors();
    if (descriptors == null || descriptors.length != 1) {
      throw new IOException("Did not receive exactly 1 file descriptor but " + (descriptors == null
          ? 0 : descriptors.length));
    }
    this.fd = descriptors[0];
  }

  /**
   * Returns the file descriptor.
   * 
   * This is either the original one that was specified in the constructor or a copy that was sent
   * via RMI over an AF_UNIX connection as part of an ancillary message.
   * 
   * @return The file descriptor.
   */
  public FileDescriptor getFileDescriptor() {
    return fd;
  }
}
