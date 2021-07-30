/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Provides object-oriented access to file descriptors via {@link InputStream}, {@link Socket},
 * etc., depending on the file descriptor type.
 * 
 * Typical usage:
 * 
 * <code>
 * FileDescriptor fd;
 * 
 * // succeeds if fd refers to an AF_UNIX stream socket
 * AFUNIXSocket socket = FileDescriptorCast.using(fd).as(AFUNIXSocket.class); 
 * 
 * // succeeds if fd refers to an AF_UNIX datagram socket
 * AFUNIXDatagramChannel channel = FileDescriptorCast.using(fd).as(AFUNIXDatagramChannel.class); 
 * 
 * // always succeeds
 * InputStream in = FileDescriptorCast.using(fd).as(InputStream.class); 
 * OutputStream in = FileDescriptorCast.using(fd).as(OutputStream.class); 
 * </code>
 * 
 * IMPORTANT: On some platforms (e.g., Solaris, Illumos) you may need to re-apply a read timeout
 * (e.g., using {@link Socket#setSoTimeout(int)}) after obtaining the socket.
 * 
 * @author Christian Kohlschütter
 */
public final class FileDescriptorCast {
  private final FileDescriptor fdObj;

  private int localPort = 0;
  private int remotePort = 0;

  private static final CastingProviderMap GLOBAL_PROVIDERS_FINAL = new CastingProviderMap() {

    @SuppressWarnings("null")
    @Override
    protected void addProviders() {
      // FileDescriptor and Object cannot be overridden
      addProvider(FileDescriptor.class, new CastingProvider<FileDescriptor>() {
        @Override
        public FileDescriptor provideAs(FileDescriptorCast fdc,
            Class<? super FileDescriptor> desiredType) throws IOException {
          return fdc.getFileDescriptor();
        }
      });
    }
  };

  private static final CastingProviderMap GLOBAL_PROVIDERS = new CastingProviderMap() {
    @SuppressWarnings("null")
    @Override
    protected void addProviders() {
      addProvider(WritableByteChannel.class, new CastingProvider<WritableByteChannel>() {
        @SuppressWarnings("resource")
        @Override
        public WritableByteChannel provideAs(FileDescriptorCast fdc,
            Class<? super WritableByteChannel> desiredType) throws IOException {
          return new FileOutputStream(fdc.getFileDescriptor()).getChannel();
        }
      });
      addProvider(ReadableByteChannel.class, new CastingProvider<ReadableByteChannel>() {
        @SuppressWarnings("resource")
        @Override
        public ReadableByteChannel provideAs(FileDescriptorCast fdc,
            Class<? super ReadableByteChannel> desiredType) throws IOException {
          return new FileInputStream(fdc.getFileDescriptor()).getChannel();
        }
      });

      addProvider(FileChannel.class, new CastingProvider<FileChannel>() {
        @SuppressWarnings("resource")
        @Override
        public FileChannel provideAs(FileDescriptorCast fdc, Class<? super FileChannel> desiredType)
            throws IOException {
          return RAFChannelProvider.getFileChannel(fdc.getFileDescriptor());
        }
      });

      addProvider(FileOutputStream.class, new CastingProvider<FileOutputStream>() {
        @Override
        public FileOutputStream provideAs(FileDescriptorCast fdc,
            Class<? super FileOutputStream> desiredType) throws IOException {
          return new FileOutputStream(fdc.getFileDescriptor());
        }
      });
      addProvider(FileInputStream.class, new CastingProvider<FileInputStream>() {
        @Override
        public FileInputStream provideAs(FileDescriptorCast fdc,
            Class<? super FileInputStream> desiredType) throws IOException {
          return new FileInputStream(fdc.getFileDescriptor());
        }
      });
    }
  };

  private static final Map<Class<?>, CastingProviderMap> PRIMARY_TYPE_PROVIDERS_MAP =
      new HashMap<>();

  private final CastingProviderMap cpm;

  private FileDescriptorCast(FileDescriptor fdObj, CastingProviderMap cpm) {
    this.fdObj = Objects.requireNonNull(fdObj);
    this.cpm = Objects.requireNonNull(cpm);
  }

  static {
    registerCastingProviders(AFUNIXSocket.class, new CastingProviderMap() {

      @SuppressWarnings("null")
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        final CastingProvider<AFUNIXSocket> cpSocket = new CastingProvider<AFUNIXSocket>() {
          @Override
          public AFUNIXSocket provideAs(FileDescriptorCast fdc,
              Class<? super AFUNIXSocket> desiredType) throws IOException {
            return AFUNIXSocket.newInstance(fdc.getFileDescriptor(), fdc.localPort, fdc.remotePort);
          }
        };
        final CastingProvider<AFUNIXServerSocket> cpServerSocket =
            new CastingProvider<AFUNIXServerSocket>() {
              @Override
              public AFUNIXServerSocket provideAs(FileDescriptorCast fdc,
                  Class<? super AFUNIXServerSocket> desiredType) throws IOException {
                return AFUNIXServerSocket.newInstance(fdc.getFileDescriptor(), fdc.localPort,
                    fdc.remotePort);
              }
            };

        addProvider(AFUNIXSocketChannel.class, new CastingProvider<AFUNIXSocketChannel>() {
          @Override
          public AFUNIXSocketChannel provideAs(FileDescriptorCast fdc,
              Class<? super AFUNIXSocketChannel> desiredType) throws IOException {
            return cpSocket.provideAs(fdc, AFUNIXSocket.class).getChannel();
          }
        });
        addProvider(AFUNIXServerSocketChannel.class,
            new CastingProvider<AFUNIXServerSocketChannel>() {
              @Override
              public AFUNIXServerSocketChannel provideAs(FileDescriptorCast fdc,
                  Class<? super AFUNIXServerSocketChannel> desiredType) throws IOException {
                return cpServerSocket.provideAs(fdc, AFUNIXServerSocket.class).getChannel();
              }
            });
        addProvider(AFUNIXSocket.class, cpSocket);
        addProvider(AFUNIXServerSocket.class, cpServerSocket);
      }
    });

    registerCastingProviders(AFUNIXDatagramSocket.class, new CastingProviderMap() {

      @SuppressWarnings("null")
      @Override
      protected void addProviders() {
        addProviders(GLOBAL_PROVIDERS);

        final CastingProvider<AFUNIXDatagramSocket> cpDatagramSocket =
            new CastingProvider<AFUNIXDatagramSocket>() {
              @Override
              public AFUNIXDatagramSocket provideAs(FileDescriptorCast fdc,
                  Class<? super AFUNIXDatagramSocket> desiredType) throws IOException {
                return AFUNIXDatagramSocket.newInstance(fdc.getFileDescriptor(), fdc.localPort,
                    fdc.remotePort);
              }
            };

        addProvider(AFUNIXDatagramChannel.class, new CastingProvider<AFUNIXDatagramChannel>() {
          @Override
          public AFUNIXDatagramChannel provideAs(FileDescriptorCast fdc,
              Class<? super AFUNIXDatagramChannel> desiredType) throws IOException {
            return cpDatagramSocket.provideAs(fdc, AFUNIXDatagramSocket.class).getChannel();
          }
        });
        addProvider(AFUNIXDatagramSocket.class, cpDatagramSocket);
      }
    });
  }

  private static void registerCastingProviders(Class<?> primaryType, CastingProviderMap cpm) {
    PRIMARY_TYPE_PROVIDERS_MAP.put(primaryType, cpm);
  }

  private abstract static class CastingProviderMap {
    private final Map<Class<?>, CastingProvider<?>> providers = new HashMap<>();
    private final Set<Class<?>> classes = Collections.unmodifiableSet(providers.keySet());

    protected CastingProviderMap() {
      addProviders();

      addProviders(GLOBAL_PROVIDERS_FINAL);
    }

    protected abstract void addProviders();

    protected final <T> void addProvider(Class<T> type, CastingProvider<? extends T> cp) {
      Objects.requireNonNull(type);

      addProvider0(type, cp);
    }

    private void addProvider0(Class<?> type, CastingProvider<?> cp) {
      if (providers.put(type, cp) != cp) { // NOPMD
        for (Class<?> cl : type.getInterfaces()) {
          addProvider0(cl, cp);
        }
        Class<?> scl = type.getSuperclass();
        if (scl != null) {
          addProvider0(scl, cp);
        }
      }
    }

    protected final void addProviders(CastingProviderMap other) {
      if (other == null || other == this) { // NOPMD
        return;
      }
      this.providers.putAll(other.providers);
    }

    @SuppressWarnings("unchecked")
    public <T> CastingProvider<? extends T> get(Class<T> desiredType) {
      return (CastingProvider<? extends T>) providers.get(desiredType);
    }
  }

  private interface CastingProvider<T> {
    T provideAs(FileDescriptorCast fdc, Class<? super T> desiredType) throws IOException;
  }

  public static FileDescriptorCast using(FileDescriptor fdObj) throws IOException {
    if (!fdObj.valid()) {
      throw new IOException("Not a valid file descriptor");
    }
    Class<?> primaryType = NativeUnixSocket.primaryType(fdObj);
    if (primaryType == null) {
      throw new IOException("Unsupported file descriptor");
    }

    CastingProviderMap map = PRIMARY_TYPE_PROVIDERS_MAP.get(primaryType);
    return new FileDescriptorCast(fdObj, map == null ? GLOBAL_PROVIDERS : map);
  }

  public FileDescriptorCast withLocalPort(int port) {
    if (port < 0) {
      throw new IllegalArgumentException();
    }
    this.localPort = port;
    return this;
  }

  public FileDescriptorCast withRemotePort(int port) {
    if (port < 0) {
      throw new IllegalArgumentException();
    }
    this.remotePort = port;
    return this;
  }

  @SuppressWarnings("PMD.ShortMethodName")
  public @NonNull <K> K as(Class<K> desiredType) throws IOException {
    Objects.requireNonNull(desiredType);

    CastingProvider<? extends K> provider = cpm.get(desiredType);
    if (provider != null) {
      K obj = desiredType.cast(provider.provideAs(this, desiredType));
      Objects.requireNonNull(obj);
      return obj;
    } else {
      throw new ClassCastException("Cannot access file descriptor as " + desiredType);
    }
  }

  public boolean isAvailable(Class<?> desiredType) throws IOException {
    return cpm.providers.containsKey(desiredType);
  }

  public Set<Class<?>> availableTypes() {
    return cpm.classes;
  }

  public FileDescriptor getFileDescriptor() {
    return fdObj;
  }
}
