package io.github.oliviercailloux.keyboardd.keyboard.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reader of a json object that represents a rectangular keyboard (meaning a keyboard including
 * only rectangular keys), described row by row.
 */
public class JsonRectangularKeyboardReader {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonRectangularKeyboardReader.class);

  private JsonRectangularKeyboardReader() {}

  public static JsonRectangularRowKeyboard rowKeyboard(CharSource source) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JavaType listOfKeysType =
        mapper.getTypeFactory().constructCollectionType(List.class, JsonRectangularRowKey.class);
    JavaType listOfListOfKeysType =
        mapper.getTypeFactory().constructCollectionType(List.class, listOfKeysType);
    try (Reader reader = source.openStream()) {
      List<List<JsonRectangularRowKey>> rows = mapper.readValue(reader, listOfListOfKeysType);
      return JsonRectangularRowKeyboard.fromRows(rows);
    }
  }
}
