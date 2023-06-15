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
package org.newsclub.net.unix.demo.okhttp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.time.Duration;

import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.demo.DemoHelper;
import org.newsclub.net.unix.demo.nanohttpd.NanoHttpdServerDemo;

import com.kohlschutter.util.IOUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Connects to {@code /tmp/junixsocket-http-server.sock} and performs an http request over that
 * socket, using the <a href="https://square.github.io/okhttp/">OkHttp</a> HTTP client library.
 *
 * If that socket is bound by {@link NanoHttpdServerDemo}, the expected output is "Hello world".
 *
 * @author Christian Kohlschütter
 * @see NanoHttpdServerDemo
 */
public class OkHttpClientDemo {
  public static void main(String[] args) throws IOException {
    SocketAddress addr = DemoHelper.parseAddress(args, //
        AFUNIXSocketAddress.of(new File("/tmp/junixsocket-http-server.sock")));

    OkHttpClient.Builder builder = new OkHttpClient.Builder() //
        .socketFactory(new AFSocketFactory.FixedAddressSocketFactory(addr)) //
        .callTimeout(Duration.ofMinutes(1));

    OkHttpClient client = builder.build();

    Request request = new Request.Builder().url("http://localhost/").build();
    try (Response response = client.newCall(request).execute()) {

      @SuppressWarnings("resource")
      ResponseBody body = response.body();
      if (body != null) {
        try (InputStream in = body.byteStream()) { // NOPMD.UseTryWithResources
          IOUtil.transferAllBytes(in, System.out);
        } finally {
          body.close();
        }
      }
    }
  }
}
