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
package org.newsclub.net.unix.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import com.kohlschutter.annotations.compiletime.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Wraps another {@link SSLSocket}.
 *
 * @author Christian Kohlschütter
 */
@ExcludeFromCodeCoverageGeneratedReport
@SuppressWarnings("PMD.ExcessivePublicCount")
class FilterSSLSocket extends SSLSocket {
  private final SSLSocket wrapped;

  protected FilterSSLSocket(SSLSocket wrapped) {
    super();
    this.wrapped = wrapped;
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return wrapped.getSupportedCipherSuites();
  }

  @Override
  public String[] getEnabledCipherSuites() {
    return wrapped.getEnabledCipherSuites();
  }

  @Override
  public void setEnabledCipherSuites(String[] suites) {
    wrapped.setEnabledCipherSuites(suites);
  }

  @Override
  public String[] getSupportedProtocols() {
    return wrapped.getSupportedProtocols();
  }

  @Override
  public String[] getEnabledProtocols() {
    return wrapped.getEnabledProtocols();
  }

  @Override
  public void setEnabledProtocols(String[] protocols) {
    wrapped.setEnabledProtocols(protocols);
  }

  @Override
  public SSLSession getSession() {
    return wrapped.getSession();
  }

  @Override
  public SSLSession getHandshakeSession() {
    return wrapped.getHandshakeSession();
  }

  @Override
  public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
    wrapped.addHandshakeCompletedListener(listener);
  }

  @Override
  public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
    wrapped.removeHandshakeCompletedListener(listener);
  }

  @Override
  public void startHandshake() throws IOException {
    wrapped.startHandshake();
  }

  @Override
  public void setUseClientMode(boolean mode) {
    wrapped.setUseClientMode(mode);
  }

  @Override
  public boolean getUseClientMode() {
    return wrapped.getUseClientMode();
  }

  @Override
  public void setNeedClientAuth(boolean need) {
    wrapped.setNeedClientAuth(need);
  }

  @Override
  public boolean getNeedClientAuth() {
    return wrapped.getNeedClientAuth();
  }

  @Override
  public void setWantClientAuth(boolean want) {
    wrapped.setWantClientAuth(want);
  }

  @Override
  public boolean getWantClientAuth() {
    return wrapped.getWantClientAuth();
  }

  @Override
  public void setEnableSessionCreation(boolean flag) {
    wrapped.setEnableSessionCreation(flag);
  }

  @Override
  public boolean getEnableSessionCreation() {
    return wrapped.getEnableSessionCreation();
  }

  @Override
  public SSLParameters getSSLParameters() {
    return wrapped.getSSLParameters();
  }

  @Override
  public void setSSLParameters(SSLParameters params) {
    wrapped.setSSLParameters(params);
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    wrapped.connect(endpoint);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    wrapped.connect(endpoint, timeout);
  }

  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    wrapped.bind(bindpoint);
  }

  @Override
  public InetAddress getInetAddress() {
    return wrapped.getInetAddress();
  }

  @Override
  public InetAddress getLocalAddress() {
    return wrapped.getLocalAddress();
  }

  @Override
  public int getPort() {
    return wrapped.getPort();
  }

  @Override
  public int getLocalPort() {
    return wrapped.getLocalPort();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return wrapped.getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return wrapped.getLocalSocketAddress();
  }

  @Override
  public SocketChannel getChannel() {
    return wrapped.getChannel();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return wrapped.getInputStream();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return wrapped.getOutputStream();
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    wrapped.setTcpNoDelay(on);
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return wrapped.getTcpNoDelay();
  }

  @Override
  public void setSoLinger(boolean on, int linger) throws SocketException {
    wrapped.setSoLinger(on, linger);
  }

  @Override
  public int getSoLinger() throws SocketException {
    return wrapped.getSoLinger();
  }

  @Override
  public void sendUrgentData(int data) throws IOException {
    wrapped.sendUrgentData(data);
  }

  @Override
  public void setOOBInline(boolean on) throws SocketException {
    wrapped.setOOBInline(on);
  }

  @Override
  public boolean getOOBInline() throws SocketException {
    return wrapped.getOOBInline();
  }

  @Override
  public synchronized void setSoTimeout(int timeout) throws SocketException {
    wrapped.setSoTimeout(timeout);
  }

  @Override
  public synchronized int getSoTimeout() throws SocketException {
    return wrapped.getSoTimeout();
  }

  @Override
  public synchronized void setSendBufferSize(int size) throws SocketException {
    wrapped.setSendBufferSize(size);
  }

  @Override
  public synchronized int getSendBufferSize() throws SocketException {
    return wrapped.getSendBufferSize();
  }

  @Override
  public synchronized void setReceiveBufferSize(int size) throws SocketException {
    wrapped.setReceiveBufferSize(size);
  }

  @Override
  public synchronized int getReceiveBufferSize() throws SocketException {
    return wrapped.getReceiveBufferSize();
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    wrapped.setKeepAlive(on);
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return wrapped.getKeepAlive();
  }

  @Override
  public void setTrafficClass(int tc) throws SocketException {
    wrapped.setTrafficClass(tc);
  }

  @Override
  public int getTrafficClass() throws SocketException {
    return wrapped.getTrafficClass();
  }

  @Override
  public void setReuseAddress(boolean on) throws SocketException {
    wrapped.setReuseAddress(on);
  }

  @Override
  public boolean getReuseAddress() throws SocketException {
    return wrapped.getReuseAddress();
  }

  @Override
  public synchronized void close() throws IOException {
    wrapped.close();
  }

  @Override
  public void shutdownInput() throws IOException {
    wrapped.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    wrapped.shutdownOutput();
  }

  @Override
  public String toString() {
    return wrapped.toString();
  }

  @Override
  public boolean isConnected() {
    return wrapped.isConnected();
  }

  @Override
  public boolean isBound() {
    return wrapped.isBound();
  }

  @Override
  public boolean isClosed() {
    return wrapped.isClosed();
  }

  @Override
  public boolean isInputShutdown() {
    return wrapped.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return wrapped.isOutputShutdown();
  }

  @Override
  public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    wrapped.setPerformancePreferences(connectionTime, latency, bandwidth);
  }
}
