/*
 * junixsocket
 *
 * Copyright 2009-2022 Christian Kohlschütter
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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class AFSelector extends AbstractSelector {
  private final AFPipe selectorPipe;
  private final PollFd selectorPipePollFd;

  private final ByteBuffer pipeMsgWakeUp = ByteBuffer.allocate(1);
  private final ByteBuffer pipeMsgReceiveBuffer = ByteBuffer.allocateDirect(256);

  private final Set<AFSelectionKey> keysRegistered = new HashSet<>();

  private PollFd pollFd = null;
  private final Set<SelectionKey> keysSelected = new HashSet<>();

  protected AFSelector(AFSelectorProvider<?> provider) throws IOException {
    super(provider);

    this.selectorPipe = AFUNIXSelectorProvider.getInstance().openSelectablePipe();
    this.selectorPipePollFd = new PollFd(selectorPipe.sourceFD());
  }

  @Override
  protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
    AFSelectionKey key = new AFSelectionKey(this, ch, ops, att);
    synchronized (this) {
      pollFd = null;
      keysRegistered.add(key);
    }
    return key;
  }

  @Override
  public Set<SelectionKey> keys() {
    synchronized (this) {
      return Collections.unmodifiableSet(new HashSet<>(keysRegistered));
    }
  }

  @Override
  public Set<SelectionKey> selectedKeys() {
    synchronized (keysSelected) {
      return new SelectionKeySet(keysSelected);
    }
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

  @SuppressWarnings("PMD.CognitiveComplexity")
  private int select0(int timeout) throws IOException {
    PollFd pfd;
    synchronized (this) {
      if (!isOpen()) {
        throw new ClosedSelectorException();
      }
      pfd = pollFd = initPollFd(pollFd);
      synchronized (keysSelected) {
        keysSelected.clear();
      }
    }
    int num;
    try {
      begin();
      num = NativeUnixSocket.poll(pfd, timeout);
    } finally {
      end();
    }
    synchronized (this) {
      pfd = pollFd;
      if (pfd != null) {
        AFSelectionKey[] keys = pfd.keys;
        if (keys != null) {
          for (AFSelectionKey key : keys) {
            if (key != null && key.hasOpInvalid()) {
              SelectableChannel ch = key.channel();
              if (ch != null && ch.isOpen()) {
                ch.close();
              }
            }
          }
        }
      }
      if (num > 0) {
        consumeAllBytesAfterPoll();
        setOpsReady();
      }
      synchronized (keysSelected) {
        return keysSelected.size();
      }
    }
  }

  private synchronized void consumeAllBytesAfterPoll() throws IOException {
    if (pollFd == null) {
      return;
    }
    if ((pollFd.rops[0] & SelectionKey.OP_READ) == 0) {
      return;
    }
    int maxReceive;
    int bytesReceived;

    int options = selectorPipe.getOptions();

    synchronized (pipeMsgReceiveBuffer) {
      pipeMsgReceiveBuffer.clear();
      maxReceive = pipeMsgReceiveBuffer.remaining();
      bytesReceived = NativeUnixSocket.receive(pollFd.fds[0], pipeMsgReceiveBuffer, 0, maxReceive,
          null, options, null, 1);
    }

    if (bytesReceived == maxReceive && maxReceive > 0) {
      // consume all pending bytes
      int read;
      do {
        if ((read = NativeUnixSocket.poll(selectorPipePollFd, 0)) > 0) {
          synchronized (pipeMsgReceiveBuffer) {
            pipeMsgReceiveBuffer.clear();
            read = NativeUnixSocket.receive(selectorPipePollFd.fds[0], pipeMsgReceiveBuffer, 0,
                maxReceive, null, options, null, 1);
          }
        }
      } while (read == maxReceive && read > 0);
    }
  }

  private synchronized void setOpsReady() {
    if (pollFd == null) {
      return;
    }
    for (int i = 1; i < pollFd.rops.length; i++) {
      int rops = pollFd.rops[i];
      if (rops == 0) {
        continue;
      }
      AFSelectionKey key = pollFd.keys[i];
      key.setOpsReady(rops);
      synchronized (keysSelected) {
        keysSelected.add(key);
      }
    }
  }

  @SuppressWarnings("resource")
  private PollFd initPollFd(PollFd existingPollFd) throws IOException {
    synchronized (this) {
      for (Iterator<AFSelectionKey> it = keysRegistered.iterator(); it.hasNext();) {
        AFSelectionKey key = it.next();
        if (!key.getAFCore().fd.valid()) {
          key.cancelNoRemove();
          it.remove();
          existingPollFd = null;
        }
      }

      if (existingPollFd != null && //
          existingPollFd.keys != null && //
          (existingPollFd.keys.length - 1) == keysRegistered.size()) {
        boolean needsUpdate = false;
        int i = 1;
        for (AFSelectionKey key : keysRegistered) {
          if (existingPollFd.keys[i] != key || !key.isValid()) { // NOPMD
            needsUpdate = true;
            break;
          }
          existingPollFd.ops[i] = key.interestOps();

          i++;
        }

        if (!needsUpdate) {
          return existingPollFd;
        }
      }

      int size = keysRegistered.size() + 1;
      FileDescriptor[] fds = new FileDescriptor[size];
      int[] ops = new int[size];

      AFSelectionKey[] keys = new AFSelectionKey[size];
      fds[0] = selectorPipe.sourceFD();
      ops[0] = SelectionKey.OP_READ;

      int i = 1;
      for (AFSelectionKey key : keysRegistered) {
        keys[i] = key;
        fds[i] = key.getAFCore().fd;
        ops[i] = key.interestOps();
        i++;
      }
      return new PollFd(keys, fds, ops);
    }
  }

  @Override
  protected void implCloseSelector() throws IOException {
    wakeup();
    Set<SelectionKey> keys;
    synchronized (this) {
      keys = keys();
      keysRegistered.clear();
    }
    for (SelectionKey key : keys) {
      ((AFSelectionKey) key).cancelNoRemove();
    }
    selectorPipe.close();
  }

  @Override
  public Selector wakeup() {
    if (isOpen()) {
      try {
        synchronized (pipeMsgWakeUp) {
          pipeMsgWakeUp.clear();
          try {
            selectorPipe.sink().write(pipeMsgWakeUp);
          } catch (SocketException e) {
            if (selectorPipe.sinkFD().valid()) {
              throw e;
            } else {
              // ignore (Broken pipe, etc)
            }
          }
        }
      } catch (IOException e) {
        // FIXME throw as runtimeexception?
        e.printStackTrace();
      }
    }
    return this;
  }

  synchronized void remove(AFSelectionKey key) {
    deregister(key);
    keysRegistered.remove(key);
    synchronized (keysSelected) {
      keysSelected.remove(key);
    }
    pollFd = null;
  }

  private void deregister(AFSelectionKey key) {
    // super.deregister unnecessarily casts SelectionKey to AbstractSelectionKey, and
    // ((AbstractSelectableChannel)key.channel()).removeKey(key); is not visible.
    // so we have to resort to some JNI trickery...
    try {
      NativeUnixSocket.deregisterSelectionKey((AbstractSelectableChannel) key.channel(), key);
    } catch (ClassCastException e) {
      // because our key isn't an AbstractSelectableKey, internal invalidation fails
      // but at that point, the key is deregistered
    }
  }

  static final class PollFd {
    // accessed from native code
    final FileDescriptor[] fds;
    // accessed from native code
    final int[] ops;
    // accessed from native code
    final int[] rops;

    final AFSelectionKey[] keys;

    PollFd(FileDescriptor pipeSourceFd) {
      this(pipeSourceFd, SelectionKey.OP_READ);
    }

    PollFd(FileDescriptor pipeSourceFd, int op) {
      this.fds = new FileDescriptor[] {pipeSourceFd};
      this.ops = new int[] {op};
      this.rops = new int[1];
      this.keys = null;
    }

    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    PollFd(AFSelectionKey[] keys, FileDescriptor[] fds, int[] ops) {
      this.keys = keys;
      if (fds.length != ops.length) {
        throw new IllegalStateException();
      }
      this.fds = fds;
      this.ops = ops;
      this.rops = new int[ops.length];
    }
  }

  private static final class SelectionKeySet extends AbstractSet<SelectionKey> {
    private final Set<SelectionKey> keys;

    SelectionKeySet(Set<SelectionKey> keys) {
      super();
      this.keys = keys;
    }

    @Override
    public boolean add(SelectionKey e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends SelectionKey> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      synchronized (keys) {
        return keys.remove(o);
      }
    }

    @Override
    public Iterator<SelectionKey> iterator() {
      final Set<SelectionKey> copy;
      synchronized (keys) {
        copy = new HashSet<>(keys);
      }
      Iterator<SelectionKey> it = copy.iterator();
      return new Iterator<SelectionKey>() {
        SelectionKey current = null;

        @Override
        public boolean hasNext() {
          return it.hasNext();
        }

        @Override
        public SelectionKey next() {
          current = it.next();
          return current;
        }

        @Override
        public void remove() {
          if (current != null) {
            SelectionKeySet.this.remove(current);
          }
        }
      };
    }

    @Override
    public int size() {
      synchronized (keys) {
        return keys.size();
      }
    }

    @Override
    public boolean isEmpty() {
      synchronized (keys) {
        return keys.isEmpty();
      }
    }

    @Override
    public boolean contains(Object o) {
      synchronized (keys) {
        return keys.contains(o);
      }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      synchronized (keys) {
        return super.containsAll(c);
      }
    }

    @Override
    public void clear() {
      synchronized (keys) {
        keys.clear();
      }
    }
  }
}
