/**
 * MySQL-specific code.
 */
@SuppressWarnings("module") module org.newsclub.net.mysql {
  exports org.newsclub.net.mysql;

  requires org.newsclub.net.unix;
  requires java.sql;
  requires java.base;

  // requires mysql.connector.java; // until 8.0.30
  requires transitive mysql.connector.j; // from 8.0.31

  requires static com.kohlschutter.annotations.compiletime;
  requires static org.eclipse.jdt.annotation;
}
