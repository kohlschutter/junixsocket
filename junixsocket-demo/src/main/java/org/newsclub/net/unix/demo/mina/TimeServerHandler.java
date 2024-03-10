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
package org.newsclub.net.unix.demo.mina;

import java.util.Date;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

/**
 * Time server handler.
 *
 * Based on example code from
 * <a href="https://mina.apache.org/mina-project/userguide/ch2-basics/ch2.2-sample-tcp-server.html">
 * Apache Mina user guide, chapter 2.2 — Sample TCP Server</a>
 */
@SuppressWarnings("JavaUtilDate" /* errorprone */)
class TimeServerHandler extends IoHandlerAdapter {
  @Override
  public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
    cause.printStackTrace();
  }

  @Override
  public void messageReceived(IoSession session, Object message) throws Exception {
    String str = message.toString();
    if ("quit".equalsIgnoreCase(str.trim())) {
      session.closeNow();
      return;
    }
    Date date = new Date();
    session.write(date.toString());
    System.out.println("Message written...");
  }

  @Override
  public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
    System.out.println("IDLE " + session.getIdleCount(status));
  }
}
