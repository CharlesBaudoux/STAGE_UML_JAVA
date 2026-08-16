/**
 * Generates a PlantUML class diagram from annotated Java code.
 *
 * <p>The user annotates their classes, names a directory of compiled classes, and receives an
 * image. The path from a class to the drawing is always the same: the annotated code is read by
 * reflection, what is drawn from it forms a model, and that model is written as PlantUML.
 *
 * <p>{@link io.github.oliviercailloux.umlg.UMLGenerator} is the only entry point.
 */
package io.github.oliviercailloux.umlg;
