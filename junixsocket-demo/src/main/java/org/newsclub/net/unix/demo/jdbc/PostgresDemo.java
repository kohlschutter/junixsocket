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
package org.newsclub.net.unix.demo.jdbc;

import java.io.IOException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.newsclub.net.unix.AFUNIXSocketFactory;
import org.newsclub.net.unix.demo.DemoHelper;

/**
 * Demonstrates how to connect to a local PostgreSQL server via unix sockets.
 *
 * @author Christian Kohlschuetter
 * @see AFUNIXSocketFactory
 */
public final class PostgresDemo {
  public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
    DemoHelper.initJDBCDriverClass("postgresqlDriver", "org.postgresql.Driver", null);

    String databaseName = "postgres";

    int port = 12345; // the port number doesn't matter
    String socketPath = DemoHelper.getPropertyValue("socketPath", "/tmp/.s.PGSQL.5432",
        "/var/run/postgresql/.s.PGSQL.5432");

    // Just try to connect to the socket using a file:// URI (this is not necessary, just for demo)
    new AFUNIXSocketFactory.URIScheme().createSocket("file://" + socketPath, port).close();

    String urlEncoded = URLEncoder.encode("file://" + socketPath, "UTF-8");
    System.out.println("urlEncoded: " + urlEncoded);
    System.out.println();

    String socketFactory = DemoHelper.getPropertyValue(//
        "socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg", //
        "org.newsclub.net.unix.AFUNIXSocketFactory$URIScheme", (String s) -> {
          if (!s.isEmpty() && !s.contains(".")) {
            // NOTE: for demo purposes, you can simply specify URIScheme, FactoryArg, etc.
            return "org.newsclub.net.unix.AFUNIXSocketFactory$" + s;
          } else {
            return s;
          }
        });

    Properties props = new Properties();
    props.setProperty("socketFactory", socketFactory);

    DemoHelper.addProperty(props, "user", System.getProperty("user.name"), "postgresqlUser",
        "root");
    DemoHelper.addProperty(props, "password", "", "postgresqlPassword", "secret");
    DemoHelper.addProperty(props, "sslMode", "disable", "postgresqlSslMode", "prefer");

    String url;
    if ("org.newsclub.net.unix.AFUNIXSocketFactory$URIScheme".equals(socketFactory)) {
      url = "jdbc:postgresql://[" + urlEncoded + ":" + port + "/" + databaseName;
    } else {
      url = "jdbc:postgresql://localhost/postgres";
      if ("org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg".equals(socketFactory)) {
        DemoHelper.addProperty(props, "socketFactoryArg", socketPath, "socketFactoryArg", null);
      }
    }

    System.out.println();
    System.out.println("Connecting to db: " + url);

    // System.setProperty("org.newsclub.net.unix.socket.default", "/tmp/.s.PGSQL.5432");
    // props.setProperty("socketFactory",
    // "org.newsclub.net.unix.AFUNIXSocketFactory$SystemProperty");

    System.out.println();

    try (Connection conn = DriverManager.getConnection(url, props)) {
      System.out.println("Connection " + conn);

      DatabaseMetaData metadata = conn.getMetaData();
      System.out.println("Database version: " + metadata.getDatabaseProductName() + " " + metadata
          .getDatabaseProductVersion());
      String sql = "SHOW ALL";
      System.out.println(sql);
      try (Statement stmt = conn.createStatement(); //
          ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          System.out.println("* " + rs.getString(1) + "=" + rs.getString(2));
        }
      }
    }
  }
}
