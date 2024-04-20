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

import com.kohlschutter.testutil.TestAbortedNotAnIssueException;

final class MysqlCredentials {
  private final String mysqlSock;
  private final String user;
  private final String password;
  private final String database;

  private MysqlCredentials(String mysqlSock, String user, String password, String database) {
    this.mysqlSock = mysqlSock;
    this.user = user;
    this.password = password;
    this.database = database;
  }

  public static MysqlCredentials getCredentials() throws TestAbortedNotAnIssueException {
    String mysqlSock = System.getProperty("selftest.mysql.sock", "");
    if (mysqlSock.isEmpty()) {
      throw new TestAbortedNotAnIssueException(
          "Specify -Dselftest.mysql.sock=/tmp/mysql.sock or similar to enable test");
    }
    String user = System.getProperty("selftest.mysql.user", "root");
    String password = System.getProperty("selftest.mysql.password", "");
    String database = System.getProperty("selftest.mysql.database", "mysql");

    return new MysqlCredentials(mysqlSock, user, password, database);
  }

  public String getMysqlSock() {
    return mysqlSock;
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public String getDatabase() {
    return database;
  }

  @Override
  public String toString() {
    return "-Dselftest.mysql.sock=" + mysqlSock + " -Dselftest.mysql.user=" + user
        + " -Dselftest.mysql.password=" + password + " -Dselftest.mysql.database=" + database;
  }
}
