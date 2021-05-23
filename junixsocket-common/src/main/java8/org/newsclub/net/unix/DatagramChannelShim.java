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
  public <T> DatagramChannel setOption(SocketOption<T> name, T value) throws IOException {
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId != null) {
      socket.getAFImpl().setOption(optionId.intValue(), value);
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T getOption(SocketOption<T> name) throws IOException {
    Integer optionId = SocketOptionsMapper.resolve(name);
    if (optionId == null) {
      return null;
    } else {
      return (T) socket.getAFImpl().getOption(optionId.intValue());
    }
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return SocketOptionsMapper.SUPPORTED_SOCKET_OPTIONS;
  }
}
