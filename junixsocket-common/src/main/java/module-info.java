/**
 * The common junixsocket classes.
 */
// NOPMD -- https://github.com/pmd/pmd/issues/4620
module org.newsclub.net.unix {
  exports org.newsclub.net.unix;

  requires java.base;
  requires static java.rmi;
  requires static transitive com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
