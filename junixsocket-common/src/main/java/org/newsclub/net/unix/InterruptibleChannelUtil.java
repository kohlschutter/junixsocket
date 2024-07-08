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
import java.nio.channels.NotYetBoundException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.util.Objects;

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
   * @param exception An optional exception that was caught in the try-catch-finally block.
   * @throws AsynchronousCloseException on error.
   */
  static void endInterruptable(AFSomeSocketChannel channel, EndMethod end, boolean complete,
      Exception exception) throws AsynchronousCloseException {
    if (!complete) {
      if (exception instanceof ClosedChannelException) {
        // we already have caught a valid exception; we don't need to throw one from within "end"
        complete = true;
      }
    }
    try {
      end.end(complete);
    } catch (AsynchronousCloseException e) {
      throw closeAndThrow(channel, e);
    }
  }

  private static <T extends Exception> T closeAndThrow(AFSomeSocketChannel channel, T exc) {
    Objects.requireNonNull(exc);
    if (channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException e2) {
        exc.addSuppressed(e2);
      }
    }
    return exc;
  }

  static IOException ioExceptionOrThrowRuntimeException(Exception exception) {
    if (exception instanceof IOException) {
      return (IOException) exception;
    } else if (exception instanceof RuntimeException) {
      throw (RuntimeException) exception;
    } else {
      throw new IllegalStateException(exception);
    }
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
  @SuppressWarnings("PMD.CognitiveComplexity")
  static Exception handleException(AFSomeSocketChannel channel, IOException e) {
    if (e instanceof NotConnectedSocketException) {
      return (NotYetConnectedException) new NotYetConnectedException().initCause(e);
    } else if (e instanceof NotBoundSocketException) {
      return (NotYetBoundException) new NotYetBoundException().initCause(e);
    }

    if (e instanceof InvalidArgumentSocketException) {
      if (channel instanceof AFServerSocketChannel<?>) {
        AFServerSocketChannel<?> sc = (AFServerSocketChannel<?>) channel;
        if (!sc.socket().isBound()) {
          return (NotYetBoundException) new NotYetBoundException().initCause(e);
        }
      } else if (channel instanceof AFSocketChannel<?>) {
        if (!((AFSocketChannel<?>) channel).socket().isConnected()) {
          return (NotYetConnectedException) new NotYetConnectedException().initCause(e);
        }
      }
    }

    if (e instanceof SocketClosedException || e instanceof ClosedChannelException
        || e instanceof BrokenPipeSocketException) {
      Thread t = Thread.currentThread();

      if (e instanceof SocketClosedByInterruptException
          || e instanceof ClosedByInterruptException) {
        if (!t.isInterrupted()) {
          t.interrupt();
        }
      }

      if (!(e instanceof ClosedChannelException)) {
        // Make sure the caught exception is transformed into the expected exception
        if (t.isInterrupted()) {
          e = (ClosedByInterruptException) new ClosedByInterruptException().initCause(e);
        } else if (e instanceof BrokenPipeSocketException) {
          e = (AsynchronousCloseException) new AsynchronousCloseException().initCause(e);
        } else {
          e = (ClosedChannelException) new ClosedChannelException().initCause(e);
        }
      }

      return closeAndThrow(channel, e);
    } else {
      return e;
    }
  }
}
