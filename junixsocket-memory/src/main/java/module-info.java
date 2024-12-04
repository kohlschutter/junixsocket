/**
 * The junixsocket implementation for memory-related operations
 */
module org.newsclub.net.unix.memory {
  exports org.newsclub.net.unix.memory;

  requires transitive org.newsclub.net.unix;

  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
