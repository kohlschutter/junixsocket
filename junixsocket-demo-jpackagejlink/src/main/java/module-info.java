/**
 * jpackage/jlink demo module
 */
module org.newsclub.net.unix.demo.jpackagejlink {
  exports org.newsclub.net.unix.demo.jpackagejlink;

  requires java.base;

  requires org.newsclub.net.unix;
  requires com.kohlschutter.junixsocket.nativecommon;
}
