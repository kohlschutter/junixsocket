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
package org.newsclub.net.unix.darwin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class KernelControlNamesTest {
  @Test
  // @AFSocketCapabilityRequirement(AFSocketCapability.CAPABILITY_DARWIN)
  public void testStandardKernelControlNames() throws Exception {
    try (AFSYSTEMDatagramSocket socket = AFSYSTEMDatagramSocket.newInstance()) {
      assertThrows(IOException.class, () -> socket.getNodeIdentity("definitely.missing"));

      int errors = 0;
      Set<Integer> ids = new LinkedHashSet<>();
      for (WellKnownKernelControlNames n : WellKnownKernelControlNames.values()) {
        int id;
        try {
          id = socket.getNodeIdentity(n.getControlName());
          assertTrue(id > 0, "id should be a positive integer");
          ids.add(id);
        } catch (SocketException e) {
          errors++;
          continue;
        }
      }

      System.out.println("Resolved control names " + ids.size() + "/" + WellKnownKernelControlNames
          .values().length);

      assertEquals(WellKnownKernelControlNames.values().length - errors, ids.size());
    }
  }
}
