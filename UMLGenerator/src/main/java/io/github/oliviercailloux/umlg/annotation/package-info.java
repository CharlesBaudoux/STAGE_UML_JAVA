/**
 * The vocabulary the user writes in their own code.
 *
 * <p>Nothing here computes anything: this package holds annotations and enumerations only.
 * enumerations. All of them use {@code RetentionPolicy.RUNTIME}; otherwise they would vanish at
 * compilation, and extraction, which works by reflection, could no longer read them.
 */
package io.github.oliviercailloux.umlg.annotation;
