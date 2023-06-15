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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.NoRouteToHostException;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFSocketFactory;
import org.newsclub.net.unix.AFTIPCSocketAddress;
import org.newsclub.net.unix.demo.DemoHelper;
import org.newsclub.net.unix.demo.nanohttpd.NanoHttpdServerDemo;

import com.kohlschutter.util.IOUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Connects to TIPC service 8080.1 and performs an HTTP request over that socket, using the
 * <a href="https://square.github.io/okhttp/">OkHttp</a> HTTP client library.
 *
 * If that socket is bound by {@link NanoHttpdServerDemo}, the expected output is "Hello world from
 * &lt;hostname&gt;" (start {@link NanoHttpdServerDemo} with {@code --url tipc://8080.1}.
 *
 * @author Christian Kohlschütter
 * @see NanoHttpdServerDemo
 */
public class OkHttpClientTIPCDemo {
  public static void main(String[] args) throws IOException, InterruptedException {

    SocketAddress addr = DemoHelper.parseAddress(args, //
        AFTIPCSocketAddress.ofService(8080, 1));

    OkHttpClient client = new OkHttpClient.Builder() //
        .socketFactory(new AFSocketFactory.FixedAddressSocketFactory(addr)) //
        // .callTimeout(Duration.ofMinutes(1)) //
        .retryOnConnectionFailure(true) //
        .connectTimeout(500, TimeUnit.MILLISECONDS) //
        .readTimeout(500, TimeUnit.MILLISECONDS) //
        .callTimeout(500, TimeUnit.MILLISECONDS) //
        .build();

    // We keep looping so you can try adding/removing/disconnecting TIPC nodes and see how
    // beautifully TIPC handles these situations
    while (!Thread.interrupted()) {
      Request request = new Request.Builder().url("http://localhost/").build();
      try (Response response = client.newCall(request).execute()) {

        // NOTE: Spotbugs can't make its mind up:
        // If we use a try-with-resources statement here, it either
        // returns NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE
        // or RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE
        @SuppressWarnings("resource")
        ResponseBody body = response.body();
        if (body != null) {
          try (InputStream in = body.byteStream()) { // NOPMD.UseTryWithResources
            IOUtil.transferAllBytes(in, System.out);
          } finally {
            body.close();
          }
        }
      } catch (InterruptedIOException | NoRouteToHostException e) {
        e.printStackTrace();
      }

      // Instead of reusing the same connection, connect to any node that exposes the TIPC service
      client.connectionPool().evictAll();

      // sleep for convenience
      Thread.sleep(100);
    }
  }
}
