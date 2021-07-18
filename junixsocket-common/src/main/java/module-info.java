module org.newsclub.net.unix {
  exports org.newsclub.net.unix;

  requires java.base;
  requires static java.rmi;
  requires static com.kohlschutter.annotations.compiletime;
  requires transitive static org.eclipse.jdt.annotation;
}
