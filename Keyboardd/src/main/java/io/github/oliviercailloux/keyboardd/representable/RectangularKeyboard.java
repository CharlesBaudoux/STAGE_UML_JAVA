package io.github.oliviercailloux.keyboardd.representable;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.geometry.Displacement;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Zone;
import java.util.Objects;
import java.util.Set;

/**
 * A keyboard, conceived as a set of keys that each have a position, a rectangular shape, a size,
 * and an X key name corresponding to the signal that it sends.
 * <p>
 * The unit is 1 cm.
 * <p>
 * Two such keyboards are equal iff they have equal sets of keys.
 */
@UMLClass
public class RectangularKeyboard {
  /**
   * Builds a rectangular keyboard from a set of rectangular keys.
   * <p>
   * If the given set of keys overlaps (i.e., if two keys have a common point), then the behavior is
   * unspecified. Future versions of this library may throw an exception in that case.
   *
   * @param physicalKeys may be empty; one key must have the top left corner at the origin
   *        ({@link Point#zero()}); duplicate X key names are allowed
   * @return a rectangular keyboard
   */
  public static RectangularKeyboard from(Set<RectangularKey> physicalKeys) {
    return new RectangularKeyboard(physicalKeys);
  }

  /**
   * Allows for duplicate x key names.
   */
  // notation = ATTRIBUTE : le README écrit ce lien comme une ligne de la boîte,
  // « physicalKeys: RectangularKey [0..*] », et non comme un trait vers RectangularKey.
  @UMLAssociation(
      target = RectangularKey.class,
      targetRole = "physicalKeys",
      targetCardinality = "0..*",
      notation = UMLNotation.ATTRIBUTE)
  private final ImmutableSet<RectangularKey> keys;

  private RectangularKeyboard(Set<RectangularKey> physicalKeys) {
    this.keys = ImmutableSet.copyOf(physicalKeys);
    ImmutableMultiset<Point> starts = physicalKeys.stream().map(k -> k.zone().topLeft())
        .collect(ImmutableMultiset.toImmutableMultiset());
    checkArgument(starts.size() == starts.entrySet().size(), starts);
    if (!physicalKeys.isEmpty()) {
      checkArgument(starts.stream().anyMatch(c -> c.equals(Point.zero())));
    }
  }

  /**
   * Returns the set of keys that compose this keyboard.
   *
   * @return an empty set, or a set of keys, one of which having the top left corner at the origin
   *         ({@link Point#zero()})
   */
  public ImmutableSet<RectangularKey> keys() {
    return keys;
  }

  public Zone zone() {
    return Zone.enclosing(keys.stream().map(k -> k.zone()).toArray(Zone[]::new));
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof RectangularKeyboard)) {
      return false;
    }
    final RectangularKeyboard t2 = (RectangularKeyboard) o2;
    return keys.equals(t2.keys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keys);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("physicalKeys", keys).toString();
  }
}
