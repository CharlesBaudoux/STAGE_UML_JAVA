# umlg — UML class diagrams from annotated code

`umlg` generates PlantUML class diagrams (SVG or PNG) from a compiled Java project. Nothing is
guessed: a class enters the diagram because it carries `@UMLClass`, a link is drawn because an
annotation declares it or because the field type says so, and what holds for the whole project is
written once, in an `umlg.json` file.

Three pieces:

- **the library** (`umlg`): the annotations, the generator, and an annotation processor that
  refuses wrongly annotated code at compile time;
- **the Maven plugin** (`umlg-maven-plugin`): `mvn compile umlg:generate`, with not one argument
  to write;
- **the `umlg.json` file**: the rules of the project — strategy, noise, type renamings, views.

## Installation

### Graphviz

**The layout of a class diagram is done by Graphviz: `dot` must be installed.** It is not a Maven
dependency, so it does not install itself.

```sh
apt install graphviz        # or: brew install graphviz
dot -V                      # must answer with a version
```

Failing that, the `GRAPHVIZ_DOT` environment variable can name the executable.

The generation refuses to work without it, and says so — without that guard, PlantUML would write
an image reading "Cannot find Graphviz" that nothing tells apart from a successful drawing. **The
build of the library itself requires it too**: a score of tests really render an SVG, and on a
machine without `dot` they fail on obscure content assertions rather than on a configuration
message.

### The library and the plugin

The library is not published on a central repository: it must be installed locally, once, together
with the plugin.

```sh
cd UMLGenerator           && mvn install -DskipTests
cd ../umlg-maven-plugin   && mvn install
```

## Wiring a project: the complete pom

```xml
<properties>
  <!-- umlg requires Java 21. -->
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  <!--
    Writes parameter names in the .class files. Without this option, reflection knows only
    "arg0", "arg1"… and umlg then displays types only: "entries(String)" instead of
    "entries(xKeyName : String)". The generation reports it if the option is missing.
  -->
  <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>

<dependencies>
  <!--
    The annotations, and the processor checking at compile time what they declare. javac
    discovers it today through META-INF/services as soon as the library is on the classpath, but
    it now warns about it and announces that this automatic discovery will disappear: naming it
    explicitly, as in the compiler block below, is the form that will last.
  -->
  <dependency>
    <groupId>io.github.oliviercailloux</groupId>
    <artifactId>umlg</artifactId>
    <version>1.0-SNAPSHOT</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <!--
        The version, and this is no stylistic precaution: without it, Maven resolves 3.1,
        published in 2013, older than the "parameters" setting. The maven.compiler.parameters
        property above is then silently ignored — the signatures lose their parameter names, and
        umlg advises adding what you have already written.
      -->
      <version>3.13.0</version>
      <configuration>
        <!--
          Without showWarnings, Maven passes "-nowarn" to javac, which silences every warning of
          the umlg processor — that is, everything it has to say, its errors aside.
        -->
        <showWarnings>true</showWarnings>
        <!--
          Names the processor rather than letting it be discovered. javac now warns that
          discovery through the classpath will disappear; this form will not move.
        -->
        <annotationProcessorPaths>
          <path>
            <groupId>io.github.oliviercailloux</groupId>
            <artifactId>umlg</artifactId>
            <version>1.0-SNAPSHOT</version>
          </path>
        </annotationProcessorPaths>
        <compilerArgs>
          <!--
            Gives the processor the rules file of the project. Without this option, it ignores
            the chosen strategy and stays silent on everything depending on it — a @UMLInclude
            without effect, a @UMLDerived on a member the strategy does not display. It would
            rather stay silent than guess; it still needs something to know from.

            The path is written absolute, through ${project.basedir}: javac resolves a relative
            path against its own working directory, which is not necessarily the project's — in a
            multi-module build it is not. A relative path then "works" here and not there, and the
            processor merely warns that it found nothing.
          -->
          <arg>-Aumlg.rules=${project.basedir}/src/main/resources/umlg.json</arg>
        </compilerArgs>
      </configuration>
    </plugin>

    <!-- "mvn compile umlg:generate": target/classes analysed, diagrams/ written. -->
    <plugin>
      <groupId>io.github.oliviercailloux</groupId>
      <artifactId>umlg-maven-plugin</artifactId>
      <version>1.0-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

The generation is then run with:

```sh
mvn compile umlg:generate
```

It writes `diagrams/` at the root of the project: the overview view (`project.svg`) at its root,
the other views in `diagrams/views/`, each image accompanied by its `.puml` source, and the
complete list of the classes left out of the diagram in `ignored-classes.txt`. Settings through
properties: `-Dumlg.outputFile=architecture.svg` (a single file), `-Dumlg.format=png`,
`-Dumlg.outputDirectory=…`, `-Dumlg.skip=true`.

Without Maven, the same generation is obtained programmatically:

```java
UMLGenerator.builder()
    .sourceDirectory("target/classes")
    .outputDirectory("diagrams")
    .build()
    .generate();   // returns a GenerationReport
