package org.newsclub.net.unix;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.DatagramChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

abstract class DatagramChannelShim extends DatagramChannel {

  protected final AFUNIXDatagramSocket socket;

  protected DatagramChannelShim(SelectorProvider provider, AFUNIXDatagramSocket socket) {
    super(provider);
    this.socket = socket;
  }

  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
//    return socket.getOption(name);
    return null;
  }

  @SuppressWarnings("resource")
  @Override
  public <T> DatagramChannel setOption(SocketOption<T> name, T value) throws IOException {
//    socket.setOption(name, value);
    return this;
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
//    return socket.supportedOptions();
    return null;
  }
}
