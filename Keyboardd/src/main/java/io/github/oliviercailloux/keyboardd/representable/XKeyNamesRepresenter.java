package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;

@UMLClass
public interface XKeyNamesRepresenter {
  public static XKeyNamesRepresenter using(Function<String, List<Representation>> representations) {
    return XKeyNamesRepresenterImpl.using(representations);
  }

  // La multiplicite n'est plus declaree : « multiplicity brackets » la lit dans le type.
  @UMLInclude
  ImmutableList<Representation> representations(String name);
}
