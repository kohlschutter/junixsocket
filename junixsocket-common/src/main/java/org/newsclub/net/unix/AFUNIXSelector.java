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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class AFUNIXSelector extends AbstractSelector {
  private static AFUNIXPipe sharedSelectorPipe = null;
  private static PollFd sharedSelectorPipePollFd;

  private static final ByteBuffer PIPE_MSG = ByteBuffer.allocate(1);
  private static final ByteBuffer PIPE_MSG_RECEIVE_BUFFER = ByteBuffer.allocateDirect(256);

  private final Set<AFUNIXSelectionKey> keysRegistered = new HashSet<>();

  @SuppressWarnings("unchecked")
  private final Set<SelectionKey> keysView =
      (Set<SelectionKey>) (Set<? extends SelectionKey>) Collections.unmodifiableSet(keysRegistered);

  private PollFd pollFd = null;

  private final SelectionKeySet keysSelected = new SelectionKeySet();

  protected AFUNIXSelector(SelectorProvider provider) {
    super(provider);
  }

  protected AFUNIXPipe selectorPipe() throws IOException {
    AFUNIXPipe selectorPipe;
    synchronized (AFUNIXSelector.class) {
      if (sharedSelectorPipe == null) {
        sharedSelectorPipe = AFUNIXSelectorProvider.getInstance().openSelectablePipe();
        sharedSelectorPipePollFd = new PollFd(sharedSelectorPipe.sourceFD());
      }
      selectorPipe = sharedSelectorPipe;
    }
    return selectorPipe;
  }

  @Override
  protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
    AFUNIXSelectionKey key = new AFUNIXSelectionKey(this, ch, ops, att);
    pollFd = null;
    keysRegistered.add(key);
    return key;
  }

  @Override
  public Set<SelectionKey> keys() {
    return keysView;
  }

  @Override
  public Set<SelectionKey> selectedKeys() {
    return keysSelected;
  }

  @Override
  public int selectNow() throws IOException {
    return select0(0);
  }

  @Override
  public int select(long timeout) throws IOException {
    if (timeout > Integer.MAX_VALUE) {
      timeout = Integer.MAX_VALUE;
    } else if (timeout < 0) {
      throw new IllegalArgumentException("Timeout must not be negative");
    }

    return select0((int) timeout);
  }

  @Override
  public int select() throws IOException {
    try {
      return select0(-1);
    } catch (SocketTimeoutException e) {
      return 0;
    }
  }

  private int select0(int timeout) throws IOException {
    pollFd = initPollFd(pollFd);

    keysSelected.clear();

    int num;
    begin();
    try {
      num = NativeUnixSocket.poll(pollFd, timeout);
    } finally {
      end();
    }
    if (num > 0) {
      consumeAllBytesAfterPoll();
      setOpsReady();
    }
    return keysSelected.size();
  }

  private void consumeAllBytesAfterPoll() throws IOException {
    if ((pollFd.rops[0] & SelectionKey.OP_READ) == 0) {
      return;
    }
    synchronized (PIPE_MSG_RECEIVE_BUFFER) {
      PIPE_MSG_RECEIVE_BUFFER.clear();
      int maxReceive = PIPE_MSG_RECEIVE_BUFFER.remaining();
      int bytesReceived = NativeUnixSocket.receive(pollFd.fds[0], PIPE_MSG_RECEIVE_BUFFER, 0,
          maxReceive, null, NativeUnixSocket.OPT_NON_SOCKET, null, 0);

      if (bytesReceived == maxReceive) {
        // consume all pending bytes
        int read;
        do {
          if ((read = NativeUnixSocket.poll(sharedSelectorPipePollFd, 0)) > 0) {
            PIPE_MSG_RECEIVE_BUFFER.clear();
            read = NativeUnixSocket.receive(sharedSelectorPipePollFd.fds[0],
                PIPE_MSG_RECEIVE_BUFFER, 0, maxReceive, null, NativeUnixSocket.OPT_NON_SOCKET, null,
                0);
          }
        } while (read == maxReceive);
      }
    }
  }

  private void setOpsReady() {
    for (int i = 1; i < pollFd.rops.length; i++) {
      int rops = pollFd.rops[i];
      if (rops == 0) {
        continue;
      }
      AFUNIXSelectionKey key = pollFd.keys[i];
      key.setOpsReady(rops);
      keysSelected.add0(key);
    }
  }

  @SuppressWarnings("resource")
  private PollFd initPollFd(PollFd existingPollFd) throws IOException {
    for (Iterator<AFUNIXSelectionKey> it = keysRegistered.iterator(); it.hasNext();) {
      AFUNIXSelectionKey key = it.next();
      if (!key.getAFCore().fd.valid()) {
        key.cancelNoRemove();
        it.remove();
        existingPollFd = null;
      }
    }

    if (existingPollFd != null) {
      return existingPollFd;
    }

    int size = keysRegistered.size() + 1;
    FileDescriptor[] fds = new FileDescriptor[size];
    int[] ops = new int[size];

    AFUNIXSelectionKey[] keys = new AFUNIXSelectionKey[size];
    fds[0] = selectorPipe().sourceFD();
    ops[0] = SelectionKey.OP_READ;

    int i = 1;
    for (AFUNIXSelectionKey key : keysRegistered) {
      keys[i] = key;
      fds[i] = key.getAFCore().fd;
      ops[i] = key.interestOps();
    }
    return new PollFd(keys, fds, ops);
  }

  @Override
  protected void implCloseSelector() throws IOException {
    wakeup();
    for (AFUNIXSelectionKey key : keysRegistered) {
      key.cancelNoRemove();
    }
    keysRegistered.clear();
  }

  @Override
  public Selector wakeup() {
    try {
      selectorPipe().sink().write(PIPE_MSG);
    } catch (IOException e) {
      // FIXME throw as runtimeexception?
      e.printStackTrace();
    }
    return this;
  }

  void remove(AFUNIXSelectionKey key) {
    keysRegistered.remove(key);
    pollFd = null;
  }

  static final class PollFd {
    // accessed from native code
    final FileDescriptor[] fds;
    // accessed from native code
    final int[] ops;
    // accessed from native code
    final int[] rops;

    final AFUNIXSelectionKey[] keys;

    private PollFd(FileDescriptor pipeSourceFd) {
      this(pipeSourceFd, SelectionKey.OP_READ);
    }

    PollFd(FileDescriptor pipeSourceFd, int op) {
      this.fds = new FileDescriptor[] {pipeSourceFd};
      this.ops = new int[] {op};
      this.rops = new int[1];
      this.keys = null;
    }

    private PollFd(AFUNIXSelectionKey[] keys, FileDescriptor[] fds, int[] ops) {
      this.keys = keys;
      if (fds.length != ops.length) {
        throw new IllegalStateException();
      }
      this.fds = fds;
      this.ops = ops;
      this.rops = new int[ops.length];
    }
  }

  private static final class SelectionKeySet extends HashSet<SelectionKey> {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean add(SelectionKey e) {
      throw new UnsupportedOperationException();
    }

    private void add0(SelectionKey e) {
      super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends SelectionKey> c) {
      throw new UnsupportedOperationException();
    }
  }
}
