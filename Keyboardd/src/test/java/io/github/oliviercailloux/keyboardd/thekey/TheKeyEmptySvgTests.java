package io.github.oliviercailloux.keyboardd.thekey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.keyboardd.keyboard.json.JsonRectangularKeyboardReader;
import io.github.oliviercailloux.keyboardd.representable.RectangularKeyboard;
import io.github.oliviercailloux.keyboardd.representable.SvgKeyboard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class TheKeyEmptySvgTests {
  private static final double KEYS_WIDTH_CM = 1d;
  private static final double KEYS_HEIGHT_CM = 1d;
  private static final Size KEYS_SIZE = Size.given(KEYS_WIDTH_CM, KEYS_HEIGHT_CM);

  private static final double KEYS_HORIZONTAL_SPACING_CM = 0.2d;
  private static final Size KEYS_SPACING =
      Size.given(KEYS_HORIZONTAL_SPACING_CM, 0d);

  @Test
  public void writeEmptySvg() throws IOException {
    CharSource json =
        Resources.asCharSource(getClass().getResource("The Key.json"), StandardCharsets.UTF_8);
    RectangularKeyboard keyboard =
        JsonRectangularKeyboardReader.rowKeyboard(json).toPhysicalKeyboard(KEYS_SIZE, KEYS_SPACING);
    Document svgDoc = SvgKeyboard.zonedFrom(keyboard).document();
    String svgString = DomHelper.domHelper().toString(svgDoc);

    CharSource svg =
        Resources.asCharSource(getClass().getResource("The Key.svg"), StandardCharsets.UTF_8);
    assertEquals(svg.read(), svgString);
  }
}
