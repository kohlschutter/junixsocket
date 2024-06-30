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

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.spi.AbstractInterruptibleChannel;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Helper methods when working with {@link AbstractInterruptibleChannel} subclasses.
 *
 * @author Christian Kohlschütter
 */
final class InterruptibleChannelUtil {
  /**
   * Reference to the protected {@code AbstractInterruptibleChannel#end(boolean)} method.
   */
  @FunctionalInterface
  interface EndMethod {
    void end(boolean completed) throws AsynchronousCloseException;
  }

  /**
   * Wrapper method that calls {@code AbstractInterruptibleChannel#end(boolean)}, making sure the
   * socket is closed and the {@link Thread#interrupted()} state is set correctly upon error.
   *
   * @param channel The channel.
   * @param end The reference to the protected {@code AbstractInterruptibleChannel#end(boolean)}
   *          method.
   * @param complete {@code true} if the block started with {@code begin} succeeded without an
   *          exception.
   * @throws AsynchronousCloseException on error.
   */
  static void endInterruptable(AbstractInterruptibleChannel channel, EndMethod end,
      boolean complete) throws AsynchronousCloseException {
    try {
      end.end(complete);
    } catch (AsynchronousCloseException e) {
      throw closeAndThrow(channel, e);
    }
  }

  private static <T extends Exception> T closeAndThrow(AbstractInterruptibleChannel channel,
      @NonNull T exc) {
    if (channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException e2) {
        exc.addSuppressed(e2);
      }
    }
    return exc;
  }

  /**
   * Makes sure that upon an exception that is documented to have the channel be closed the channel
   * is indeed closed before throwing that exception. If the exception is also documented to have
   * the "Thread interrupted" state be set, make sure that this state is actually set as well.
   *
   * @param channel The channel to work with.
   * @param e The exception
   * @return The exception.
   */
  static IOException handleException(AbstractInterruptibleChannel channel, IOException e) {
    if (e instanceof SocketClosedException || e instanceof ClosedChannelException
        || e instanceof BrokenPipeSocketException) {
      if (e instanceof SocketClosedByInterruptException
          || e instanceof ClosedByInterruptException) {
        Thread t = Thread.currentThread();
        if (!t.isInterrupted()) {
          t.interrupt();
        }
      }
      return closeAndThrow(channel, e);
    } else {
      return e;
    }
  }
}