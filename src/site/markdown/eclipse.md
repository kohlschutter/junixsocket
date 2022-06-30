# Developing with Eclipse

Eclipse is the preferred development environment for junixsocket. Here are some steps that make your life easier.

NOTE: There are no special instructions if you "just use" junixsocket. Simply [add junixsocket to your dependencies](dependency.html).

## Install a recent version of Eclipse

Download the most recent version of the Eclipse IDE from https://www.eclipse.org/

It is recommended to download the package "Eclipse IDE for Java Developers" or
"Eclipse IDE for Enterprise Java Developers". You can always add packages later through
"Eclipse Help -> Install new software" or the Eclipse Marketplace. 

## Keep the "junixsocket-native-*" projects closed

Due to incompatibilities with Eclipse m2e, you need to keep the following projects closed
(or simply do not import them into the Eclipse workspace in the first place):

   - junixsocket-native-common
   - junixsocket-native-custom
   
If you don't, you may encounter the following error:

    java.lang.UnsatisfiedLinkError: Could not load native library.

Moreover, if you try to run "Selftest" (from junixsocket-selftest) from within Eclipse, it will
most likely tell you that the selftest failed.

## Code Formatter and Style conventions
 
We use the coding conventions from [https://github.com/kohlschutter/coding-style](https://github.com/kohlschutter/coding-style).

The first time you run `mvn clean install` on junixsocket, these configurations will be automatically
installed into a `coding-style` folder in the top-level project (unless the projects exists one level
above the junixsocket-parent project).

You are encouraged to import the corresponding configuration files to your Eclipse workspace.

If you don't want to apply these settings to your other projects, create a new Eclipse workspace
for junixsocket. You can also run `mvn formatter:format` to apply the formatting rules from the command line. 

## Code quality checks

You should install SpotBugs and CheckStyle. They're incredibly helpful tools to ensure high-quality
code.

The `coding-style` project has configurations for both. Import them into Eclipse.

   - `coding-style/eclipse/checkstyle-configuration.xml` (add to Eclipse Preferences -> Checkstyle -> New... -> External Configuration File) 
   - `coding-style/eclipse/spotbugs-exclude.xml` (add to Eclipse Preferences -> Java -> SpotBugs -> Filter Files -> Exclude filter files)

You can use `mvn clean install` to check from the command line. Add `-Pstrict` to fail if there are issues.

## Native hooks

Enable "Refresh using native hooks or polling" (under Eclipse Preferences -> General -> Workspace)
I don't know why this isn't on by default.

## Use Java 11 or newer for development

Make sure Java 11 or later is installed and available in Eclipse.

## Working with the native C JNI library

Whenever you want to recompile the library, make sure to rebuild from the command line, using the
following commands:

    cd junixsocket 
    mvn clean install -DskipTests=true
    
The `-DskipTests=true` makes sure we can actually install the new code, even if it may not pass all
unit tests.

If you just want to see if the code compiles, use this instead:

    cd junixsocket 
    mvn clean install -DskipTests=true -pl junixsocket-native
