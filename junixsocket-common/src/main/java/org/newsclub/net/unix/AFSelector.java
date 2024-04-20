/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class AFSelector extends AbstractSelector {
  private final AFPipe selectorPipe;
  private final PollFd selectorPipePollFd;

  private final ByteBuffer pipeMsgWakeUp = ByteBuffer.allocate(1);
  private final ByteBuffer pipeMsgReceiveBuffer = ByteBuffer.allocateDirect(256);

  private final Map<AFSelectionKey, Integer> keysRegistered = new ConcurrentHashMap<>();
  private final Set<AFSelectionKey> keysRegisteredKeySet = keysRegistered.keySet();
  private final Set<SelectionKey> keysRegisteredPublic = Collections.unmodifiableSet(
      keysRegisteredKeySet);

  private final AtomicInteger selectCount = new AtomicInteger(0);

  @SuppressWarnings("PMD.LooseCoupling")
  private final MapValueSet<SelectionKey, Integer> selectedKeysSet =
      new MapValueSet<SelectionKey, Integer>(keysRegistered, selectCount::get, 0);
  private final Set<SelectionKey> selectedKeysPublic = new UngrowableSet<>(selectedKeysSet);

  private PollFd pollFd = null;

  AFSelector(AFSelectorProvider<?> provider) throws IOException {
    super(provider);

    this.selectorPipe = AFUNIXSelectorProvider.getInstance().openSelectablePipe();
    this.selectorPipePollFd = new PollFd(selectorPipe.sourceFD());
  }

  @Override
  protected SelectionKey register(AbstractSelectableChannel ch, int ops, Object att) {
    AFSelectionKey key = new AFSelectionKey(this, ch, ops, att);
    synchronized (this) {
      pollFd = null;
      selectedKeysSet.markRemoved(key);
    }
    return key;
  }

  @Override
  public Set<SelectionKey> keys() {
    return keysRegisteredPublic;
  }

  @Override
  public Set<SelectionKey> selectedKeys() {
    return selectedKeysPublic;
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

    int selectId = updateSelectCount();

    synchronized (this) {
      if (!isOpen()) {
        throw new ClosedSelectorException();
      }

      pfd = pollFd = initPollFd(pollFd);
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
        setOpsReady(pfd, selectId); // updates keysSelected and numKeysSelected
      }
      return selectedKeysSet.size();
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
      bytesReceived = receive(maxReceive, options);
    }

    if (bytesReceived == maxReceive && maxReceive > 0) {
      // consume all pending bytes
      int read;
      do {
        if ((read = NativeUnixSocket.poll(selectorPipePollFd, 0)) > 0) {
          synchronized (pipeMsgReceiveBuffer) {
            pipeMsgReceiveBuffer.clear();
            read = receive(maxReceive, options);
          }
        }
      } while (read == maxReceive && read > 0);
    }
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  private int receive(int maxReceive, int options) throws IOException {
    final boolean virtualBlocking = ThreadUtil.isVirtualThread();
    final long now;
    if (virtualBlocking) {
      now = System.currentTimeMillis();
      options |= NativeUnixSocket.OPT_NON_BLOCKING;
    } else {
      now = 0;
    }

    FileDescriptor fdesc = selectorPipePollFd.fds[0];

    boolean park = false;
    int count;
    virtualThreadLoop : do {
      if (virtualBlocking) {
        if (park) {
          VirtualThreadPoller.INSTANCE.parkThreadUntilReady(fdesc, SelectionKey.OP_WRITE, now,
              AFPipe.DUMMY_TIMEOUT, this::close);
        }
        NativeUnixSocket.configureBlocking(fdesc, false);
      }
      try {
        count = NativeUnixSocket.receive(fdesc, pipeMsgReceiveBuffer, 0, maxReceive, null, options,
            null, 1);
        if (count == 0 && virtualBlocking) {
          // try again
          park = true;
          continue virtualThreadLoop;
        }
      } catch (SocketTimeoutException e) {
        if (virtualBlocking) {
          // try again
          park = true;
          continue virtualThreadLoop;
        } else {
          throw e;
        }
      } finally {
        if (virtualBlocking) {
          NativeUnixSocket.configureBlocking(fdesc, true);
        }
      }
      break; // NOPMD.AvoidBranchingStatementAsLastInLoop virtualThreadLoop
    } while (true); // NOPMD.WhileLoopWithLiteralBoolean
    return count;
  }

  private int updateSelectCount() {
    int selectId = selectCount.incrementAndGet();
    if (selectId == 0) {
      // overflow (unlikely)
      selectedKeysSet.markAllRemoved();
      selectId = selectCount.incrementAndGet();
    }
    return selectId;
  }

  private void setOpsReady(PollFd pfd, int selectId) {
    if (pfd != null) {
      for (int i = 1; i < pfd.rops.length; i++) {
        int rops = pfd.rops[i];
        AFSelectionKey key = pfd.keys[i];
        key.setOpsReady(rops);
        if (rops != 0 && keysRegistered.containsKey(key)) {
          keysRegistered.put(key, selectId);
        }
      }
    }
  }

  @SuppressWarnings({"resource", "PMD.CognitiveComplexity"})
  private PollFd initPollFd(PollFd existingPollFd) throws IOException {
    synchronized (this) {
      for (Iterator<AFSelectionKey> it = keysRegisteredKeySet.iterator(); it.hasNext();) {
        AFSelectionKey key = it.next();
        if (!key.getAFCore().fd.valid() || !key.isValid()) {
          key.cancelNoRemove();
          it.remove();
          existingPollFd = null;
        } else {
          key.setOpsReady(0);
        }
      }

      if (existingPollFd != null && //
          existingPollFd.keys != null && //
          (existingPollFd.keys.length - 1) == keysRegistered.size()) {
        boolean needsUpdate = false;
        int i = 1;
        for (AFSelectionKey key : keysRegisteredKeySet) {
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

      int keysToPoll = keysRegistered.size();
      for (AFSelectionKey key : keysRegisteredKeySet) {
        if (!key.isValid()) {
          keysToPoll--;
        }
      }

      int size = keysToPoll + 1;
      FileDescriptor[] fds = new FileDescriptor[size];
      int[] ops = new int[size];

      AFSelectionKey[] keys = new AFSelectionKey[size];
      fds[0] = selectorPipe.sourceFD();
      ops[0] = SelectionKey.OP_READ;

      int i = 1;
      for (AFSelectionKey key : keysRegisteredKeySet) {
        if (!key.isValid()) {
          continue;
        }
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
      } catch (IOException e) { // NOPMD.ExceptionAsFlowControl
        // FIXME throw as runtimeexception?
        StackTraceUtil.printStackTrace(e);
      }
    }
    return this;
  }

  synchronized void remove(AFSelectionKey key) {
    selectedKeysSet.remove(key);
    deregister(key);
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

    PollFd(FileDescriptor[] fds, int[] ops) {
      this(null, fds, ops);
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
}
