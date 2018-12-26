# Maven dependencies

Add the following dependency to your Maven project

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-core</artifactId>
      <version>2.1.0</version>
    </dependency>

([See here](customarch.html) how to add support for custom architectures that aren't supported out of the box).
    
If you're going to use RMI over Unix sockets, add the following dependency:
    
    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-rmi</artifactId>
      <version>2.1.0</version>
    </dependency>

If you're going to use the mySQL Connector for Unix sockets, add the following dependency:

    <dependency>
      <groupId>com.kohlschutter.junixsocket</groupId>
      <artifactId>junixsocket-mysql</artifactId>
      <version>2.1.0</version>
    </dependency>
 
# Gradle
 
 Minimum requirement:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-core:2.1.0'
 
 For RMI support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-rmi:2.1.0'
 
 For MySQL support, add:
 
    compile 'com.kohlschutter.junixsocket:junixsocket-mysql:2.1.0'
 