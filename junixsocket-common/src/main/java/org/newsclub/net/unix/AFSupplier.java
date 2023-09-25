package org.newsclub.net.unix;

@FunctionalInterface
interface AFSupplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();
}
