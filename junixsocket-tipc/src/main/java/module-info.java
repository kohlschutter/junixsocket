/**
 * The junixsocket implementation for AF_TIPC sockets.
 */
module org.newsclub.net.unix.tipc {
  exports org.newsclub.net.unix.tipc;

  requires transitive org.newsclub.net.unix;
  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
