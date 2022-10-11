/**
 * The junixsocket implementation for AF_VSOCK sockets.
 */
module org.newsclub.net.unix.vsock {
  exports org.newsclub.net.unix.vsock;

  requires transitive org.newsclub.net.unix;
  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
