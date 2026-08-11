package io.github.oliviercailloux.keyboardd.keyboard.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.geometry.Point;
import io.github.oliviercailloux.geometry.Size;
import io.github.oliviercailloux.geometry.Zone;
import io.github.oliviercailloux.keyboardd.representable.RectangularKey;
import io.github.oliviercailloux.keyboardd.representable.RectangularKeyboard;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

public class KeyboardReaderTests {
  @Test
  public void testKey() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    CharSource source = Resources.asCharSource(getClass().getResource("Key name size.json"),
        StandardCharsets.UTF_8);
    try (Reader r = source.openStream()) {
      JsonRectangularRowKey k = mapper.readValue(r, JsonRectangularRowKey.class);
      assertEquals(new JsonRectangularRowKey("Ploum", 1.5d), k);
    }
  }

  @Test
  public void testKeyName() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    CharSource source =
        Resources.asCharSource(getClass().getResource("Key name.json"), StandardCharsets.UTF_8);
    try (Reader r = source.openStream()) {
      JsonRectangularRowKey k = mapper.readValue(r, JsonRectangularRowKey.class);
      assertEquals(new JsonRectangularRowKey("Ploum", 1d), k);
    }
  }

  @Test
  public void testKeySize() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    CharSource source =
        Resources.asCharSource(getClass().getResource("Key size.json"), StandardCharsets.UTF_8);
    try (Reader r = source.openStream()) {
      JsonRectangularRowKey k = mapper.readValue(r, JsonRectangularRowKey.class);
      assertEquals(new JsonRectangularRowKey("", 1.5d), k);
    }
  }

  @Test
  public void testRow() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    CharSource source =
        Resources.asCharSource(getClass().getResource("Key row.json"), StandardCharsets.UTF_8);
    JavaType type =
        mapper.getTypeFactory().constructCollectionType(List.class, JsonRectangularRowKey.class);
    try (Reader r = source.openStream()) {
      List<JsonRectangularRowKey> ks = mapper.readValue(r, type);
      ImmutableList<JsonRectangularRowKey> row =
          ImmutableList.of(new JsonRectangularRowKey("Name1", 1.5d),
              new JsonRectangularRowKey("Name2", 1d), new JsonRectangularRowKey("", 1.5d));
      assertEquals(row, ks);
    }
  }

  @Test
  public void testLayout() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    CharSource source = Resources.asCharSource(
        getClass().getResource("Keyboard layout missing names.json"), StandardCharsets.UTF_8);
    JavaType type =
        mapper.getTypeFactory().constructCollectionType(List.class, JsonRectangularRowKey.class);
    JavaType type2 = mapper.getTypeFactory().constructCollectionType(List.class, type);
    try (Reader r = source.openStream()) {
      List<List<JsonRectangularRowKey>> ks = mapper.readValue(r, type2);
      ImmutableList<JsonRectangularRowKey> row1 =
          ImmutableList.of(new JsonRectangularRowKey("Name1", 1.5d),
              new JsonRectangularRowKey("Name2", 1d), new JsonRectangularRowKey("", 1.5d));
      ImmutableList<JsonRectangularRowKey> row2 =
          ImmutableList.of(new JsonRectangularRowKey("Name3", 1.5d),
              new JsonRectangularRowKey("Name4", 1d), new JsonRectangularRowKey("", 1.5d));
      assertEquals(ImmutableList.of(row1, row2), ks);
    }
  }

  @Test
  public void testReadSimple() throws Exception {
    CharSource source = Resources.asCharSource(
        getClass().getResource("Keyboard layout simple.json"), StandardCharsets.UTF_8);

    JsonRectangularRowKeyboard parsed = JsonRectangularKeyboardReader.rowKeyboard(source);
    ImmutableList<JsonRectangularRowKey> row = ImmutableList
        .of(new JsonRectangularRowKey("TAB", 1.5d), new JsonRectangularRowKey("AD01", 1d));
    assertEquals(ImmutableList.of(row), parsed.rows());
  }

  @Test
  public void testReadFull() throws Exception {
    CharSource source = Resources.asCharSource(getClass().getResource("Keyboard layout full.json"),
        StandardCharsets.UTF_8);

    JsonRectangularRowKeyboard parsed = JsonRectangularKeyboardReader.rowKeyboard(source);
    ImmutableList<ImmutableList<JsonRectangularRowKey>> rows = parsed.rows();
    assertEquals(6, rows.size());
    ImmutableList<JsonRectangularRowKey> row0 = rows.get(0);
    assertEquals(16, row0.size());
  }

  @Test
  public void testReadAsPhys() throws Exception {
    CharSource source = Resources.asCharSource(
        getClass().getResource("Keyboard layout two rows.json"), StandardCharsets.UTF_8);

    RectangularKey r1k1 =
        RectangularKey.from(Zone.at(Point.zero(), Size.given(3d, 2d)), "R1K1");
    RectangularKey r1k2 =
        RectangularKey.from(Zone.at(Point.given(4d, 0d), Size.given(4.5d, 2d)), "R1K2");
    RectangularKey r2k1 =
        RectangularKey.from(Zone.at(Point.given(0d, 3.2d), Size.given(6d, 2d)), "R2K1");
    RectangularKey r2k2 =
        RectangularKey.from(Zone.at(Point.given(7d, 3.2d), Size.given(3d, 2d)), "R2K2");
    RectangularKeyboard expected =
        RectangularKeyboard.from(ImmutableSet.of(r1k1, r1k2, r2k1, r2k2));

    RectangularKeyboard keyboard = JsonRectangularKeyboardReader.rowKeyboard(source)
        .toPhysicalKeyboard(Size.given(3d, 2d), Size.given(1d, 1.2d));
    assertEquals(expected, keyboard);
  }
}
