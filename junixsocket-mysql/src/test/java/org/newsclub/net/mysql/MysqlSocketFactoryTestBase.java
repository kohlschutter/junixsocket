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
package org.newsclub.net.mysql;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import com.kohlschutter.testutil.TestAbortedWithImportantMessageException;
import com.kohlschutter.testutil.TestAbortedWithImportantMessageException.MessageType;
import com.mysql.cj.jdbc.exceptions.CommunicationsException;

abstract class MysqlSocketFactoryTestBase {

  @Test
  public void testDriverManagerConnectionToMissingServer() throws Exception {
    AFUNIXSocketAddress addr = AFUNIXSocketAddress.ofNewTempFile();
    try (Connection unused = DriverManager.getConnection("jdbc:mysql://localhost/db?socketFactory="
        + socketFactory() + "&junixsocket.file=" + addr.getPath())) {
      fail(
          "Should have thrown an exception since we're trying to connect to a non-existing database server");
    } catch (CommunicationsException e) {
      // expected
    }
  }

  @Test
  public void testDriverManagerConnectionToMysqlSock() throws Exception {
    MysqlCredentials creds = MysqlCredentials.getCredentials();

    AFUNIXSocketAddress addr = AFUNIXSocketAddress.of(new File("/tmp/mysql.sock"));
    boolean connected = false;
    try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/mysql?socketFactory="
        + socketFactory() + "&junixsocket.file=" + addr.getPath(), creds.getUser(), creds
            .getPassword())) {
      connected = true;

      try (PreparedStatement pstmt = conn.prepareStatement("SHOW TABLES");
          ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
          assertNotNull(rs.getString(1));
        }
      }
    } catch (SQLException e) {
      if (connected) {
        throw e;
      } else {
        throw new TestAbortedWithImportantMessageException(MessageType.TEST_ABORTED_WITH_ISSUES,
            "Could not connect to MySQL database for testing. Please correct the following definitions: "
                + creds.toString(), e);
      }
    }
  }

  protected abstract String socketFactory();
}
