# Developing with Eclipse

Eclipse is the preferred development environment for junixsocket. Here are some steps that make your life easier.

NOTE: There are no special instructions if you "just use" junixsocket. Simply [add junixsocket to your dependencies](dependency.html).

## Keep the "junixsocket-native-*" projects closed

Due to incompatibilities with Eclipse m2e, you need to keep the following projects closed, unless
you're going to modify their POMs.

   - junixsocket-native
   - junixsocket-native-common
   - junixsocket-native-custom
   
If you don't, you may encounter the following error:

    java.lang.UnsatisfiedLinkError: Could not load native library.

## Code Formatter and Style conventions
 
We use the coding conventions from [https://github.com/kohlschutter/coding-style](https://github.com/kohlschutter/coding-style).

You are encouraged to import the corresponding configuration files to your Eclipse workspace.

If you don't want to apply these settings to your other projects, create a new Eclipse workspace
for junixsocket.

## Code quality checks

You should install SpotBugs and CheckStyle. They're incredibly helpful tools to ensure high-quality
code.

The `coding-style` project has configurations for both. Import them into Eclipse.

   - `coding-style/eclipse/checkstyle-configuration.xml` (add to Eclipse Preferences -> Checkstyle -> New... -> External Configuration File) 
   - `coding-style/eclipse/spotbugs-exclude.xml` (add to Eclipse Preferences -> Java -> SpotBugs -> Filter Files -> Exclude filter files)

## Native hooks

Enable "Refresh using native hooks or polling" (under Eclipse Preferences -> General -> Workspace)
I don't know why this isn't on by default.

## Use Java 11 for development

Make sure Java 9 or later is installed and available in Eclipse. If you have the choice, simply skip
Java 9 and 10, and go straight to 11.
