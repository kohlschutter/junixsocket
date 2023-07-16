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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Git properties that are populated upon build time.
 *
 * Also see {@code src/main/unresolved-java/.../GitProperties.java}
 * 
 * @author Christian Kohlschütter
 */
final class GitProperties {
  private static final Map<String, String> MAP = new LinkedHashMap<>();

  static {
    MAP.put("git.build.version", "${git.build.version}"); // junixsocket version
    MAP.put("git.commit.id.abbrev", "${git.commit.id.abbrev}");
    MAP.put("git.commit.id.describe", "${git.commit.id.abbrev}");
    MAP.put("git.commit.id.full", "${git.commit.id.full}");
    MAP.put("git.commit.time", "${git.commit.time}");
    MAP.put("git.dirty", "${git.dirty}");
  }

  private GitProperties() {
    throw new IllegalStateException("No instances");
  }

  static Map<String, String> getGitProperties() {
    return Collections.unmodifiableMap(MAP);
  }
}
