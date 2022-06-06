/**
 * Base implementation for junixsocket servers.
 */
module org.newsclub.net.unix.server {
  exports org.newsclub.net.unix.server;

  requires transitive org.newsclub.net.unix;
  requires static com.kohlschutter.annotations.compiletime;
  requires org.eclipse.jdt.annotation;
}
