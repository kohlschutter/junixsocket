/**
 * junixsocket
 *
 * Copyright (c) 2009-2018 Christian Kohlschütter
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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

final class NativeLibraryLoader implements Closeable {
  private static final String PROP_LIBRARY_OVERRIDE = "org.newsclub.net.unix.library.override";

  private static final String architectureAndOS = architectureAndOS();

  private String libraryName = "junixsocket-native";
  private String version;
  private String libraryNameAndVersion;
  private Class<?> providerClass;
  private String artifactName;

  private InputStream libraryIn;

  public NativeLibraryLoader() {
  }

  private void findLibraryArtifact() {
    try {
      if (!tryProviderClass("org.newsclub.lib.junixsocket.common.NarMetadata",
          "junixsocket-native-common") && //
          !tryProviderClass("org.newsclub.lib.junixsocket.custom.NarMetadata",
              "junixsocket-native-custom") //
      ) {

        String cp = System.getProperty("java.class.path", "");
        if (cp.contains("junixsocket-native-custom/target-eclipse") || cp.contains(
            "junixsocket-native-common/target-eclipse")) {
          throw new UnsatisfiedLinkError("Could not load native library.\n\n*** ECLIPSE USERS ***\n"
              + "If you're running from within Eclipse, please close the \"junixsocket-native-*\" projects\n");
        }

        if (artifactName != null) {
          throw new UnsatisfiedLinkError("Artifact " + artifactName
              + " does not contain library for " + architectureAndOS);
        } else {
          throw new ClassNotFoundException(
              "You need to add a dependency to either junixsocket-native-common or junixsocket-native-custom");
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean tryProviderClass(String providerClassname, String artifactCandidate)
      throws IOException {
    try {
      this.providerClass = Class.forName(providerClassname);
    } catch (ClassNotFoundException e) {
      return false;
    }
    this.artifactName = artifactCandidate;
    Properties p = new Properties();
    try (InputStream in = providerClass.getResourceAsStream(
        "/META-INF/maven/com.kohlschutter.junixsocket/" + artifactCandidate + "/pom.properties")) {
      p.load(in);
      this.version = p.getProperty("version");
      if (version == null) {
        throw new NullPointerException("Could not read version from pom.properties");
      }
      this.libraryNameAndVersion = libraryName + "-" + version;
    }

    this.libraryIn = findLibrary();
    if (libraryIn != null) {
      return true;
    } else {
      return false;
    }
  }

  public void loadLibrary() {
    String libraryOverride = System.getProperty(PROP_LIBRARY_OVERRIDE, "");
    if (!libraryOverride.isEmpty()) {
      System.loadLibrary(libraryOverride);
      return;
    }

    findLibraryArtifact();
    try {
      System.loadLibrary(libraryNameAndVersion);
      return;
    } catch (Exception | UnsatisfiedLinkError e) {
      if (libraryIn == null) {
        throw e;
      } else {
        // ignore
      }
    }

    try {
      File libFile = File.createTempFile("libtmp", System.mapLibraryName(libraryNameAndVersion));
      try (OutputStream out = new FileOutputStream(libFile)) {
        byte[] buf = new byte[4096];
        int read;
        while ((read = libraryIn.read(buf)) >= 0) {
          out.write(buf, 0, read);
        }
      }
      System.load(libFile.getAbsolutePath());
      if (!libFile.delete()) {
        libFile.deleteOnExit();
      }
      close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String architectureAndOS() {
    return System.getProperty("os.arch") + "-" + System.getProperty("os.name").replaceAll(" ", "");
  }

  private InputStream findLibrary() {
    String mappedName = System.mapLibraryName(libraryNameAndVersion);

    for (String compiler : new String[] {
        "gpp", "g++", "linker", "gcc", "cc", "CC", "icpc", "icc", "xlC", "xlC_r", "msvc", "icl",
        "ecpc", "ecc"}) {
      String path = "/lib/" + architectureAndOS + "-" + compiler + "/jni/" + mappedName;

      InputStream in = providerClass.getResourceAsStream(path);
      if (in != null) {
        return in;
      }
    }
    return null;
  }

  @Override
  public synchronized void close() {
    if (libraryIn != null) {
      try {
        libraryIn.close();
      } catch (IOException e) {
        // ignore
      }
      libraryIn = null;
    }
  }
}
