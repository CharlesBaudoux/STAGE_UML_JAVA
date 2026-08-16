package io.github.oliviercailloux.umlg.model;

/** What a box is, and the word PlantUML writes before its name. */
public enum UmlEntityKind {
    /** An ordinary class. */
    CLASS("class"),

    /** An abstract class. */
    ABSTRACT_CLASS("abstract class"),

    /** One interface. */
    INTERFACE("interface"),

    /** An enumeration. */
    ENUM("enum"),

    /** One annotation. */
    ANNOTATION("annotation");

    private final String wordKey;

    UmlEntityKind(String wordKey) {
        this.wordKey = wordKey;
    }

    /** {@return the word PlantUML writes at the head of the box} */
    public String keyword() {
        return wordKey;
    }
}
