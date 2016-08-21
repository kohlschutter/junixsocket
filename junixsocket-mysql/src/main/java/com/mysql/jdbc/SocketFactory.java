package com.mysql.jdbc;

// JUST TO KEEP THE COMPILER HAPPY
// NOT TO BE INCLUDED IN FINAL PRODUCT BINARY
public interface SocketFactory {
  // // Method descriptor #4 ()Ljava/net/Socket;
  java.net.Socket afterHandshake() throws
          java.io.IOException;

  // Method descriptor #4 ()Ljava/net/Socket;
  java.net.Socket beforeHandshake() throws
          java.io.IOException;

  // Method descriptor #10 (Ljava/lang/String;ILjava/util/Properties;)Ljava/net/Socket;
  java.net.Socket connect(java.lang.String arg0, int arg1, java.util.Properties arg2)
      throws java.io.IOException;
}