```

## Annotating

`@UMLClass` is the pivotal annotation: a class that does not carry it is ignored (the generation
counts those classes, and names them in `ignored-classes.txt`). The rest of the vocabulary, in
brief — each annotation carries its Javadoc:

| Annotation | What it says |
|---|---|
| `@UMLClass(name, stereotype, color, strategy)` | this class is a box; its strategy says which members are drawn |
| `@UMLAssociation(target, notation, type, …)` | this field is a link — a stroke (`ARROW`) or a line in the box (`ATTRIBUTE`) |
| `@UMLDependency`, `@UMLAssociationClass` | dependencies, association classes |
| `@UMLInclude(name, asAttribute)` | show this member whatever the strategy; `name` renames it, `asAttribute = true` writes an accessor in attribute notation (`xKeycodeByXKeyName : Map<String, Short>`, without parentheses) |
| `@UMLExclude` | silence this member — the veto of highest priority; set on the declaration of an interface, it holds for every override |
| `@UMLMultiplicity("0..*")` | the multiplicity of a property, when the type does not say it |
| `@UMLNote`, `@UMLConstraint`, `@UMLDerived`, `@UMLPackage` | bubbles, constraints, computed members, visual groups |

The components of a record are its definition: they are drawn whatever the strategy, `@UMLExclude`
remaining the only way of silencing one.

## The `umlg.json` file

Optional, placed in `src/main/resources/umlg.json` (the build copies it to the root of the
analysed directory). A JSON object, one key per nature of rule; an unknown key stops the
generation, and a rule left without effect is reported at the end of the scan. JSON has no
comments: a key whose name starts with `_` is not read, and that is where a note goes. The
essentials:

```json
{
  "_": "What the boxes show, without annotating every class.",
  "strategy": "public",

  "_hide": "Name patterns — methods and fields alike; \"*\" stands for any sequence of characters.",
  "hide": ["get*", "set*", "toString", "with*", "is*", "log", "LOGGER"],

  "_omit": "A void return is not written; no \"+\" or \"-\" before the members; \"from(KeyboardMap)\" instead of \"from(map : KeyboardMap)\"; no italics on an operation without implementation.",
  "omit": ["return void", "visibility", "parameter names", "abstract"],

  "_types": "The name under which a type is displayed.",
  "types": { "ImmutableList": "List" },

  "_order": "The members in code order, rather than alphabetical.",
  "order": "declaration",
  "_multiplicity": "Set<X> is written \"X [0..*]\", Optional<X> \"X [0..1]\".",
  "multiplicity": "brackets",

  "_drawing": "The title of the overview view, its reading direction, and the empty compartments restored.",
  "title": "Architecture of the module",
  "direction": "left-to-right",
  "showEmptyMembers": true,

  "views": [
    { "name": "Mapping", "retain": ["KeysymEntry", "KeyboardMap"],
      "remove": ["XkbSymbolsReader"], "direction": "left-to-right" },
    { "name": "Around X", "retain": [{ "around": "X" }] }
  ],
  "_viewsByPackage": "One view per package, without declaring anything.",
  "viewsByPackage": true
}
```

A string in a `retain` or a `remove` designates a class; `{ "package": … }` takes a whole package,
`{ "around": … }` a class and its immediate neighbours. The writing order is that of the drawing.

### Validating the file in an editor

A JSON schema is delivered with the library, as `umlg-schema.json`: it describes the eleven keys,
the words each of them accepts, and the shape of a view. The file refers to it through `$schema`,
which is not a rule and is not read — it is the usage of the format, and it is what gives, in an
editor, key completion and underlining of faults, without setting anything else:

```json
{
  "$schema": "https://raw.githubusercontent.com/CharlesBaudoux/STAGE_UML_JAVA/main/UMLGenerator/src/main/resources/umlg-schema.json",
  "strategy": "public"
}
```

A local copy does just as well, and avoids the network: `"$schema": "./umlg-schema.json"`, the file
set next to `umlg.json`.

The schema says what a schema can say. Three refusals remain the reader's alone, which it holds:
two views with the same name, a JSON key written twice, and a file that is not UTF-8 without BOM.

A second description of the format ends up diverging from the first: `SchemaTest` keeps it in step
by comparing the keys and words of the schema to those the refusal messages of the reader name.
Adding a twelfth key without writing it in the schema fails the build.

Eleven keys, and **none** redoing what an annotation can do. The criterion is a single one: *if the
decision designates an element by its name, it is an annotation; otherwise it is a rule.* Hence the
precedence rule, with no exception to remember: **the annotation always wins over the rule.**

Three consequences: no rule designates a member by name — `"hide": ["Representation.string"]` is
refused, `@UMLExclude` is made for that; no rule files a named class — `@UMLPackage` alone; no rule
makes a box or a link — what is not in the code is not on the diagram. And what says nothing of the
model is set at the call rather than here: `.keepPrevious(true)`, or `-Dumlg.keepPrevious=true`,
keeps the previous generation in a numbered neighbouring directory rather than replacing it.

Two words, and what separates them: `hide` makes a member vanish, `omit` removes a piece of the
line of a member that stays displayed. The word alone says whether `@UMLInclude` can contradict it
— it can for `hide`, and there is nothing to bring back for `omit`.

## Colours

`@UMLClass(color = ...)` accepts a hexadecimal code (`"#DDEEFF"`, `"#ABC"`) or a PlantUML colour
name (`"red"`, `"LightBlue"`). The complete catalogue is obtained through
`UMLGenerator.knownColors()`.

## What the generation reports

The generation says what it did — how many diagrams, where, how many classes drawn and ignored —
and warns about what is worth seeing without stopping the drawing: boxes empty for lack of a
strategy, compilation without `-parameters`, rules left a dead letter, an `around` view with no
neighbour, a diagram with unreadable proportions. Faults, for their part, stop it: contradictory
annotation, unknown colour, faulty rules file or one that is not UTF-8 — and the annotation
processor says the same things as early as compile time, down to the line.
