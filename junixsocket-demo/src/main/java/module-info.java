@SuppressWarnings("module")
module org.newsclub.net.unix.demo {
  requires org.newsclub.net.unix;
  requires org.newsclub.net.unix.server;
  requires org.newsclub.net.mysql;
  requires java.rmi;
  requires java.sql;
  requires transitive org.newsclub.net.unix.rmi;
  requires nanohttpd;

  exports org.newsclub.net.unix.demo.rmi.services to java.rmi;
  exports org.newsclub.net.unix.demo.rmi.fd to java.rmi;
}
