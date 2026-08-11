package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import static com.google.common.base.Preconditions.checkState;

import org.w3c.dom.Document;

/**
 * What a key displays: either a string, or an SVG document.
 * <p>
 * The two cases are two implementations rather than two fields of which exactly one is filled.
 * The invariant — one and only one of the two — is then carried by the type system instead of by
 * a constructor check, and the diagram shows the alternative for what it is.
 */
// La hierarchie a trois boites est celle du diagramme tenu a la main. Elle vivait auparavant
// dans le fichier de regles, qui declarait deux classes absentes du code ; ce qui n'est pas dans
// le code n'etant pas sur le diagramme, c'est le code qui a rattrape l'ecart.
@UMLClass
public sealed interface Representation permits StringRepresentation, SvgRepresentation {
  public static Representation fromString(String string) {
    return new StringRepresentation(string);
  }

  public static Representation fromSvg(Document svg) {
    return new SvgRepresentation(svg);
  }

  public default boolean isString() {
    return this instanceof StringRepresentation;
  }

  public default boolean isSvg() {
    return this instanceof SvgRepresentation;
  }

  public default String string() {
    checkState(this instanceof StringRepresentation);
    return ((StringRepresentation) this).string();
  }

  public default Document svg() {
    checkState(this instanceof SvgRepresentation);
    return ((SvgRepresentation) this).svg();
  }
}
