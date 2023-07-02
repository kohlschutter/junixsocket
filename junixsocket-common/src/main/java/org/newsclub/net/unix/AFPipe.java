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
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.spi.SelectorProvider;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * A {@link Pipe}, natively implemented.
 *
 * @author Christian Kohlschütter
 */
public final class AFPipe extends Pipe implements Closeable {
  private final AFCore sourceCore;
  private final AFCore sinkCore;
  private final SourceChannel sourceChannel;
  private final SinkChannel sinkChannel;
  private final int options;

  AFPipe(AFSelectorProvider<?> provider, boolean selectable) throws IOException {
    super();

    NativeUnixSocket.ensureSupported();

    this.sourceCore = new AFCore(this, (FileDescriptor) null);
    this.sinkCore = new AFCore(this, (FileDescriptor) null);

    boolean isSocket = NativeUnixSocket.initPipe(sourceCore.fd, sinkCore.fd, selectable);
    this.options = isSocket ? 0 : NativeUnixSocket.OPT_NON_SOCKET;

    this.sourceChannel = new SourceChannel(provider);
    this.sinkChannel = new SinkChannel(provider);
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public SourceChannel source() {
    return sourceChannel;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  @Override
  public SinkChannel sink() {
    return sinkChannel;
  }

  FileDescriptor sourceFD() {
    return sourceCore.fd;
  }

  FileDescriptor sinkFD() {
    return sinkCore.fd;
  }

  @Override
  public void close() throws IOException {
    try { // NOPMD.UseTryWithResources
      source().close();
    } finally {
      sink().close();
    }
  }

  /**
   * A channel representing the readable end of a {@link Pipe}, with access to the
   * {@link FileDescriptor}.
   */
  public final class SourceChannel extends java.nio.channels.Pipe.SourceChannel implements
      FileDescriptorAccess {
    SourceChannel(SelectorProvider provider) {
      super(provider);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      return read(dsts[offset]);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
      return read(dsts, 0, dsts.length);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      return sourceCore.read(dst, null, options);
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
      sourceCore.implConfigureBlocking(block);
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
      sourceCore.close();
    }

    @Override
    public FileDescriptor getFileDescriptor() throws IOException {
      return sourceCore.fd;
    }
  }

  /**
   * A channel representing the writable end of a {@link Pipe}, with access to the
   * {@link FileDescriptor}.
   */
  public final class SinkChannel extends java.nio.channels.Pipe.SinkChannel implements
      FileDescriptorAccess {
    SinkChannel(SelectorProvider provider) {
      super(provider);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      return write(srcs[offset]);
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
      return write(srcs, 0, srcs.length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      return sinkCore.write(src, null, options);
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
      sinkCore.implConfigureBlocking(block);
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
      sinkCore.close();
    }

    @Override
    public FileDescriptor getFileDescriptor() throws IOException {
      return sinkCore.fd;
    }
  }

  /**
   * Returns the options bitmask that is to be passed to native receive/send calls.
   *
   * @return The options.
   */
  int getOptions() {
    return options;
  }

  /**
   * Opens an {@link AFPipe}.
   *
   * @return The new pipe
   * @throws IOException on error.
   */
  public static AFPipe open() throws IOException {
    return AFUNIXSelectorProvider.provider().openPipe();
  }
}
