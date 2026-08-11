package io.github.oliviercailloux.keyboardd.representable;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Zone;
import io.github.oliviercailloux.svgb.RectangleElement;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/** Not really immutable as the XML elements are not. 
 * 
 * Does not implement equals because hashCode would be infinitely recursive.
*/
public class SvgXKey {
  static SvgXKey create(String xKeyName, Zone keyZone, RectangleElement rectangle) {
    return new SvgXKey(xKeyName, keyZone, rectangle);
  }

  private final String xKeyName;
  private final Zone keyZone;
  private final RectangleElement rectangle;
  private ImmutableSortedSet<SvgKeysymEntry> svgKeysymEntries;

  private SvgXKey(String xKeyName, Zone keyZone, RectangleElement rectangle) {
    this.xKeyName = xKeyName;
    this.keyZone = keyZone;
    this.rectangle = rectangle;
    this.svgKeysymEntries = null;
  }

  void setContent(Set<SvgKeysymEntry> entries) {
    checkArgument(entries.stream().map(SvgKeysymEntry::zone).distinct().count() == entries.size());
    Comparator<Point> pointComparator = Comparator.comparing(Point::x).thenComparing(Point::y,
        Comparator.<Double>naturalOrder().reversed());
    Comparator<Zone> zoneComparator = Comparator.comparing(Zone::topLeft, pointComparator)
        .thenComparing(Zone::bottomRight, pointComparator);
    this.svgKeysymEntries = ImmutableSortedSet
        .copyOf(Comparator.comparing(SvgKeysymEntry::zone, zoneComparator), entries);
  }

  public String xKeyName() {
    return xKeyName;
  }

  public Zone keyZone() {
    return keyZone;
  }

  public RectangleElement rectangle() {
    return rectangle;
  }

  public ImmutableSortedSet<SvgKeysymEntry> svgKeysymEntries() {
    return svgKeysymEntries;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("xKeyName", xKeyName).add("keyZone", keyZone)
        .add("rectangle", rectangle).add("svgKeysymEntries", svgKeysymEntries).toString();
  }
}
