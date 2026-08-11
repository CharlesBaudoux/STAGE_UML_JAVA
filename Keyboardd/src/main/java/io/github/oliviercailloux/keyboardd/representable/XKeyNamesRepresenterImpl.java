package io.github.oliviercailloux.keyboardd.representable;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;

class XKeyNamesRepresenterImpl implements XKeyNamesRepresenter {
  public static XKeyNamesRepresenterImpl
      using(Function<String, List<Representation>> representations) {
    return new XKeyNamesRepresenterImpl(representations);
  }

  private final Function<String, List<Representation>> representations;

  private XKeyNamesRepresenterImpl(Function<String, List<Representation>> representations) {
    this.representations = representations;
  }

  @Override
  public ImmutableList<Representation> representations(String name) {
    return ImmutableList.copyOf(representations.apply(name));
  }
}
