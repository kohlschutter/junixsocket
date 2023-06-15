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
package org.newsclub.net.unix.tipc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.AFSocketCapabilityRequirement;
import org.newsclub.net.unix.AFTIPCSocketAddress;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;

@AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_TIPC)
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
public final class AFTIPCTopologyWatcherTest extends
    org.newsclub.net.unix.SocketTestBase<AFTIPCSocketAddress> {

  public AFTIPCTopologyWatcherTest() throws IOException {
    super(AFTIPCAddressSpecifics.INSTANCE);
  }

  @Test
  @SuppressWarnings("PMD.CognitiveComplexity")
  public void testSubscriptionEvents() throws IOException, InterruptedException, ExecutionException,
      TimeoutException {
    AtomicInteger numPortEvents = new AtomicInteger(0);
    AtomicInteger numServiceEvents = new AtomicInteger(0);
    AtomicBoolean foundTestService = new AtomicBoolean(false);
    AtomicBoolean foundTestServiceWithdrawal = new AtomicBoolean(false);

    try (AFTIPCDatagramSocket testServiceSock = AFTIPCDatagramSocket.newInstance()) {
      testServiceSock.bind(AFTIPCSocketAddress.ofServiceRange(1234, 5678, 91011));

      final AFTIPCSocketAddress bindAddress = testServiceSock.getLocalSocketAddress();

      CompletableFuture<Void> cf = new CompletableFuture<>();
      try (AFTIPCTopologyWatcher watcher = new AFTIPCTopologyWatcher() {

        @Override
        protected void onEvent(AFTIPCTopologyEvent event) throws IOException {
          assertTrue(isRunning());
          assertThrows(IllegalStateException.class, () -> runLoop());

          if (event.isPublished()) {
            if (event.isPort()) {
              assertFalse(event.isService());
              assertFalse(event.isCancellationRequest());

              numPortEvents.incrementAndGet();
            } else if (event.isService()) {
              assertFalse(event.isPort());
              assertFalse(event.isCancellationRequest());

              numServiceEvents.incrementAndGet();
              if (event.getAddress().equals(bindAddress)) {
                // we found our service
                foundTestService.set(true);
                stopLoop();
              }
            }
          } else if (event.isWithdrawn()) {
            assertFalse(event.isCancellationRequest());

            if (event.isService()) {
              if (event.getAddress().equals(bindAddress) && event.getFoundLower() == 5678 && event
                  .getFoundUpper() == 91011) {
                // we found our service
                foundTestServiceWithdrawal.set(true);
                stopLoop();
                cf.complete(null);
              }
            }
          }
        }
      }) {
        watcher.addPortSubscription();
        watcher.addServiceSubscription(1234);
        Thread t = new Thread(() -> {
          try {
            Thread.sleep(1000);
            watcher.close();
            cf.complete(null);
          } catch (Exception e) {
            cf.completeExceptionally(e);
          }
        });
        t.setDaemon(true);
        t.start();
        watcher.runLoop();
        assertFalse(watcher.isRunning());

        if (numPortEvents.get() == 0) {
          // this can happen if no bearer was enabled (i.e., TIPC running in standalone mode)
          // FIXME: check if that's the case
          throw new TestAbortedWithImportantMessageException(
              MessageType.TEST_ABORTED_SHORT_INFORMATIONAL,
              "TIPC enabled but no bearer set up? If you don't need TIPC, consider \"rmmod tipc\".");
        }
        assertNotEquals(0, numPortEvents.get(), "We should have seen at least one port event");
        assertNotEquals(0, numServiceEvents.get(), "We should have seen at least service event");
        assertTrue(foundTestService.get(), "We should have found our test service");
        assertFalse(foundTestServiceWithdrawal.get());

        // closing a service should trigger an event
        testServiceSock.close();
        watcher.runLoop();

        assertTrue(foundTestServiceWithdrawal.get());
      }
      cf.get(1, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testGetNodeId() throws Exception {
    AFTIPCSocketAddress addr = AFTIPCDatagramSocket.newInstance().getLocalSocketAddress();
    assertNotNull(addr);
    Objects.requireNonNull(addr);
    String str = AFTIPCSocket.getNodeIdHexString(addr.getTIPCNodeHash());
    if (str == null) {
      // old kernel
    } else {
      assertNotEquals(0, str.length());
    }
  }

  @Test
  public void testClusterConnectivity() throws Exception {
    try (AFTIPCTopologyWatcher watcher = new AFTIPCTopologyWatcher(0) {

      @Override
      protected void onEvent(AFTIPCTopologyEvent event) throws IOException {
        if (event.isTimeout()) {
          stopLoop();
          return;
        } else if (event.isPublished()) {
          // something like "f875a40e707d:eth0-8c1645f2ce27:eth0"
          String linkName = event.getLinkName();
          if (linkName == null) {
            // old kernel
          } else {
            assertNotEquals(0, linkName.length());
            assertTrue(linkName.indexOf(':') >= 0, linkName);
          }
        }
      }

    }) {
      watcher.addLinkStateSubscription();
      watcher.runLoop();
    }
  }
}
