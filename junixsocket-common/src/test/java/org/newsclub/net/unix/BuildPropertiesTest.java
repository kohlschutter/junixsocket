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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.kohlschutter.util.ExecutionEnvironmentUtil;

public class BuildPropertiesTest {
  @Test
  public void testNotEmpty() throws Exception {
    Map<String, String> properties = BuildProperties.getBuildProperties();
    assertNotEquals(0, properties.size());
  }

  @Test
  public void testHasProperties() throws Exception {
    Map<String, String> properties = BuildProperties.getBuildProperties();
    assertTrue(properties.containsKey("project.version"));
    assertTrue(properties.containsKey("git.commit.id.full"));
  }

  @Test
  public void testResolved() throws Exception {
    Map<String, String> properties = BuildProperties.getBuildProperties();

    String projectVersion = properties.get("project.version");
    if (ExecutionEnvironmentUtil.isInEclipse()) {
      assertEquals("${project.version}", projectVersion);
    } else {
      assertNotEquals("", projectVersion);
      assertNotEquals("${project.version}", projectVersion);
    }
  }
}
