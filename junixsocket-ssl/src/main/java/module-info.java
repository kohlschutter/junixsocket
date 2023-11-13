/**
 * junixsocket-ssl
 */
module org.newsclub.net.unix.ssl {
  exports org.newsclub.net.unix.ssl;

  requires transitive org.newsclub.net.unix;

  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
