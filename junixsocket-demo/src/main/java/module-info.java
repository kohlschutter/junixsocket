/**
 * Some demo code.
 */
@SuppressWarnings("module") //
module org.newsclub.net.unix.demo {
  requires org.newsclub.net.unix;
  requires org.newsclub.net.unix.tipc;
  requires org.newsclub.net.unix.server;
  requires org.newsclub.net.mysql;
  requires transitive org.newsclub.net.unix.rmi;
  requires java.rmi;
  requires java.sql;
  requires nanohttpd;
  requires okhttp3;
  requires com.kohlschutter.util;
  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;

  exports org.newsclub.net.unix.demo.rmi.services to java.rmi;
  exports org.newsclub.net.unix.demo.rmi.fd to java.rmi;
}
