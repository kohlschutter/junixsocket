/*
 * junixsocket
 *
 * Copyright 2009-2026 Christian Kohlschütter
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Random;

import com.kohlschutter.annotations.compiletime.SuppressFBWarnings;

abstract class TempFileUtil {
  static final TempFileUtil INSTANCE = newInstance();

  private TempFileUtil() {
  }

  private static TempFileUtil newInstance() {
    if ("Astral".equals(System.getProperty("os.name", ""))) {
      return new MaxLengthInstance(14);
    }
    return new StandardInstance();
  }

  public static TempFileUtil getInstance() {
    return INSTANCE;
  }

  public abstract Path newPathForUnixDomainSocket(boolean delete) throws IOException;

  private static final class StandardInstance extends TempFileUtil {
    @Override
    public Path newPathForUnixDomainSocket(boolean delete) throws IOException {
      Path p = Files.createTempFile("jux", ".sock");
      if (!Files.deleteIfExists(p) && Files.exists(p)) {
        throw new IOException("Could not delete temporary file that we just created: " + p);
      }
      return p;
    }
  }

  @SuppressFBWarnings("PREDICTABLE_RANDOM")
  private static final class MaxLengthInstance extends TempFileUtil {
    private final int maxLength;
    private final Random random = new Random();
    private final byte[] bytesPrefix = "/tmp/".getBytes(StandardCharsets.US_ASCII);
    private final byte[] bytesSuffix = ".j".getBytes(StandardCharsets.US_ASCII);
    private final byte[] validCharacters = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', //
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', //
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', //
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', //
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', //
        '_', '-', //
    };

    public MaxLengthInstance(int maxLength) {
      super();
      this.maxLength = maxLength;
    }

    @Override
    public Path newPathForUnixDomainSocket(boolean delete) throws IOException {
      Path p = null;

      while (!Thread.interrupted()) {
        p = randomPath();
        if (Files.exists(p)) {
          continue;
        }
        try {
          Files.createFile(p);
          break;
        } catch (FileAlreadyExistsException e) {
          continue;
        }
      }
      Objects.requireNonNull(p);
      if (delete && !Files.deleteIfExists(p) && Files.exists(p)) {
        throw new IOException("Could not delete temporary file that we just created: " + p);
      }
      return p;
    }

    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    private Path randomPath() {
      byte[] bytes = new byte[maxLength];
      random.nextBytes(bytes);
      System.arraycopy(bytesPrefix, 0, bytes, 0, bytesPrefix.length);
      System.arraycopy(bytesSuffix, 0, bytes, bytes.length - bytesSuffix.length,
          bytesSuffix.length);

      for (int i = bytesPrefix.length, n = bytes.length - bytesSuffix.length; i < n; i++) {
        bytes[i] = validCharacters[Math.abs(bytes[i]) % validCharacters.length];
      }
      return Paths.get(new String(bytes, StandardCharsets.US_ASCII));
    }
  }
}
