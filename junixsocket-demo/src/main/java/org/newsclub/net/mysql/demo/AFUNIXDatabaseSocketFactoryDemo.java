/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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
package org.newsclub.net.mysql.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import org.newsclub.net.mysql.AFUNIXDatabaseSocketFactory;

/**
 * Demonstrates how to connect to a local MySQL server.
 * 
 * @author Christian Kohlschuetter
 */
public class AFUNIXDatabaseSocketFactoryDemo {
  @SuppressWarnings("resource")
  public static void main(String[] args) throws Exception {
    // specify -DdriverClass=com.mysql.jdbc.Driver to use the old JDBC driver
    String driverClass = System.getProperty("mysqlDriver", "");
    if (driverClass.isEmpty()) {
      System.out.println(
          "Using JDBC driver provided by SPI; override with -DmysqlDriver=com.mysql.jdbc.Driver");
    } else {
      System.out.println("Using JDBC driver provided by -DmysqlDriver=" + driverClass);
      Class.forName(driverClass);
    }
    System.out.println();

    // try -DmysqlUser=test -DmysqlPassword=test -DmysqlSocket=/var/lib/mysql.sock
    Properties props = new Properties();
    addProperty(props, "socketFactory", AFUNIXDatabaseSocketFactory.class.getName(), null);
    addProperty(props, "junixsocket.file", "/tmp/mysql.sock", "mysqlSocket");
    addProperty(props, "user", "root", "mysqlUser");
    addProperty(props, "password", "", "mysqlPassword");

    System.out.println();

    // SHOW DATABASES
    Connection conn = DriverManager.getConnection("jdbc:mysql://", props);
    System.out.println("Connection: " + conn);

    String sql = "SHOW DATABASES";
    System.out.println(sql);
    try (Statement stmt = conn.createStatement(); //
        ResultSet rs = stmt.executeQuery(sql)) {
      while (rs.next()) {
        System.out.println("* " + rs.getString(1));
      }
    }
  }

  private static void addProperty(Properties props, String key, String value, String property) {
    if (property == null) {
      System.out.println(key + "=" + value);
    } else {
      value = System.getProperty(property, value);
      System.out.println(key + "=" + value + "; override with -D" + property);
    }
    props.setProperty(key, value);
  }
}
