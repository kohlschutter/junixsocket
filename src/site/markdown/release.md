# How to release junixsocket

NOTE: This is probably not interesting unless you're the project admin or going to fork it.

## Prerequisites

##### github.com credentials

1. On github.com, create an OAuth token with write permissions to the repo.
2. Add the credentials to your keychain
    
##### GPG keys

Instructions for macOS

 * Install gpg and helper tools    

    brew install gpg gpg2 gpg-agent pinentry-mac`


 * Enable pinentry-mac (allows us to store the GPG keys in the macOS keychain)

    # open or create ~/.gnupg/gpg-agent.conf 
    # then add the following line if it doesn't exist yet:
    pinentry-program /usr/local/bin/pinentry-mac
    
 * Generate GPG key
 
    gpg2 --generate-key 
    # Follow on-screen instructions. Use a long, memorable passphrase. Remember GPG key ID
    
    # Publish the GPG public key on the GPG keyservers
    gpg2 --send-keys THEKEYID
    
## Common tasks

#### Ensure code is properly formatted and licenses are in place

    cd junixsocket
    # review LICENSE file and verify that it's up-to-date
    mvn java-formatter:format
    mvn license:format
    # git add / commit here...

#### Bump project version

    cd junixsocket
    mvn versions:set -DnewVersion=2.1.0
    # git add / commit here...
    mvn scm:tag

#### Deploy code

##### 1. Deploy to staging

  
    cd junixsocket
    mvn clean install -Pstrict
    mvn deploy -Psigned
    
NOTE: `-Pstrict` enforces *spotbugs* and *checkstyle* checks to succeed.

##### In case of failures while staging:

If the deployment fails with `Remote staging failed: Staging rules failure!` and due to
`No public key: Key with id: (...) was not able to be located on ...`,
then that means that the GPG key you created above has not been fully distributed among the GPG key
servers. Try to manually push to the ones mentioned in the error message, and try again.

For example:

    `gpg2 --keyserver http://keyserver.ubuntu.com:11371 --send-keys THEKEYID`
    
##### 2. Review the deployed artifacts
  
    The URL of the staging repository is `https://oss.sonatype.org/content/groups/staging`.
    The artifacts can be found [here](https://oss.sonatype.org/content/groups/staging/com/kohlschutter/junixsocket/).

##### 3. Release artifact to Maven Central
  
    mvn nexus-staging:release     
    
#### Deploy website 

This builds the Maven site and publishes it to [https://kohlschutter.github.io/junixsocket/](https://kohlschutter.github.io/junixsocket/).

    cd junixsocket 
    mvn clean install
    mvn site site:stage scm-publish:publish-scm

NOTE: There can be a 10-minute delay until the pages get updated automatically in your browser. Hit refresh to expedite.

#### Prepare next version

    mvn versions:set -DnewVersion=2.1.1-SNAPSHOT
    # git add / commit here...