/**
 * The junixsocket implementation for things specific to Darwin (macOS kernel), such as AF_SYSTEM
 * sockets.
 */
module org.newsclub.net.unix.darwin {
  exports org.newsclub.net.unix.darwin.system;

  requires transitive org.newsclub.net.unix;
  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
