package io.github.oliviercailloux.keyboardd.representable;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import io.github.oliviercailloux.keyboardd.mapping.KeyboardMap;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry;
import java.util.Map;

class VisibleKeyboardMapImpl implements XKeyNamesAndRepresenter {
  private final ImmutableListMultimap<String, Representation> representations;

  VisibleKeyboardMapImpl(ListMultimap<String, Representation> representations) {
    this.representations = ImmutableListMultimap.copyOf(representations);
  }

  @Override
  public ImmutableSet<String> names() {
    return representations.keySet();
  }

  @Override
  public ImmutableList<Representation> representations(String name) {
    return representations.get(name);
  }

  @Override
  public ImmutableListMultimap<String, Representation> representations() {
    return representations;
  }
}
