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

/**
 * Provides some default settings.
 * 
 * At the moment, only a default library path is specified.
 * 
 * @author Christian Kohlschuetter
 */
final class NativeUnixSocketConfig {
    /**
     * The default path where the junixsocket native library is located.
     */
    // public static String LIBRARY_PATH = null;
    public static String LIBRARY_PATH = "/opt/newsclub/lib-native";
}
