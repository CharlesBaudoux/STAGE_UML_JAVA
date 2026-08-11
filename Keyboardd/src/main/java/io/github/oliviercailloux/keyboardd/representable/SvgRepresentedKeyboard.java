package io.github.oliviercailloux.keyboardd.representable;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Zone;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeysymEntry;
import io.github.oliviercailloux.svgb.RectangleElement;
import io.github.oliviercailloux.svgb.TextElement;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;

public class SvgRepresentedKeyboard {
  static SvgRepresentedKeyboard given(SvgKeyboard svgKeyboard, Set<SvgXKey> svgXKeys) {
    return new SvgRepresentedKeyboard(svgKeyboard, svgXKeys);
  }

  private final SvgKeyboard svgKeyboard;
  private final ImmutableSortedMap<SvgXKey, String> svgXKeysToXKeyName;

  private SvgRepresentedKeyboard(SvgKeyboard svgKeyboard, Set<SvgXKey> svgXKeys) {
    this.svgKeyboard = svgKeyboard;
    Comparator<Point> pointComparator = Comparator.comparing(Point::y).thenComparing(Point::x);
    Comparator<Zone> zoneComparator = Comparator.comparing(Zone::topLeft, pointComparator)
        .thenComparing(Zone::bottomRight, pointComparator);
    this.svgXKeysToXKeyName = svgXKeys.stream().collect(ImmutableSortedMap.toImmutableSortedMap(
        Comparator.comparing(SvgXKey::keyZone, zoneComparator), s -> s, SvgXKey::xKeyName));
  }

  public Document document() {
    return svgKeyboard.document();
  }

  public ImmutableSortedMap<SvgXKey, String> svgXKeysToXKeyName() {
    return svgXKeysToXKeyName;
  }

  public ImmutableSortedSet<SvgXKey> svgXKeys(String xKeyName) {
    return ImmutableSortedSet.copyOf(svgXKeysToXKeyName.comparator(),
        svgXKeysToXKeyName.asMultimap().inverse().get(xKeyName));
  }

  public ImmutableSet<String> xKeyNames() {
    return ImmutableSet.copyOf(svgXKeysToXKeyName.values());
  }
}
