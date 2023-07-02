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
package org.newsclub.net.unix.rmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFSocketCapability;
import org.newsclub.net.unix.rmi.RemoteCloseableThing.IsCloseable;
import org.newsclub.net.unix.rmi.RemoteCloseableThing.NotCloseable;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

/**
 * Tests {@link RemoteCloseable}.
 *
 * @author Christian Kohlschütter
 */
@SuppressFBWarnings({
    "THROWS_METHOD_THROWS_CLAUSE_THROWABLE", "THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION"})
@AFSocketCapabilityRequirement({AFSocketCapability.CAPABILITY_UNIX_DOMAIN})
public class RemoteCloseableTest extends TestBase {
  public RemoteCloseableTest() throws IOException {
    super();
  }

  @Test
  public void testRemoteCloseableWithACloseableThing() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    svc.remoteCloseableThingResetNumberOfCloseCalls(IsCloseable.class);

    try (RemoteCloseable<? extends IsCloseable> remoteCloseable = svc.remoteCloseable(
        IsCloseable.class)) {
      try (IsCloseable unused = remoteCloseable.get()) {
        assertEquals(0, svc.remoteCloseableThingNumberOfCloseCalls(IsCloseable.class));
        // forcibly calling close here unexports the RemoteCloseable
        remoteCloseable.close();
        assertEquals(1, svc.remoteCloseableThingNumberOfCloseCalls(IsCloseable.class));

        assertThrows(NoSuchObjectException.class, () -> {
          remoteCloseable.close();
        });
      }
    } catch (NoSuchObjectException e) {
      // expected — since the object was forcibly closed above, it was unexported already.
      // ideally, RMI could gracefully handle calling #close() on an proxy that points to an
      // unexported object.
    }
    assertEquals(1, svc.remoteCloseableThingNumberOfCloseCalls(IsCloseable.class));

    try (RemoteCloseable<? extends IsCloseable> remoteCloseable = svc.remoteCloseable(
        IsCloseable.class)) {
      try (IsCloseable unused = remoteCloseable.get()) {
        // no exception thrown
      }
    }

    // The underlying object still remains on the server-side, so the call count keeps going up
    assertEquals(2, svc.remoteCloseableThingNumberOfCloseCalls(IsCloseable.class));
  }

  @Test
  public void testRemoteCloseableWithANotCloseableThing() throws IOException, NotBoundException {
    TestService svc = lookupTestService();

    svc.remoteCloseableThingResetNumberOfCloseCalls(NotCloseable.class);

    try (RemoteCloseable<? extends NotCloseable> remoteCloseable = svc.remoteCloseable(
        NotCloseable.class)) {
      @SuppressWarnings("null")
      @NonNull
      RemoteCloseableThing testNotCloseable = remoteCloseable.get();
      Objects.requireNonNull(testNotCloseable.toString());
      assertEquals(0, svc.remoteCloseableThingNumberOfCloseCalls(NotCloseable.class));
      // forcibly calling close here unexports the RemoteCloseable
      remoteCloseable.close();
      assertEquals(0, svc.remoteCloseableThingNumberOfCloseCalls(NotCloseable.class));

      assertThrows(NoSuchObjectException.class, () -> {
        remoteCloseable.close();
      });
    } catch (NoSuchObjectException e) {
      // expected — since the object was forcibly closed above, it was unexported already.
      // ideally, RMI could gracefully handle calling #close() on an proxy that points to an
      // unexported object.
    }
    assertEquals(0, svc.remoteCloseableThingNumberOfCloseCalls(NotCloseable.class));

    try (RemoteCloseable<? extends NotCloseable> unused = svc.remoteCloseable(NotCloseable.class)) {
      // no exception thrown
    }

    // The underlying object still remains on the server-side, so the call count keeps going up
    assertEquals(0, svc.remoteCloseableThingNumberOfCloseCalls(NotCloseable.class));
  }
}
