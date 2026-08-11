package io.github.oliviercailloux.keyboardd.keyboard.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.oliviercailloux.geometry.Size;

/**
 * A key, part of a rectangular keyboard. Such a key has a specified relative width but no specified
 * height: all keys in a rectangular keyboard have the same height.
 */
public record JsonRectangularRowKey (String xKeyName, double relativeWidth) {
  @JsonCreator()
  public JsonRectangularRowKey(@JsonProperty("xKeyName") String xKeyName,
      @JsonProperty("width") Double width) {
    this(xKeyName == null ? "" : xKeyName, width == null ? 1d : width);
  }

  public Size relativeSize() {
    return Size.given(relativeWidth, 1d);
  }
}
