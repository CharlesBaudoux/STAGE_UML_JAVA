package io.github.oliviercailloux.keyboardd;

import com.google.common.io.CharSource;
import com.google.common.io.Resources;

public class TestResources {
  public static CharSource charSource(String resourceName) {
    return Resources.asCharSource(TestResources.class.getResource(resourceName), java.nio.charset.StandardCharsets.UTF_8);
  }
}
