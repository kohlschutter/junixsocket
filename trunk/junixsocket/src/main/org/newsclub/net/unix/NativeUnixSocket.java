/**
 * junixsocket
 * 
 * Copyright (c) 2009 NewsClub, Christian Kohlschütter
 * 
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.newsclub.net.unix;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JNI connector to native JNI C code.
 * 
 * @author Christian Kohlschütter
 */
final class NativeUnixSocket {
    // private static final String PROP_LIBRARY_DIR =
    // "org.newsclub.net.unix.library.path";

    static void load() {
        if (!isLoaded()) {
            final String osName = System.getProperty("os.name");
            final String arch = System.getProperty("os.arch");
            final String javaSpec = System
                    .getProperty("java.specification.version");
            final String prefix = "lib";
            String suffix = ".so";
            final String os = osName.replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase();
            if ("macosx".equals(os)) {
                suffix = ".dylib";
            } else if ("linux".equals(os) || "freebsd".equals(os)
                    || "sunos".equals(os)) {
                suffix = ".so";
            } else {
                Logger.getLogger(NativeUnixSocket.class.getName()).log(
                        Level.WARNING,
                        "Operating System not officially supported by junixsocket: "
                                + osName);
            }

            final String[] javaSpecs = "1.5".equals(javaSpec) ? new String[] { javaSpec }
                    : new String[] { javaSpec, "1.5" };

            InputStream in = null;

            final List<String> paths = new ArrayList<String>();
            String libId = null;
            for (final String js : javaSpecs) {
                libId = prefix + "junixsocket-" + os + "-" + js + "-" + arch;
                paths.add(libId + suffix);

                in = NativeUnixSocket.class.getResourceAsStream(libId + suffix);
                if (in != null) {
                    break;
                }
            }

            if (in == null) {
                throw new UnsatisfiedLinkError(
                        "Could not find library in classpath, tried: " + paths);
            }

            final File tmpLib;
            try {
                tmpLib = File.createTempFile(libId, suffix);
                tmpLib.deleteOnExit();
                final byte[] buf = new byte[4096];
                final FileOutputStream fos = new FileOutputStream(tmpLib);
                int r;
                while ((r = in.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
                fos.close();
                in.close();
            } catch (final IOException e) {
                throw (UnsatisfiedLinkError) new UnsatisfiedLinkError(
                        "Could not create temporary file for library")
                        .initCause(e);
            }

            // System.out.println("tmpFile: "+tmpLib);

            System.load(tmpLib.getAbsolutePath());

            loaded = true;
        }
    }

    private static boolean loaded = false;

    static {
        load();
    }

    static boolean isLoaded() {
        return loaded;
    }

    static void checkSupported() {
        load();
    }

    native static void bind(final String socketFile, final FileDescriptor fd,
            final int backlog) throws IOException;

    native static void listen(final FileDescriptor fd, final int backlog)
            throws IOException;

    native static void accept(final String socketFile,
            final FileDescriptor fdServer, final FileDescriptor fd)
            throws IOException;

    native static void
            connect(final String socketFile, final FileDescriptor fd)
                    throws IOException;

    native static int read(final FileDescriptor fd, byte[] b, int off, int len)
            throws IOException;

    native static int
            write(final FileDescriptor fd, byte[] b, int off, int len)
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

    static void setPort1(AFUNIXSocketAddress addr, int port)
            throws AFUNIXSocketException {
        if (port < 0) {
            throw new IllegalArgumentException("port out of range:" + port);
        }

        boolean setOk = false;
        try {
            final Field holderField = InetSocketAddress.class
                    .getDeclaredField("holder");
            if (holderField != null) {
                holderField.setAccessible(true);

                final Object holder = holderField.get(addr);
                if (holder != null) {
                    System.out.println(Arrays.toString(holder.getClass()
                            .getDeclaredFields()));

                    final Field portField = holder.getClass().getDeclaredField(
                            "port");
                    if (portField != null) {
                        portField.setAccessible(true);
                        portField.set(holder, port);
                        setOk = true;
                    }
                }
            } else {
                setPort(addr, port);
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            if (e instanceof AFUNIXSocketException) {
                throw (AFUNIXSocketException) e;
            }
            throw new AFUNIXSocketException("Could not set port", e);
        }
        if (!setOk) {
            throw new AFUNIXSocketException("Could not set port");
        }
    }
}
