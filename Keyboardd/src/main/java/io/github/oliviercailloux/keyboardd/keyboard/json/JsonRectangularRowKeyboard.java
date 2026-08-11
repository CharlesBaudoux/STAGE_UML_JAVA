package io.github.oliviercailloux.keyboardd.keyboard.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.geometry.Displacement;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.geometry.Zone;
import io.github.oliviercailloux.keyboardd.representable.RectangularKey;
import io.github.oliviercailloux.keyboardd.representable.RectangularKeyboard;
import java.util.List;

/**
 * A rectangular keyboard, conceived as a list of rows (top to bottom), each row being a list of
 * keys (left to right).
 */
public class JsonRectangularRowKeyboard {
  static JsonRectangularRowKeyboard fromRows(List<? extends List<JsonRectangularRowKey>> rows) {
    return new JsonRectangularRowKeyboard(rows);
  }

  /**
   * With no empty list. Allows for duplicates (not required usually as even similar keys such as
   * the left shift and right shift keys send different codes, it seems, but some keyboards might
   * differ from mine in that respect).
   */
  private final ImmutableList<ImmutableList<JsonRectangularRowKey>> rows;

  private JsonRectangularRowKeyboard(List<? extends List<JsonRectangularRowKey>> rows) {
    checkArgument(rows.stream().noneMatch(r -> r.isEmpty()));
    this.rows = rows.stream().map(r -> ImmutableList.<JsonRectangularRowKey>copyOf(r))
        .collect(ImmutableList.toImmutableList());
  }

  /**
   * Returns the list of rows (from top to bottom) that compose this keyboard; each row being a non
   * empty list of keys (from left to right).
   *
   * @return a possibly empty list of rows
   */
  public ImmutableList<ImmutableList<JsonRectangularRowKey>> rows() {
    return rows;
  }

  /**
   * Obtains a (scaled) rectangular keyboard from this json rectangular keyboard.
   *
   * @param scale the scale to apply to the keys: the height is the height of each row in cm; the
   *        width is the width of a one unit width key in cm.
   * @param spacing the width is the horizontal space between each key in a given row; the height is
   *        the vertical space between each row.
   * @return a (scaled) rectangular keyboard
   */
  public RectangularKeyboard toPhysicalKeyboard(Size scale, Size spacing) {
    Zone rowZone = Zone.enclosing(Point.zero());

    final ImmutableSet.Builder<RectangularKey> keys = new ImmutableSet.Builder<>();
    for (ImmutableList<JsonRectangularRowKey> row : rows) {
      for (JsonRectangularRowKey sourceKey : row) {
        Size targetSize = sourceKey.relativeSize().mult(scale);
        Zone targetZone = Zone.at(rowZone.topRight(), targetSize);
        RectangularKey targetKey = RectangularKey.from(targetZone, sourceKey.xKeyName());
        keys.add(targetKey);
        Zone wholeZone = targetZone.extend(spacing.asDisplacement());
        rowZone = rowZone.andEnclosing(wholeZone.bottomRight());
      }
      rowZone = Zone.enclosing(Point.given(0d, rowZone.bottomRight().y()));
    }

    return RectangularKeyboard.from(keys.build());
  }
}
