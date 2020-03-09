/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
final class NativeLibraryLoader implements Closeable {
  private static final String PROP_LIBRARY_OVERRIDE = "org.newsclub.net.unix.library.override";
  private static final String PROP_LIBRARY_TMPDIR = "org.newsclub.net.unix.library.tmpdir";

  private static final File TEMP_DIR;
  private static final String ARCHITECTURE_AND_OS = architectureAndOS();
  private static final String LIBRARY_NAME = "junixsocket-native";

  private static boolean loaded = false;

  static {
    String dir = System.getProperty(PROP_LIBRARY_TMPDIR, null);
    TEMP_DIR = (dir == null) ? null : new File(dir);
  }

  NativeLibraryLoader() {
  }

  private List<LibraryCandidate> tryProviderClass(String providerClassname, String artifactName)
      throws IOException, ClassNotFoundException {
    Class<?> providerClass = Class.forName(providerClassname);

    String version = getArtifactVersion(providerClass, artifactName);
    String libraryNameAndVersion = LIBRARY_NAME + "-" + version;

    return findLibraryCandidates(artifactName, libraryNameAndVersion, providerClass);
  }

  public static String getJunixsocketVersion() throws IOException {
    return getArtifactVersion(AFUNIXSocket.class, "junixsocket-common");
  }

  private static String getArtifactVersion(Class<?> providerClass, String... artifactNames)
      throws IOException {
    for (String artifactName : artifactNames) {
      Properties p = new Properties();
      String resource = "/META-INF/maven/com.kohlschutter.junixsocket/" + artifactName
          + "/pom.properties";
      try (InputStream in = providerClass.getResourceAsStream(resource)) {
        if (in == null) {
          throw new FileNotFoundException("Could not find resource " + resource + " relative to "
              + providerClass);
        }
        p.load(in);
        String version = p.getProperty("version");

        Objects.requireNonNull(version, "Could not read version from pom.properties");
        return version;
      }
    }
    throw new IllegalStateException("No artifact names specified");
  }

  private abstract static class LibraryCandidate implements Closeable {
    protected final String libraryNameAndVersion;

    protected LibraryCandidate(String libraryNameAndVersion) {
      this.libraryNameAndVersion = libraryNameAndVersion;
    }

    abstract String load() throws Exception;

    @Override
    public abstract void close();

    @Override
    public String toString() {
      return super.toString() + "[" + libraryNameAndVersion + "]";
    }
  }

  private static final class StandardLibraryCandidate extends LibraryCandidate {
    StandardLibraryCandidate(String version) {
      super(version == null ? null : LIBRARY_NAME + "-" + version);
    }

    @Override
    String load() throws Exception, LinkageError {
      if (libraryNameAndVersion != null) {
        System.loadLibrary(libraryNameAndVersion);
        return libraryNameAndVersion;
      }
      return null;
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
      return super.toString() + "(standard library path)";
    }

  }

  private static final class ClasspathLibraryCandidate extends LibraryCandidate {
    private final String artifactName;
    private final InputStream libraryIn;
    private final String path;

    ClasspathLibraryCandidate(String artifactName, String libraryNameAndVersion, String path,
        InputStream libraryIn) {
      super(libraryNameAndVersion);
      this.artifactName = artifactName;
      this.path = path;
      this.libraryIn = libraryIn;
    }

    @Override
    synchronized String load() throws IOException, LinkageError {
      if (libraryNameAndVersion == null) {
        return null;
      }
      File libFile;
      try {
        libFile = createTempFile("libtmp", System.mapLibraryName(libraryNameAndVersion));
        try (OutputStream out = new FileOutputStream(libFile)) {
          byte[] buf = new byte[4096];
          int read;
          while ((read = libraryIn.read(buf)) >= 0) {
            out.write(buf, 0, read);
          }
        } finally {
          libraryIn.close();
        }
      } catch (IOException e) {
        throw e;
      }
      System.load(libFile.getAbsolutePath());
      if (!libFile.delete()) {
        libFile.deleteOnExit();
      }
      return artifactName + "/" + libraryNameAndVersion;
    }

    @Override
    public void close() {
      try {
        libraryIn.close();
      } catch (IOException e) {
        // ignore
      }
    }

    @Override
    public String toString() {
      return super.toString() + "(" + artifactName + ":" + path + ")";
    }
  }

