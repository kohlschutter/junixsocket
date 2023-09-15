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
package org.newsclub.net.unix.demo.netty;

import java.io.File;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;

import org.newsclub.net.unix.AFSocketAddress;
import org.newsclub.net.unix.AFUNIXSelectorProvider;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Echos any incoming data.
 * <p>
 * Based on example code from <a href="https://netty.io/wiki/user-guide-for-4.x.html">Netty user
 * guide for 4.x</a>
 */
public class EchoServer {
  private final AFSocketAddress addr;

  public EchoServer(AFSocketAddress addr) {
    this.addr = addr;
  }

  public void run() throws Exception {
    SelectorProvider provider = AFUNIXSelectorProvider.provider();
    // SelectorProvider provider = AFTIPCSelectorProvider.provider();

    // We need to specify our custom selector provider here (1), as well as in (3)
    EventLoopGroup bossGroup = new NioEventLoopGroup(0, (Executor) null, provider); // (1)
    EventLoopGroup workerGroup = new NioEventLoopGroup(0, (Executor) null, provider); // (1)
    try {
      ServerBootstrap b = new ServerBootstrap(); // (2)
      b.group(bossGroup, workerGroup) //
          .channelFactory(() -> new NioServerSocketChannel(provider)) // (3)
          .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
              ch.pipeline().addLast(new EchoServerHandler());
            }
          }) //
          .option(ChannelOption.SO_BACKLOG, 128) // (5)
          .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

      // Bind and start to accept incoming connections.
      ChannelFuture f = b.bind(addr).sync(); // (7)

      // Wait until the server socket is closed.
      // In this example, this does not happen, but you can do that to gracefully
      // shut down your server.
      f.channel().closeFuture().sync();
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }
  }

  public static void main(String[] args) throws Exception {
    File path = new File("/tmp/nettyecho");
    if (args.length > 0) {
      path = new File(args[0]);
    }

    new EchoServer(AFUNIXSocketAddress.of(path)).run();
    // new EchoServer(AFTIPCSocketAddress.ofService(Scope.SCOPE_CLUSTER, 128, 3)).run();
  }
}