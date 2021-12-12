/**
 * MySQL-specific code.
 */
@SuppressWarnings("module") module org.newsclub.net.mysql {
  exports org.newsclub.net.mysql;

  requires org.newsclub.net.unix;
  requires java.sql;
  requires java.base;

  requires mysql.connector.java;

  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
