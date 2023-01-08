/**
 * Helper package to identify the Maven artifact with JNI libraries for specific architectures.
 *
 * There are multiple artifacts with the same identifier (junixsocket-native), one per architecture.
 *
 * If you want to run junixsocket on your architecture, you need to make sure that the correct one
 * is in the classpath.
 *
 * See "junixsocket-native-common" for an artifact containing the commonly used architectures.
 */
package org.newsclub.lib.junixsocket.custom;
