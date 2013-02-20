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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;

/**
 * The Java-part of the {@link AFUNIXSocket} implementation.
 * 
 * @author Christian Kohlschütter
 */
class AFUNIXSocketImpl extends SocketImpl {
    private static final int SHUT_RD = 0;
    private static final int SHUT_WR = 1;
    private static final int SHUT_RD_WR = 2;

    private String socketFile;
    private boolean closed = false;
    private boolean bound = false;
    private boolean connected = false;

    private boolean closedInputStream = false;
    private boolean closedOutputStream = false;

    public AFUNIXSocketImpl() {
        super();
        this.fd = new FileDescriptor();
    }

    FileDescriptor getFD() {
        return fd;
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        final AFUNIXSocketImpl si = (AFUNIXSocketImpl) s;
        NativeUnixSocket.accept(socketFile, fd, si.fd);
        si.socketFile = socketFile;
        si.connected = true;
    }

    @Override
    protected int available() throws IOException {
        return NativeUnixSocket.available(fd);
    }

    protected void bind(SocketAddress addr) throws IOException {
        bind(0, addr);
    }

    protected void bind(int backlog, SocketAddress addr) throws IOException {
        if (!(addr instanceof AFUNIXSocketAddress)) {
            throw new SocketException("Cannot bind to this type of address: "
                    + addr.getClass());
        }
        final AFUNIXSocketAddress address = (AFUNIXSocketAddress) addr;
        socketFile = address.getSocketFile();
        NativeUnixSocket.bind(socketFile, fd, backlog);
        bound = true;
        this.localport = address.getPort();
    }

    @Override
    protected void bind(InetAddress host, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    private void checkClose() throws IOException {
        if (closedInputStream && closedOutputStream) {
            // close();
        }
    }

    @Override
    protected synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (fd.valid()) {
            NativeUnixSocket.shutdown(fd, SHUT_RD_WR);
            NativeUnixSocket.close(fd);
        }
        if (bound) {
            NativeUnixSocket.unlink(socketFile);
        }
        connected = false;
    }

