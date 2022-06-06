package org.newsclub.net.unix;

import java.net.SocketAddress;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import com.kohlschutter.testutil.AvailabilityRequirement;

/**
 * Tests the {@code Socket#getOption(SocketOption)} API available since Java 9.
 * 
 * This class (in src/test/java8) is a stub that overrides this type so we can compile for Java 8
 * and, at the same time, acknowledge the absence of the test programmatically in jUnit.
 * 
 * @author Christian Kohlschütter
 */
public abstract class StandardSocketOptionsTest<A extends SocketAddress> extends SocketTestBase<A> {
  protected StandardSocketOptionsTest(AddressSpecifics<A> asp) {
    super(asp);
  }

  @Test
  @AvailabilityRequirement(classes = {"java.lang.ProcessHandle"}, //
      message = "This test requires Java 9 or later")
  public void testUnconnectedServerSocketOptions() throws Exception {
    throw new TestAbortedException();
  }

  @Test
  @AvailabilityRequirement(classes = {"java.lang.ProcessHandle"}, //
      message = "This test requires Java 9 or later")
  public void testSocketOptions() throws Exception {
    throw new TestAbortedException();
  }
}
