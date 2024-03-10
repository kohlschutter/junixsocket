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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.function.Function;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * An {@link SSLContext} wrapper that applies settings specified with {@link SSLContextBuilder}.
 *
 * @author Christian Kohlschütter
 */
final class BuilderSSLContext extends SSLContext {
  BuilderSSLContext(boolean clientMode, SSLContext context,
      Function<SSLParameters, SSLParameters> parametersFunction, SocketFactory socketFactory) {
    super(new ConfiguredSSLContextSpi(clientMode, context, parametersFunction, socketFactory),
        context.getProvider(), context.getProtocol());
  }

  /**
   * Calls {@link SSLContext#init(KeyManager[], TrustManager[], SecureRandom)} with the given
   * parameters. If there was a known problem with initializing the default {@link SecureRandom}
   * (when {@code null} was specified), try {@link SecureRandom#getInstanceStrong()} instance.
   *
   * @param context The context to initialize.
   * @param km The key managers, or {@code null}.
   * @param tm The trust managers, or {@code null}.
   * @param sr The secure random, or {@code null}.
   * @throws KeyManagementException on error.
   */
  static void initContext(SSLContext context, KeyManager[] km, TrustManager[] tm, SecureRandom sr)
      throws KeyManagementException {
    try {
      context.init(km, tm, sr);
    } catch (IllegalStateException e) {
      // In certain configurations, BouncyCastle may fail to detect a SecureRandom named "DEFAULT"
      if (sr == null && e.getMessage().contains("SecureRandom not available")) {
        try {
          // Use any strong SecureRandom instance as fallback
          sr = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e1) {
          e.addSuppressed(e1);
          throw e;
        }
        context.init(km, tm, sr);
      } else {
        throw e;
      }
    }
  }

  private static final class ConfiguredSSLContextSpi extends SSLContextSpi {
    private final SSLSocketFactory socketFactory;
    private final SSLServerSocketFactory serverSocketFactory;

    private final SSLContext context;
    private final SSLParameters params;
    private final boolean clientMode;

    private ConfiguredSSLContextSpi(boolean clientMode, SSLContext context,
        Function<SSLParameters, SSLParameters> parametersFunction, SocketFactory socketFactory) {
      super();
      this.clientMode = clientMode;
      this.context = context;

      SSLParameters p = context.getDefaultSSLParameters();
      if (parametersFunction != null) {
        p = parametersFunction.apply(p);
      }

      this.params = p;
      this.socketFactory = new BuilderSSLSocketFactory(clientMode, context, context
          .getSocketFactory(), p, socketFactory);
      this.serverSocketFactory = new BuilderSSLServerSocketFactory(context.getServerSocketFactory(),
          p);
    }

    @Override
    protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
        throws KeyManagementException {
      initContext(context, km, tm, sr);
    }

    @Override
    protected SSLSocketFactory engineGetSocketFactory() {
      return socketFactory;
    }

    @Override
    protected SSLServerSocketFactory engineGetServerSocketFactory() {
      return serverSocketFactory;
    }

    @Override
    protected SSLEngine engineCreateSSLEngine() {
      return init(context.createSSLEngine());
    }

    @Override
    protected SSLEngine engineCreateSSLEngine(String host, int port) {
      return init(context.createSSLEngine(host, port));
    }

    @Override
    protected SSLSessionContext engineGetServerSessionContext() {
      return context.getServerSessionContext();
    }

    @Override
    protected SSLSessionContext engineGetClientSessionContext() {
      return context.getClientSessionContext();
    }

    private SSLEngine init(SSLEngine engine) {
      engine.setEnabledProtocols(params.getProtocols());
      engine.setEnabledCipherSuites(params.getCipherSuites());
      engine.setUseClientMode(clientMode);

      return engine;
    }
  }
}
