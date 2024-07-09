# Bugs in other places found thanks to junixsocket

Thanks to junixsocket development, as well as due to running the [selftest](selftest.html) suite, several bugs in other projects and platforms (even kernels) have been found.

Here is an incomplete list.

* IBM AIX: AIX is vulnerable to privilege escalation (CVE-2024-27273, CVSS Base score: 8.1)

  `SO_PEERID` was incompletely implemented for datagrams, resulting in uid/gid=0 for all users
  
  see [IBM Security Advisory 7150297](https://www.ibm.com/support/pages/node/7150297)

* IBM i

  A backwards-incompatible change was introduced in JDK 15 [OpenJ9 issue 9788](https://github.com/eclipse-openj9/openj9/issues/9788)

* FreeBSD

  Linuxulator bug [277118](https://bugs.freebsd.org/bugzilla/show_bug.cgi?id=277118)

* Haiku OS

  Three kernel bugs: [18534](https://dev.haiku-os.org/ticket/18534), [18535](https://dev.haiku-os.org/ticket/18535), [18539](https://dev.haiku-os.org/ticket/18539)

* Java: [JDK-8335600](https://bugs.openjdk.org/browse/JDK-8335600), [JDK-8316703](https://bugs.openjdk.org/browse/JDK-8316703)

* GraalVM: [issue 547](https://github.com/graalvm/native-build-tools/issues/547)

* Maven: [MINSTALL-201](https://issues.apache.org/jira/browse/MINSTALL-201), [MNG-8178](https://issues.apache.org/jira/browse/MNG-8178), [MJLINK-82](https://issues.apache.org/jira/browse/MJLINK-82), 

* PMD: [issue 4620](https://github.com/pmd/pmd/issues/4620), [issue 4609](https://github.com/pmd/pmd/pull/4609)

More about junixsocket's own issues can be found in the junixsocket [bug tracker](https://github.com/kohlschutter/junixsocket/issues).