    @Override
    protected void connect(String host, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    @Override
    protected void connect(SocketAddress addr, int timeout) throws IOException {
        if (!(addr instanceof AFUNIXSocketAddress)) {
            throw new SocketException("Cannot bind to this type of address: "
                    + addr.getClass());
        }
        final AFUNIXSocketAddress address = (AFUNIXSocketAddress) addr;
        socketFile = address.getSocketFile();
        NativeUnixSocket.connect(socketFile, fd);
        this.address = address.getAddress();
        this.port = address.getPort();
        this.localport = 0;
        this.connected = true;
    }

    @Override
    protected void create(boolean stream) throws IOException {
    }

    private final AFUNIXInputStream in = new AFUNIXInputStream();
    private final AFUNIXOutputStream out = new AFUNIXOutputStream();

    @Override
    protected InputStream getInputStream() throws IOException {
        if (!connected && !bound) {
            throw new IOException("Not connected/not bound");
        }
        return in;
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        if (!connected && !bound) {
            throw new IOException("Not connected/not bound");
        }
        return out;
    }

    @Override
    protected void listen(int backlog) throws IOException {
        NativeUnixSocket.listen(fd, backlog);
    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        NativeUnixSocket.write(fd, new byte[] { (byte) (data & 0xFF) }, 0, 1);
    }

    private final class AFUNIXInputStream extends InputStream {
        private boolean streamClosed = false;

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (streamClosed) {
                throw new IOException(
                        "This InputStream has already been closed.");
            }
            if (len == 0) {
                return 0;
            }
            try {
                return NativeUnixSocket.read(fd, b, off, len);
            } catch (final IOException e) {
                throw (IOException) new IOException(e.getMessage() + " at "
                        + AFUNIXSocketImpl.this.toString()).initCause(e);
            }
        }

        private byte[] buf1 = new byte[1];

        @Override
        public int read() throws IOException {
            synchronized (buf1) {
                final int numRead = read(buf1, 0, 1);
                if (numRead <= 0) {
                    return -1;
                } else {
                    return buf1[0] & 0xFF;
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (streamClosed) {
                return;
            }
            streamClosed = true;
            if (fd.valid()) {
                NativeUnixSocket.shutdown(fd, SHUT_RD);
            }

            closedInputStream = true;
            checkClose();
        }

        @Override
        public int available() throws IOException {
            final int av = NativeUnixSocket.available(fd);
            return av;
        }
    }

    private final class AFUNIXOutputStream extends OutputStream {
        private boolean streamClosed = false;

        private byte[] buf1 = new byte[1];

        @Override
        public void write(int b) throws IOException {
            synchronized (buf1) {
                buf1[0] = (byte) b;
                write(buf1, 0, 1);
            }
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            if (streamClosed) {
                throw new AFUNIXSocketException(
                        "This OutputStream has already been closed.");
            }
            try {
                while (len > 0 && !Thread.interrupted()) {
                    final int written = NativeUnixSocket.write(fd, b, off, len);
                    if (written == -1) {
                        throw new IOException("Unspecific error while writing");
                    }
                    len -= written;
                    off += written;
                }
            } catch (final IOException e) {
                throw (IOException) new IOException(e.getMessage() + " at "
                        + AFUNIXSocketImpl.this.toString()).initCause(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (streamClosed) {
                return;
            }
            streamClosed = true;
            if (fd.valid()) {
                NativeUnixSocket.shutdown(fd, SHUT_WR);
            }
            closedOutputStream = true;
            checkClose();
        }
    }

    @Override
    public String toString() {
        return super.toString() + "[fd=" + fd + "; file=" + this.socketFile
                + "; connected=" + connected + "; bound=" + bound + "]";
    }

    private static int expectInteger(Object value) throws SocketException {
        int v;
        try {
            v = (Integer) value;
        } catch (final ClassCastException e) {
            throw new AFUNIXSocketException("Unsupport value: " + value, e);
        } catch (final NullPointerException e) {
            throw new AFUNIXSocketException("Value must not be null", e);
        }
        return v;
    }

    private static int expectBoolean(Object value) throws SocketException {
        int v;
        try {
            v = ((Boolean) value).booleanValue() ? 1 : 0;
        } catch (final ClassCastException e) {
            throw new AFUNIXSocketException("Unsupport value: " + value, e);
        } catch (final NullPointerException e) {
            throw new AFUNIXSocketException("Value must not be null", e);
        }
        return v;
    }

    @Override
    public Object getOption(int optID) throws SocketException {
        try {
            switch (optID) {
            case SocketOptions.SO_KEEPALIVE:
            case SocketOptions.TCP_NODELAY:
                return NativeUnixSocket.getSocketOptionInt(fd, optID) != 0 ? true
                        : false;
            case SocketOptions.SO_LINGER:
            case SocketOptions.SO_TIMEOUT:
            case SocketOptions.SO_RCVBUF:
            case SocketOptions.SO_SNDBUF:
                return NativeUnixSocket.getSocketOptionInt(fd, optID);
            }
        } catch (final AFUNIXSocketException e) {
            throw e;
        } catch (final Exception e) {
            throw new AFUNIXSocketException("Error while getting option", e);
        }
        throw new AFUNIXSocketException("Unsupported option: " + optID);
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
        try {
            switch (optID) {
            case SocketOptions.SO_LINGER:

                if (value instanceof Boolean) {
                    final boolean b = (Boolean) value;
                    if (b) {
                        throw new SocketException(
                                "Only accepting Boolean.FALSE here");
                    }
                    NativeUnixSocket.setSocketOptionInt(fd, optID, -1);
                    return;
                }
                NativeUnixSocket.setSocketOptionInt(fd, optID,
                        expectInteger(value));
                return;
            case SocketOptions.SO_RCVBUF:
            case SocketOptions.SO_SNDBUF:
            case SocketOptions.SO_TIMEOUT:
                NativeUnixSocket.setSocketOptionInt(fd, optID,
                        expectInteger(value));
                return;
            case SocketOptions.SO_KEEPALIVE:
            case SocketOptions.TCP_NODELAY:
                NativeUnixSocket.setSocketOptionInt(fd, optID,
                        expectBoolean(value));
                return;
            }
        } catch (final AFUNIXSocketException e) {
            throw e;
        } catch (final Exception e) {
            throw new AFUNIXSocketException("Error while setting option", e);
        }
        throw new AFUNIXSocketException("Unsupported option: " + optID);
    }

    @Override
    protected void shutdownInput() throws IOException {
        if (!closed && fd.valid()) {
            NativeUnixSocket.shutdown(fd, SHUT_RD);
        }
    }

    @Override
    protected void shutdownOutput() throws IOException {
        if (!closed && fd.valid()) {
            NativeUnixSocket.shutdown(fd, SHUT_WR);
        }
    }
}
