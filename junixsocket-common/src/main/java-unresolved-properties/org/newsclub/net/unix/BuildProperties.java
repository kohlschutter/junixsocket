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
 * Properties that are populated upon build time.
 *
 * Also see {@code src/main/unresolved-java/.../BuildProperties.java}
 * 
 * @author Christian Kohlschütter
 */
final class BuildProperties {
  private static final Map<String, String> MAP;

  static {
    Map<String, String> map = new LinkedHashMap<>();

    map.put("project.version", "${project.version}");
    map.put("git.build.version", "${git.build.version}"); // junixsocket version
    map.put("git.commit.id.abbrev", "${git.commit.id.abbrev}");
    map.put("git.commit.id.describe", "${git.commit.id.abbrev}");
    map.put("git.commit.id.full", "${git.commit.id.full}");
    map.put("git.commit.time", "${git.commit.time}");
    map.put("git.dirty", "${git.dirty}");

    MAP = Collections.unmodifiableMap(map);
  }

  private BuildProperties() {
    throw new IllegalStateException("No instances");
  }

  static Map<String, String> getBuildProperties() {
    return MAP;
  }
}
