/**
 * RMI support.
 */
module org.newsclub.net.unix.rmi {
  requires transitive org.newsclub.net.unix;
  requires transitive java.rmi;
  requires org.newsclub.net.unix.server;

  exports org.newsclub.net.unix.rmi;

  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
