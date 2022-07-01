# How to release junixsocket

NOTE: This is probably not interesting unless you're the project admin or going to fork it.

## Prerequisites

### llvm and mingw-w64

In order to build for all supported target platforms included in the release, our development
machine (the one where we do the compilation), needs to have clang and llvm, as well as
x86_64-w64-mingw32-gcc, a GCC compiler binary to allow building for Windows.

On Mac, run the following command.

    brew install llvm mingw-w64
    
If you don't have Homebrew, obtain it from [here](https://brew.sh/) first.

### github.com credentials

1. On github.com, create an OAuth token with write permissions to the repo.
2. Add the credentials to your keychain
    
### GPG keys

Instructions for macOS

 * Install gpg and helper tools    

    brew install gpg gpg2 gpg-agent pinentry-mac


 * Enable pinentry-mac
 
   This gives a nice GUI for the passphrase, and allows us to store the GPG key passphrase in the macOS keychain)

    # open or create ~/.gnupg/gpg-agent.conf 
    # then add the following line if it doesn't exist yet:
    pinentry-program /usr/local/bin/pinentry-mac
    
 * Generate GPG key
 
 
    gpg2 --generate-key 
   
   Follow on-screen instructions. Use a long, memorable passphrase.
   
   Remember the GPG key ID. Publish the corresponding GPG public key on the GPG keyservers:
   
    gpg2 --keyserver hkp://hkps.pool.sks-keyservers.net --send-key THEKEYID
    
### Build environment for other platforms

Currently, the easiest way to build for other platforms is to have a working Java 9 (or later)
environment, Maven 3+ and the junixsocket project ready. Just spin up a virtual machine (or emulator),
install Java, Maven and junixsocket, and you should be good to go.
    
## Common tasks

### Ensure the code is properly formatted and licenses are in place

    cd junixsocket
    # review LICENSE file and verify that it's up-to-date
    mvn formatter:format
    mvn license:format
    # git add / commit here...

### Bump project version

    cd junixsocket
    mvn versions:set -DnewVersion=2.5.1
    # git add / commit here...
    
### Build native libraries on other supported, common platforms

This currently means amd64-Linux-gpp in addition to our default x86_64-MacOSX-gpp environment. 

On the target machine, install junixsocket. Make sure you use the very same version as on your
development machine from where you do the release!

    cd junixsocket
    mvn clean install -Pstrict

The platform-dependent nar files should now be available in the local maven repository cache.

Use the provided script to copy the corresponding nar to a project folder:

    cd junixsocket
    # replace 2.5.1 with the desired version number
    junixsocket-native-prebuilt/bin/copy-nar-from-m2repo.sh 2.5.1

Now copy the nar files from the target machine to your development computer (from where you do the release).
By convention, copy the files to the same folder as on the target machine (*junixsocket/junixsocket-native-prebuilt/bin*)

On the development computer, install the nar files in the local Maven repository cache:

    cd junixsocket
    junixsocket-native-prebuilt/bin/install-to-m2repo.sh junixsocket-native-prebuilt/nar/*nar

### Create binary distribution

This will create a directory, a .tar.gz and a .zip archive, containing the project jars and
a script to run the demo classes from the command-line.

    cd junixsocket
    mvn clean install -Pstrict -Prelease

The files can be found in

   * `junixsocket/junixsocket-dist/target/junixsocket-dist-(VERSION)-bin`
   * `junixsocket/junixsocket-dist/target/junixsocket-dist-(VERSION)-bin.tar.gz`
   * `junixsocket/junixsocket-dist/target/junixsocket-dist-(VERSION)-bin.zip`

### Deploy code to Maven central

#### 1. Deploy to staging
  
    cd junixsocket
    mvn clean install -Pstrict -Prelease

    # after gpgkeyname, specify the key you want to use for signing
    mvn deploy -Pstrict -Prelease -Psigned -Dgpgkeyname=$(git config --get user.email) -Dgpg.executable=$(which gpg)
    
##### Notes

`-Pstrict` enforces code quality checks to succeed (e.g., *spotbugs*, *checkstyle*). 

`-Prelease` makes sure we include all common native binaries in junixsocket-native-common.

`-Psigned` enables signing the artifacts with our GPG key. 

##### In case of failures while staging:

If the deployment fails with `Remote staging failed: Staging rules failure!` and due to
`No public key: Key with id: (...) was not able to be located on ...`,
then that means that the GPG key you created above has not been fully distributed among the GPG key
servers. Try to manually push to the ones mentioned in the error message, and try again.

For example:

    gpg2 --keyserver http://keyserver.ubuntu.com:11371 --send-key THEKEYID

If you get an error `Remote staging failed: A message body reader for Java class
com.sonatype.nexus.staging.api.dto.StagingProfileRepositoryDTO, and Java type class
com.sonatype.nexus.staging.api.dto.StagingProfileRepositoryDTO, and MIME media type text/html was
not found`, then simply try again; see [OSSRH-51097](https://issues.sonatype.org/browse/OSSRH-51097).

#### 2. Review the deployed artifacts
  
The URL of the staging repository is `https://oss.sonatype.org/content/groups/staging`.
The artifacts can be found [here](https://oss.sonatype.org/content/groups/staging/com/kohlschutter/).

If you're deploying a `-SNAPSHOT` version, you can find the artifacts
[here](https://oss.sonatype.org/content/repositories/snapshots/com/kohlschutter/junixsocket/).

**IMPORTANT** Double-check that the staged junixsocket-native-common artifact contains all required
library binaries (macOS, Linux 64-bit, etc.).

At that point, it is a good idea to download the junixsocket-selftest-jar-with-dependencies.jar,
and try it on all supported platforms. The last output line should say "Selftest PASSED".

#### 3. Release artifact to Maven Central
  
**IMPORTANT** Once released, it cannot be undone! Make sure you verify the staged artifact first!
  
    MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED" mvn nexus-staging:release -Prelease

NOTE: There can be quite a delay (30 minutes?) until the artifact is deployed in Maven Central.

### Tag the release, push to upstream (i.e., GitHub)

    mvn scm:tag

### Release on GitHub
    
1. Log in to GitHub, go to Releases -> Draft a new release.

2. Select the newly created tag (= search for the version).

3. Release title = "junixsocket" + version>, e.g., "junixsocket 2.5.1"

4. Paste changelog contents to text field

5. Upload binaries: `junixsocket-dist/target/junixsocket-dist-(VERSION)-bin.tar.gz` /
   `junixsocket-dist-(VERSION)-bin.zip` as well as
   `junixsocket-selftest/target/junixsocket-selftest-(VERSION)-jar-with-dependencies.jar`

6. Hit "Publish release"

### Publish website 

This builds the Maven site 

    cd junixsocket
    mvn clean && \
      mvn install site -Pstrict,release && \
      mvn javadoc:aggregate -P '!with-native,!with-non-modularized,strict,release' && \
      mvn jxr:aggregate jxr:test-aggregate -P strict,release && \
      mvn site:stage -Pstrict,release

The website should now be inspected at `junixsocket/target/staging/index.html`

If everything looks good, we can publish it to
[https://kohlschutter.github.io/junixsocket/](https://kohlschutter.github.io/junixsocket/):
   
    mvn scm-publish:publish-scm -Pstrict,release

NOTE: There can be a 10-minute delay until the pages get updated automatically in your browser cache.
Hit refresh to expedite.

### Prepare next version

    mvn versions:set -DnewVersion=X.Y.Z-SNAPSHOT
    mvn clean install
    
