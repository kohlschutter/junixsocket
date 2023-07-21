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
package org.newsclub.net.unix;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

@SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
final class NativeLibraryLoader implements Closeable {
  private static final String PROP_LIBRARY_DISABLE = "org.newsclub.net.unix.library.disable";
  private static final String PROP_LIBRARY_OVERRIDE = "org.newsclub.net.unix.library.override";
  private static final String PROP_LIBRARY_OVERRIDE_FORCE =
      "org.newsclub.net.unix.library.override.force";
  private static final String PROP_LIBRARY_TMPDIR = "org.newsclub.net.unix.library.tmpdir";

  private static final File TEMP_DIR;
  private static final List<String> ARCHITECTURE_AND_OS = architectureAndOS();
  private static final String LIBRARY_NAME = "junixsocket-native";

  private static final AtomicBoolean LOADED = new AtomicBoolean(false);

  static {
    String dir = System.getProperty(PROP_LIBRARY_TMPDIR, null);
    TEMP_DIR = (dir == null) ? null : new File(dir);
  }

  NativeLibraryLoader() {
  }

  /**
   * Returns the temporary directory where the native library is extracted to; debugging only.
   *
   * @return The temporary directory.
   */
  static File tempDir() {
    return TEMP_DIR;
  }

  private List<LibraryCandidate> tryProviderClass(String providerClassname, String artifactName)
      throws IOException, ClassNotFoundException {
    Class<?> providerClass = Class.forName(providerClassname);

    String version = getArtifactVersion(providerClass, artifactName);
    String libraryNameAndVersion = LIBRARY_NAME + "-" + version;

    return findLibraryCandidates(artifactName, libraryNameAndVersion, providerClass);
  }

  public static String getJunixsocketVersion() throws IOException {
    // NOTE: This can't easily be tested from within the junixsocket-common Maven build

    String v = BuildProperties.getBuildProperties().get("git.build.version");
    if (v != null && !v.startsWith("$")) {
      return v;
    }

    return getArtifactVersion(AFSocket.class, "junixsocket-common");
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

    @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
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
      super(version == null ? LIBRARY_NAME : LIBRARY_NAME + "-" + version);
    }

    @Override
    @SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
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
    private final URL library;
    private final String path;

    ClasspathLibraryCandidate(String artifactName, String libraryNameAndVersion, String path,
        URL library) {
      super(libraryNameAndVersion);
      this.artifactName = artifactName;
      this.path = path;
      this.library = library;
    }

    @Override
    synchronized String load() throws IOException, LinkageError {
      if (libraryNameAndVersion == null) {
        return null;
      }

      File libDir = TEMP_DIR;

      for (int attempt = 0; attempt < 3; attempt++) {
        File libFile;
        try {
          libFile = File.createTempFile("libtmp", System.mapLibraryName(libraryNameAndVersion),
              libDir);
          try (InputStream libraryIn = library.openStream();
              OutputStream out = new FileOutputStream(libFile)) { // NOPMD UseTryWithResources
            byte[] buf = new byte[4096];
            int read;
            while ((read = libraryIn.read(buf)) >= 0) {
              out.write(buf, 0, read);
            }
          }
        } catch (IOException e) {
          throw e;
        }

        try {
          System.load(libFile.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
          String message = e.getMessage().toLowerCase(Locale.getDefault());
          if (!message.contains("perm")) {
            throw e;
          }

          // Operation not permitted; permission denied; EPERM...
          // -> tmp directory may be mounted with "noexec", try loading from user.home, user.dir

          switch (attempt) {
            case 0:
              libDir = new File(System.getProperty("user.home", "."));
              break;
            case 1:
              libDir = new File(System.getProperty("user.dir", "."));
              break;
            default:
              throw e;
          }

          continue;
        } finally {
          if (!libFile.delete()) {
            libFile.deleteOnExit();
          }
        }

        // If we reach this, then we were able to load the library
        break; // NOPMD.AvoidBranchingStatementAsLastInLoop
      }
      return artifactName + "/" + libraryNameAndVersion;
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
      return super.toString() + "(" + artifactName + ":" + path + ")";
    }
  }

  private synchronized void setLoaded(String library) {
    setLoaded0(library);
  }