  private synchronized void setLoaded(String library) {
    if (!loaded) {
      loaded = true;
      AFUNIXSocket.loadedLibrary = library;
      try {
        NativeUnixSocket.init();
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private Throwable loadLibraryOverride() {
    String libraryOverride = System.getProperty(PROP_LIBRARY_OVERRIDE, "");
    if (!libraryOverride.isEmpty()) {
      try {
        System.load(libraryOverride);
        setLoaded(libraryOverride);
        return null;
      } catch (Exception | LinkageError e) {
        return e;
      }
    } else {
      return new Exception("No library specified with -D" + PROP_LIBRARY_OVERRIDE + "=");
    }
  }

  private static Object loadLibrarySyncMonitor() {
    Object monitor = NativeLibraryLoader.class.getClassLoader(); // NOPMD
    if (monitor == null) {
      // bootstrap classloader?
      return NativeLibraryLoader.class;
    } else {
      return monitor;
    }
  }

  // NOPMD
  public synchronized void loadLibrary() {
    synchronized (loadLibrarySyncMonitor()) { // NOPMD We want to lock this class' classloader.
      if (loaded) {
        // Already loaded
        return;
      }

      List<Throwable> suppressedThrowables = new ArrayList<>();
      Throwable ex = loadLibraryOverride();
      if (ex == null) {
        return;
      }
      suppressedThrowables.add(ex);

      List<LibraryCandidate> candidates = initLibraryCandidates(suppressedThrowables);

      String loadedLibraryId = null;
      for (LibraryCandidate candidate : candidates) {
        try {
          if ((loadedLibraryId = candidate.load()) != null) {
            break;
          }
        } catch (Exception | LinkageError e) {
          suppressedThrowables.add(e);
        }
      }

      for (LibraryCandidate candidate : candidates) {
        candidate.close();
      }

      if (loadedLibraryId == null) {
        throw initCantLoadLibraryError(suppressedThrowables);
      }

      setLoaded(loadedLibraryId);
    }
  }

  private UnsatisfiedLinkError initCantLoadLibraryError(List<Throwable> suppressedThrowables) {
    String message = "Could not load native library " + LIBRARY_NAME + " for architecture "
        + ARCHITECTURE_AND_OS;

    String cp = System.getProperty("java.class.path", "");
    if (cp.contains("junixsocket-native-custom/target-eclipse") || cp.contains(
        "junixsocket-native-common/target-eclipse")) {
      message += "\n\n*** ECLIPSE USERS ***\nIf you're running from within Eclipse, "
          + "please close the projects \"junixsocket-native-common\" and \"junixsocket-native-custom\"\n";
    }

    UnsatisfiedLinkError e = new UnsatisfiedLinkError(message);
    for (Throwable suppressed : suppressedThrowables) {
      e.addSuppressed(suppressed);
    }
    throw e;
  }

  @SuppressWarnings("resource")
  private List<LibraryCandidate> initLibraryCandidates(List<Throwable> suppressedThrowables) {
    List<LibraryCandidate> candidates = new ArrayList<>();
    try {
      candidates.add(new StandardLibraryCandidate(getArtifactVersion(getClass(),
          "junixsocket-common", "junixsocket-core")));
    } catch (Exception e) {
      suppressedThrowables.add(e);
    }
    try {
      candidates.addAll(tryProviderClass("org.newsclub.lib.junixsocket.custom.NarMetadata",
          "junixsocket-native-custom"));
    } catch (Exception e) {
      suppressedThrowables.add(e);
    }
    try {
      candidates.addAll(tryProviderClass("org.newsclub.lib.junixsocket.common.NarMetadata",
          "junixsocket-native-common"));
    } catch (Exception e) {
      suppressedThrowables.add(e);
    }

    return candidates;
  }

  private static String architectureAndOS() {
    return System.getProperty("os.arch") + "-" + System.getProperty("os.name").replaceAll(" ", "");
  }

  @SuppressWarnings("resource")
  private List<LibraryCandidate> findLibraryCandidates(String artifactName,
      String libraryNameAndVersion, Class<?> providerClass) {
    String mappedName = System.mapLibraryName(libraryNameAndVersion);

    List<LibraryCandidate> list = new ArrayList<>();
    for (String compiler : new String[] {
        "gpp", "g++", "linker", "clang", "gcc", "cc", "CC", "icpc", "icc", "xlC", "xlC_r", "msvc",
        "icl", "ecpc", "ecc"}) {
      String path = "/lib/" + ARCHITECTURE_AND_OS + "-" + compiler + "/jni/" + mappedName;

      InputStream in;

      in = providerClass.getResourceAsStream(path);
      if (in != null) {
        list.add(new ClasspathLibraryCandidate(artifactName, libraryNameAndVersion, path, in));
      }

      // NOTE: we have to try .nodeps version _after_ trying the properly linked one.
      // While the former may throw an UnsatisfiedLinkError, this one may just terminate the VM
      // with a "symbol lookup error"
      String nodepsPath = nodepsPath(path);
      if (nodepsPath != null) {
        in = providerClass.getResourceAsStream(nodepsPath);
        if (in != null) {
          list.add(new ClasspathLibraryCandidate(artifactName, libraryNameAndVersion, nodepsPath,
              in));
        }
      }
    }
    return list;
  }

  private String nodepsPath(String path) {
    int lastDot = path.lastIndexOf('.');
    if (lastDot == -1) {
      return null;
    } else {
      return path.substring(0, lastDot) + ".nodeps" + path.substring(lastDot);
    }
  }

  private static File createTempFile(String prefix, String suffix) throws IOException {
    return File.createTempFile(prefix, suffix, TEMP_DIR);
  }

  @Override
  public void close() {
  }
}
