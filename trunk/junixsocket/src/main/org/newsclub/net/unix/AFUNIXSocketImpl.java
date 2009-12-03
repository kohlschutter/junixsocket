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

    protected void accept(SocketImpl s) throws IOException {
        AFUNIXSocketImpl si = (AFUNIXSocketImpl) s;
        NativeUnixSocket.accept(socketFile, fd, si.fd);
        si.socketFile = socketFile;
        si.connected = true;
    }

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
        AFUNIXSocketAddress address = (AFUNIXSocketAddress) addr;
        socketFile = address.getSocketFile();
        NativeUnixSocket.bind(socketFile, fd, backlog);
        bound = true;
        this.localport = address.getPort();
    }

    protected void bind(InetAddress host, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    private void checkClose() throws IOException {
        if (closedInputStream && closedOutputStream) {
            // close();
        }
    }

    protected synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (fd.valid()) {
            NativeUnixSocket.close(fd);
        }
        if (bound) {
            NativeUnixSocket.unlink(socketFile);
        }
        connected = false;
    }

    protected void connect(String host, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    protected void connect(InetAddress address, int port) throws IOException {
        throw new SocketException("Cannot bind to this type of address: "
                + InetAddress.class);
    }

    protected void connect(SocketAddress addr, int timeout) throws IOException {
        if (!(addr instanceof AFUNIXSocketAddress)) {
            throw new SocketException("Cannot bind to this type of address: "
                    + addr.getClass());
        }
        AFUNIXSocketAddress address = (AFUNIXSocketAddress) addr;
        socketFile = address.getSocketFile();
        NativeUnixSocket.connect(socketFile, fd);
        this.address = address.getAddress();
        this.port = address.getPort();
        this.localport = 0;
        this.connected = true;
    }

    protected void create(boolean stream) throws IOException {
    }

    private final AFUNIXInputStream in = new AFUNIXInputStream();
    private final AFUNIXOutputStream out = new AFUNIXOutputStream();

    protected InputStream getInputStream() throws IOException {
        if (!connected && !bound) {
            throw new IOException("Not connected/not bound");
        }
        return in;
    }

    protected OutputStream getOutputStream() throws IOException {
        if (!connected && !bound) {
            throw new IOException("Not connected/not bound");
        }
        return out;
    }

    protected void listen(int backlog) throws IOException {
        NativeUnixSocket.listen(fd, backlog);
    }

    protected void sendUrgentData(int data) throws IOException {
        NativeUnixSocket.write(fd, new byte[] { (byte) (data & 0xFF) }, 0, 1);
    }

    private final class AFUNIXInputStream extends InputStream {
        private boolean closed = false;

        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException(
                        "This InputStream has already been closed.");
            }
            try {
                return NativeUnixSocket.read(fd, b, off, len);
            } catch (IOException e) {
                throw (IOException) new IOException(e.getMessage() + " at "
                        + AFUNIXSocketImpl.this.toString()).initCause(e);
            }
        }

        private byte[] buf1 = new byte[1];

        public int read() throws IOException {
            synchronized (buf1) {
                int numRead = read(buf1, 0, 1);
                if (numRead == 0) {
                    return -1;
                } else {
                    return buf1[0] & 0xFF;
                }
            }
        }

        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (fd.valid()) {
                NativeUnixSocket.shutdown(fd, SHUT_RD);
            }

            closedInputStream = true;
            checkClose();
        }
    }

    private final class AFUNIXOutputStream extends OutputStream {
        private boolean closed = false;

        private byte[] buf1 = new byte[1];

        public void write(int b) throws IOException {
            synchronized (buf1) {
                buf1[0] = (byte) b;
                write(buf1, 0, 1);
            }
        }

        public void write(byte b[], int off, int len) throws IOException {
            if (closed) {
                throw new AFUNIXSocketException(
                        "This OutputStream has already been closed.");
            }
            try {
                while (len > 0 && !Thread.interrupted()) {
                    int written = NativeUnixSocket.write(fd, b, off, len);
                    if (written == -1) {
                        throw new IOException("Unspecific error while writing");
                    }
                    len -= written;
                    off += written;
                }
            } catch (IOException e) {
                throw (IOException) new IOException(e.getMessage() + " at "
                        + AFUNIXSocketImpl.this.toString()).initCause(e);
            }
        }

        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (fd.valid()) {
                NativeUnixSocket.shutdown(fd, SHUT_WR);
            }
            closedOutputStream = true;
            checkClose();
        }
    }

    public String toString() {
        return super.toString() + "[fd=" + fd + "; file=" + this.socketFile
                + "; connected=" + connected + "; bound=" + bound + "]";
    }

    private static int expectInteger(Object value) throws SocketException {
        int v;
        try {
            v = (Integer) value;
        } catch (ClassCastException e) {
            throw new AFUNIXSocketException("Unsupport value: " + value, e);
        } catch (NullPointerException e) {
            throw new AFUNIXSocketException("Value must not be null", e);
        }
        return v;
    }

    private static int expectBoolean(Object value) throws SocketException {
        int v;
        try {
            v = ((Boolean) value).booleanValue() ? 1 : 0;
        } catch (ClassCastException e) {
            throw new AFUNIXSocketException("Unsupport value: " + value, e);
        } catch (NullPointerException e) {
            throw new AFUNIXSocketException("Value must not be null", e);
        }
        return v;
    }

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
        } catch (AFUNIXSocketException e) {
            throw e;
        } catch (Exception e) {
            throw new AFUNIXSocketException("Error while getting option", e);
        }
        throw new AFUNIXSocketException("Unsupported option: " + optID);
    }

    public void setOption(int optID, Object value) throws SocketException {
        try {
            switch (optID) {
            case SocketOptions.SO_LINGER:

                if (value instanceof Boolean) {
                    boolean b = (Boolean) value;
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
            case SocketOptions.SO_TIMEOUT:
                NativeUnixSocket.setSocketOptionInt(fd, optID,
                        expectInteger(value));
                return;
            case SocketOptions.SO_KEEPALIVE:
            case SocketOptions.TCP_NODELAY:
            case SocketOptions.SO_RCVBUF:
            case SocketOptions.SO_SNDBUF:
                NativeUnixSocket.setSocketOptionInt(fd, optID,
                        expectBoolean(value));
                return;
            }
        } catch (AFUNIXSocketException e) {
            throw e;
        } catch (Exception e) {
            throw new AFUNIXSocketException("Error while setting option", e);
        }
        throw new AFUNIXSocketException("Unsupported option: " + optID);
    }

    protected void shutdownInput() throws IOException {
        if (!closed && fd.valid()) {
            NativeUnixSocket.shutdown(fd, SHUT_RD);
        }
    }

    protected void shutdownOutput() throws IOException {
        if (!closed && fd.valid()) {
            NativeUnixSocket.shutdown(fd, SHUT_WR);
        }
    }
}
