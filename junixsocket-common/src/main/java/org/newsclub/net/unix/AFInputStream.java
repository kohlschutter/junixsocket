/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

import java.io.InputStream;

/**
 * An {@link InputStream} for {@link AFSocket}, etc.
 *
 * @author Christian Kohlschütter
 */
public abstract class AFInputStream extends InputStream implements FileDescriptorAccess {
  AFInputStream() {
    super();
  }

  // IMPORTANT! also see src/main/java8/org/newsclub/net/unix/AFInputStream shim
}
