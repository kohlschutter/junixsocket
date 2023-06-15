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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * This test app responds with "Hello world" over a {@link Socket} that was passed from another
 * process via standard input.
 *
 * @author Christian Kohlschütter
 * @see org.newsclub.net.unix.domain.FileDescriptorCastTest#testForkedVMRedirectStdin()
 */
public class StdinSocketApp {
  public static void main(String[] args) throws IOException {
    Socket socket = FileDescriptorCast.using(FileDescriptor.in).as(Socket.class);
    System.out.println(socket);
    try (OutputStream out = socket.getOutputStream()) {
      out.write("Hello world\n".getBytes(StandardCharsets.UTF_8));
    }
  }
}
