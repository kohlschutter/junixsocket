/**
 * junixsocket
 *
 * Copyright 2009-2019 Christian Kohlschütter
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
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Helper class to simplify working with exchanging file descriptors via ancillary messages.
 * 
 * @author Christian Kohlschütter
 */
public final class AncillaryFileDescriptors {
  private static final ThreadLocal<WeakReference<Support>> TL_SUPPORT_REF = new ThreadLocal<>();

  private AncillaryFileDescriptors() {
    throw new UnsupportedOperationException("No instances");
  }

  /**
   * Support for exchanging file descriptors via ancillary messages.
   * 
   * @author Christian Kohlschütter
   */
  public interface Support {
    /**
     * Retrieves an array of incoming {@link FileDescriptor}s that were sent as ancillary messages,
     * along with a call to {@link InputStream#read()}, etc.
     * 
     * NOTE: Another call to this method will not return the same file descriptors again (most
     * likely, {@code null} will be returned).
     * 
     * @return The file decriptors, or {@code null} if none were available.
     * @throws IOException if the operation fails.
     */
    FileDescriptor[] getReceivedFileDescriptors() throws IOException;

    /**
     * Clears the queue of incoming {@link FileDescriptor}s that were sent as ancillary messages.
     */
    void clearReceivedFileDescriptors();

    /**
     * Sets a list of {@link FileDescriptor}s that should be sent as an ancillary message along with
     * the next write.
     * 
     * NOTE: There can only be one set of file descriptors active until the write completes.
     * 
     * @param fdescs The file descriptors, or {@code null} if none.
     * @throws IOException if the operation fails.
     */
    void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException;

    /**
     * Ensures a minimum ancillary receive buffer size.
     * 
     * @param minSize The minimum size (in bytes).
     */
    void ensureAncillaryReceiveBufferSize(int minSize);
  }

  static void setSupportRef(Support ref) {
    TL_SUPPORT_REF.set(new WeakReference<Support>(ref));
  }

  /**
   * DO NOT USE. This is for junixsocket-rmi's RemoteFileDescriptor only.
   */
  public static Support getAndClearSupportRef() {
    WeakReference<Support> ref = TL_SUPPORT_REF.get();
    if (ref == null) {
      return null;
    }
    TL_SUPPORT_REF.remove();
    return ref.get();
  }
}
