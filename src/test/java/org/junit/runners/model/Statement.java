package org.junit.runners.model;

/**
 * This is a hack to allow usage of Testcontainers while also excluding junit4
 * Ref. <a href="https://github.com/testcontainers/testcontainers-java/issues/970">...</a>
 * When Testcontainers 2.x is released we can probably remove this.
 */

@SuppressWarnings("unused")
public class Statement {
}
