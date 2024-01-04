# Developing with Xcode

The recommended editor for working with the native C code is Xcode. Even for non-Apple targets.

This works thanks to crossclang, also see [Cross-compiling junixsocket](crosscomp.html).

## Setup

In order to develop with Xcode, using crossclang, you need to install a custom Xcode SDK.

Run the following script from the Terminal, which creates a symbolic link under
`~/Library/Developer/Toolchains` to the `crossclang.sdk`, which essentially tells Xcode to use
our "crossclang" clang wrapper script instead of its built-in version, among other things.

    ./junixsocket-native/crossclang/Xcode-Support/install

You then need to:

1. Restart Xcode
2. Set the Toolchain to "crossclang" (via Xcode -> Toolchains..., or via a button in the launch window)
3. Open junixsocket-native.xcodeproj
4. Run "Clean Build Folder"
5. Run "Build"

Note that you need to switch back to the default Xcode Toolchain when you develop other projects. 

## Building from the command-line

AFter installing the "crossclang" Toolchain, you can also build from the command-line.
In this case, you don't have to switch the Toolchain via Xcode:

    xcodebuild -project junixsocket-native/junixsocket-native.xcodeproj -configuration Release \
        -target "All Architectures" -toolchain crossclang USE_HEADERMAP=NO \
        CURRENT_ARCH=undefined_arch arch=undefined_arch ARCHS=arm64 CODE_SIGNING_ALLOWED=NO \
        clean build 