  @SuppressFBWarnings("THROWS_METHOD_THROWS_RUNTIMEEXCEPTION")
  private static synchronized void setLoaded0(String library) {
    if (LOADED.compareAndSet(false, true)) {
      NativeUnixSocket.setLoaded(true);
      AFSocket.loadedLibrary = library;
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
    String libraryOverrideForce = System.getProperty(PROP_LIBRARY_OVERRIDE_FORCE, "false");

    if (libraryOverride.isEmpty() && libraryOverrideForce.startsWith("/")) {
      libraryOverride = libraryOverrideForce;
      libraryOverrideForce = "true";
    }

    if (!libraryOverride.isEmpty()) {
      try {
        System.load(libraryOverride);
        setLoaded(libraryOverride);
        return null;
      } catch (Exception | LinkageError e) {
        if (Boolean.parseBoolean(libraryOverrideForce)) {
          throw e;
        }
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

  @SuppressWarnings("null")
  public synchronized void loadLibrary() {
    synchronized (loadLibrarySyncMonitor()) { // NOPMD We want to lock this class' classloader.
      if (LOADED.get()) {
        // Already loaded
        return;
      }

      NativeUnixSocket.initPre();

      // set -Dorg.newsclub.net.unix.library.override.force=provided to assume that
      // we already have loaded the library via System.load, etc.
      if ("provided".equals(System.getProperty(PROP_LIBRARY_OVERRIDE_FORCE, ""))) {
        setLoaded("provided");
        return;
      }

      boolean provided = false;
      try {
        NativeUnixSocket.noop();
        provided = true;
      } catch (UnsatisfiedLinkError | Exception e) {
        // expected unless we manually loaded the library
      }
      if (provided) {
        setLoaded("provided");
        return;
      }

      if (Boolean.parseBoolean(System.getProperty(PROP_LIBRARY_DISABLE, "false"))) {
        throw initCantLoadLibraryError(Collections.singletonList(new UnsupportedOperationException(
            "junixsocket disabled by System.property " + PROP_LIBRARY_DISABLE)));
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
    if (suppressedThrowables != null) {
      for (Throwable suppressed : suppressedThrowables) {
        e.addSuppressed(suppressed);
      }
    }
    throw e;
  }

  private List<LibraryCandidate> initLibraryCandidates(List<Throwable> suppressedThrowables) {
    List<LibraryCandidate> candidates = new ArrayList<>();
    try {
      String version = getArtifactVersion(getClass(), "junixsocket-common", "junixsocket-core");
      if (version != null) {
        candidates.add(new StandardLibraryCandidate(version));
      }
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

    candidates.add(new StandardLibraryCandidate(null));

    return candidates;
  }

  private static String lookupArchProperty(String key, String defaultVal) {
    return System.getProperty(key, defaultVal).replaceAll("[ /\\\\'\";:\\$]", "");
  }

  private static List<String> architectureAndOS() {
    String arch = lookupArchProperty("os.arch", "UnknownArch");
    String osName = lookupArchProperty("os.name", "UnknownOS");

    String vmName = lookupArchProperty("java.vm.name", "UnknownVM");
    String vmSpecVendor = lookupArchProperty("java.vm.specification.vendor",
        "UnknownSpecificationVendor");

    List<String> list = new ArrayList<>();

    if ("Dalvik".equals(vmName) || vmSpecVendor.contains("Android")) {
      // Android identifies itself as os.name="Linux"
      // let's probe for an Android-specific library first
      list.add(arch + "-Android");
    }
    list.add(arch + "-" + osName);
    if (osName.startsWith("Windows") && !"Windows10".equals(osName)) {
      list.add(arch + "-" + "Windows10");
    }

    return list;
  }

  static List<String> getArchitectureAndOS() {
    return ARCHITECTURE_AND_OS;
  }

  private static URL validateResourceURL(URL url) {
    if (url == null) {
      return null;
    }
    try (InputStream in = url.openStream()) {
      return url;
    } catch (IOException e) {
      return null;
    }
  }

  private List<LibraryCandidate> findLibraryCandidates(String artifactName,
      String libraryNameAndVersion, Class<?> providerClass) {
    String mappedName = System.mapLibraryName(libraryNameAndVersion);

    String[] prefixes = mappedName.startsWith("lib") ? new String[] {""} : new String[] {"", "lib"};

    List<LibraryCandidate> list = new ArrayList<>();
    for (String archOs : ARCHITECTURE_AND_OS) {
      for (String compiler : new String[] {"clang", "gcc"
          // "gpp", "g++", "linker", "clang", "gcc", "cc", "CC", "icpc", "icc", "xlC", "xlC_r",
          // "msvc",
          // "icl", "ecpc", "ecc"
      }) {
        for (String prefix : prefixes) {
          String path = "/lib/" + archOs + "-" + compiler + "/jni/" + prefix + mappedName;

          URL url;

          url = validateResourceURL(providerClass.getResource(path));
          if (url != null) {
            list.add(new ClasspathLibraryCandidate(artifactName, libraryNameAndVersion, path, url));
          }

          // NOTE: we have to try .nodeps version _after_ trying the properly linked one.
          // While the former may throw an UnsatisfiedLinkError, this one may just terminate the VM
          // with a "symbol lookup error"
          String nodepsPath = nodepsPath(path);
          if (nodepsPath != null) {
            url = validateResourceURL(providerClass.getResource(nodepsPath));
            if (url != null) {
              list.add(new ClasspathLibraryCandidate(artifactName, libraryNameAndVersion,
                  nodepsPath, url));
            }
          }
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

  @Override
  public void close() {
  }
}
