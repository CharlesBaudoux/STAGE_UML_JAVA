package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;

import static com.google.common.base.Preconditions.checkNotNull;

/** A key displaying a string. */
@UMLClass
public record StringRepresentation (String string) implements Representation {
  public StringRepresentation {
    checkNotNull(string);
  }
}
