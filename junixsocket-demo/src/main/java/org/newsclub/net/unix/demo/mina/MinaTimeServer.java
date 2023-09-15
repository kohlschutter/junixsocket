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
package org.newsclub.net.unix.demo.mina;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.newsclub.net.unix.AFUNIXSelectorProvider;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * Apache Mina-based Time server, modified to use junixsocket.
 *
 * Based on example code from
 * <a href="https://mina.apache.org/mina-project/userguide/ch2-basics/ch2.2-sample-tcp-server.html">
 * Apache Mina user guide, chapter 2.2 — Sample TCP Server</a>
 */
public class MinaTimeServer {
  public static void main(String[] args) throws IOException {
    int processorCount = Runtime.getRuntime().availableProcessors() + 1;

    // IoAcceptor acceptor = new NioSocketAcceptor(processorCount); // from original example code
    IoAcceptor acceptor = new NioSocketAcceptor(processorCount, AFUNIXSelectorProvider.provider());
    // IoAcceptor acceptor = new NioSocketAcceptor(processorCount,
    // AFTIPCSelectorProvider.provider());

    acceptor.getFilterChain().addLast("logger", new LoggingFilter());
    acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new TextLineCodecFactory(
        StandardCharsets.UTF_8)));
    acceptor.setHandler(new TimeServerHandler());
    acceptor.getSessionConfig().setReadBufferSize(2048);
    acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);

    // acceptor.bind( new InetSocketAddress(PORT) ); // from original example code
    acceptor.bind(AFUNIXSocketAddress.of(new File("/tmp/minatime")));
    // acceptor.bind(AFTIPCSocketAddress.ofService(Scope.SCOPE_CLUSTER, 128, 1));
  }
}