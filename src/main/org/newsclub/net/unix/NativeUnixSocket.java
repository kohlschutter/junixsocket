/**
 * junixsocket
 *
 * Copyright (c) 2009 NewsClub, Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
    private static final String PROP_LIBRARY_LOADED = "org.newsclub.net.unix.library.loaded";
    private static final String PROP_LIBRARY_DIR = "org.newsclub.net.unix.library.path";

    static boolean isSupported() {
        return "true".equals(System.getProperty(PROP_LIBRARY_LOADED, "false"));
    }

    static void checkSupported() {
        load();
    }

    static void load() {
        if (!isSupported()) {
            String osName = System.getProperty("os.name");
            final String arch = System.getProperty("os.arch");
            final String javaSpec = System
                    .getProperty("java.specification.version");
            String prefix = "lib";
            String suffix = ".so";
            String os = osName.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
            if ("macosx".equals(os)) {
                suffix = ".dylib";
            } else if ("linux".equals(os) || "freebsd".equals(os)
                    || "sunos".equals(os)) {
                suffix = ".so";
            } else {
                Logger.getLogger(NativeUnixSocket.class.getName()).log(
                        Level.WARNING,
                        "Operating System not officially supported by junixsocket: " + osName);
            }

            final String libDir = System.getProperty(PROP_LIBRARY_DIR,
                    NativeUnixSocketConfig.LIBRARY_PATH);

            String[] libDirs = libDir == null ? new String[] { null }
                    : new String[] { libDir, null };
            String[] javaSpecs = "1.5".equals(javaSpec) ? new String[] { javaSpec }
                    : new String[] { javaSpec, "1.5" };

            List<String> paths = new ArrayList<String>();

            UnsatisfiedLinkError ule = null;
            findLib: for (String ld : libDirs) {
                for (String js : javaSpecs) {
                    ule = null;
                    String libId = "junixsocket-" + os + "-" + js + "-" + arch;
                    try {
                        if (ld == null) {
                            paths.add("lib:" + libId);
                            System.loadLibrary(libId);
                        } else {
                            final File libFile = new File(new File(ld), prefix
                                    + libId + suffix);
                            final String absPath = libFile.getAbsolutePath();
                            paths.add(absPath);
                            System.load(absPath);
                        }
                    } catch (UnsatisfiedLinkError e) {
                        ule = e;
                    }
                    if (ule == null) {
                        break findLib;
                    }

                }
            }
            if (ule != null) {
                throw (UnsatisfiedLinkError) new UnsatisfiedLinkError(
                        "Could not load junixsocket library, tried " + paths
                                + "; please define system property "
                                + PROP_LIBRARY_DIR).initCause(ule);
            }
            System.setProperty(PROP_LIBRARY_LOADED, "true");
        }
    }

    static {
        load();
    }

    native static void bind(final String socketFile, final FileDescriptor fd,
            final int backlog) throws IOException;

    native static void listen(final FileDescriptor fd, final int backlog)
            throws IOException;

    native static void accept(final String socketFile,
            final FileDescriptor fdServer, final FileDescriptor fd)
            throws IOException;

    native static void connect(final String socketFile, final FileDescriptor fd)
            throws IOException;

    native static int read(final FileDescriptor fd, byte[] b, int off, int len)
            throws IOException;

    native static int write(final FileDescriptor fd, byte[] b, int off, int len)
            throws IOException;

    native static void close(final FileDescriptor fd) throws IOException;

    native static void shutdown(final FileDescriptor fd, int mode)
            throws IOException;

    native static int getSocketOptionInt(final FileDescriptor fd, int optionId)
            throws IOException;

    native static void setSocketOptionInt(final FileDescriptor fd,
            int optionId, int value) throws IOException;

    native static void unlink(final String socketFile) throws IOException;

    native static int available(final FileDescriptor fd) throws IOException;

    native static void initServerImpl(final AFUNIXServerSocket serverSocket,
            final AFUNIXSocketImpl impl);

    native static void setCreated(final AFUNIXSocket socket);

    native static void setConnected(final AFUNIXSocket socket);

    native static void setBound(final AFUNIXSocket socket);

    native static void setCreatedServer(final AFUNIXServerSocket socket);

    native static void setBoundServer(final AFUNIXServerSocket socket);

    native static void setPort(final AFUNIXSocketAddress addr, int port);
}
