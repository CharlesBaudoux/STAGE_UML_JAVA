package io.github.oliviercailloux.keyboardd.keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.keyboardd.keyboard.json.JsonRectangularKeyboardReader;
import io.github.oliviercailloux.keyboardd.keyboard.json.JsonRectangularRowKeyboard;
import io.github.oliviercailloux.keyboardd.representable.RectangularKeyboard;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class RectangularKeyboardTests {
  @Test
  public void testFull() throws Exception {
    CharSource source = Resources.asCharSource(
        JsonRectangularKeyboardReader.class.getResource("Keyboard layout full.json"),
        StandardCharsets.UTF_8);

    JsonRectangularRowKeyboard layout = JsonRectangularKeyboardReader.rowKeyboard(source);
    RectangularKeyboard physicalKeyboard =
        layout.toPhysicalKeyboard(Size.square(2d), Size.square(1d));
    assertEquals(Size.given(47d, 17d), physicalKeyboard.zone().size());
  }
}
