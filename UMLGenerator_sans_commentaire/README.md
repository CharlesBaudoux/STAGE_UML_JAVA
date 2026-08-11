# umlg — des diagrammes de classes UML depuis le code annoté

`umlg` engendre des diagrammes de classes PlantUML (SVG ou PNG) à partir d'un projet Java
compilé. Rien n'y est deviné : une classe entre dans le diagramme parce qu'elle porte
`@UMLClass`, un lien se dessine parce qu'une annotation le déclare ou que le type de l'attribut
le dit, et ce qui vaut pour le projet entier s'écrit une fois, dans un fichier `umlg.rules`.

Trois morceaux :

- **la bibliothèque** (`umlg`) : les annotations, le générateur, et un processeur d'annotations
  qui refuse à la compilation un code annoté à tort ;
- **le plugin Maven** (`umlg-maven-plugin`) : `mvn compile umlg:generate`, sans un argument à
  écrire ;
- **le fichier `umlg.rules`** : les règles du projet — stratégie, bruit, renommages de types,
  vues.

## Installation

La bibliothèque n'est pas publiée sur un dépôt central : il faut l'installer localement, une
fois, ainsi que le plugin.

```sh
cd UMLGenerator           && mvn install -DskipTests
cd ../umlg-maven-plugin   && mvn install
```

## Brancher un projet : le pom complet

```xml
<properties>
  <!-- umlg demande Java 21. -->
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  <!--
    Écrit les noms des paramètres dans les .class. Sans cette option, la réflexion ne
    connaît que « arg0 », « arg1 »… et umlg n'affiche alors que les types :
    « entries(String) » au lieu de « entries(xKeyName : String) ». La génération le
    signale si l'option manque.
  -->
  <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>

<dependencies>
  <!--
    Les annotations, et le processeur qui vérifie à la compilation ce qu'elles déclarent.
    javac le découvre aujourd'hui par META-INF/services dès que la bibliothèque est sur le
    classpath, mais il en avertit désormais et annonce que cette découverte automatique
    disparaîtra : le nommer explicitement, comme dans le bloc du compilateur ci-dessous, est
    la forme qui durera.
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
        La version, et ce n'est pas une précaution de style : sans elle, Maven résout la 3.1,
        publiée en 2013, antérieure au paramètre « parameters ». La propriété
        maven.compiler.parameters ci-dessus est alors ignorée en silence — les signatures
        perdent leurs noms de paramètres, et umlg conseille d'ajouter ce que vous avez déjà
        écrit.
      -->
      <version>3.13.0</version>
      <configuration>
        <!--
          Sans showWarnings, Maven passe « -nowarn » à javac, ce qui fait taire tous les
          avertissements du processeur d'umlg — c'est-à-dire tout ce qu'il a à dire, ses
          erreurs mises à part.
        -->
        <showWarnings>true</showWarnings>
        <!--
          Nomme le processeur au lieu de le laisser découvrir. javac avertit désormais que la
          découverte par le classpath disparaîtra ; cette forme-ci ne bougera pas.
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
            Donne au processeur le fichier de règles du projet. Sans cette option, il ignore
            la stratégie retenue et se tait sur tout ce qui en dépend — un @UMLInclude sans
            effet, un @UMLDerived sur un membre que la stratégie n'affiche pas. Il préfère se
            taire que deviner ; encore faut-il lui donner de quoi savoir.
          -->
          <arg>-Aumlg.rules=src/main/resources/umlg.rules</arg>
        </compilerArgs>
      </configuration>
    </plugin>

    <!-- « mvn compile umlg:generate » : target/classes analysé, diagrammes/ écrit. -->
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

La génération se lance alors par :

```sh
mvn compile umlg:generate
```

Elle écrit `diagrammes/` à la racine du projet : la vue d'ensemble (`projet.svg`) à sa racine,
les autres vues dans `diagrammes/vues/`, chaque image accompagnée de sa source `.puml`, et la
liste complète des classes restées hors du diagramme dans `ignored-classes.txt`. Réglages par
propriétés : `-Dumlg.outputFile=architecture.svg` (un seul fichier), `-Dumlg.format=png`,
`-Dumlg.outputDirectory=…`, `-Dumlg.skip=true`.

Sans Maven, la même génération s'obtient par programme :

```java
UMLGenerator.builder()
    .sourceDirectory("target/classes")
    .outputDirectory("diagrammes")
    .build()
    .generate();   // rend un RapportDeGeneration
```

## Annoter

`@UMLClass` est l'annotation pivot : une classe qui ne la porte pas est ignorée (la génération
compte ces classes, et les nomme dans `ignored-classes.txt`). Le reste du vocabulaire, en bref —
chaque annotation porte sa Javadoc :

| Annotation | Ce qu'elle dit |
|---|---|
| `@UMLClass(name, stereotype, color, strategy)` | cette classe est une boîte ; sa stratégie dit quels membres se dessinent |
| `@UMLAssociation(target, notation, type, …)` | cet attribut est un lien — un trait (`ARROW`) ou une ligne dans la boîte (`ATTRIBUTE`) |
| `@UMLDependency`, `@UMLAssociationClass` | dépendances, classes d'association |
| `@UMLInclude(name, asAttribute)` | montre ce membre quelle que soit la stratégie ; `name` le renomme, `asAttribute = true` écrit un accesseur en notation attribut (`xKeycodeByXKeyName : Map<String, Short>`, sans parenthèses) |
| `@UMLExclude` | tais ce membre — le veto de plus haute priorité ; posé sur la déclaration d'une interface, il vaut pour tous les overrides |
| `@UMLMultiplicity("0..*")` | la multiplicité d'une propriété, quand le type ne la dit pas |
| `@UMLNote`, `@UMLConstraint`, `@UMLDerived`, `@UMLPackage` | bulles, contraintes, membres calculés, groupes visuels |

Les composants d'un record sont sa définition : ils se dessinent quelle que soit la stratégie,
`@UMLExclude` restant le seul moyen d'en taire un.

## Le fichier `umlg.rules`

Facultatif, posé dans `src/main/resources/umlg.rules` (la construction le recopie à la racine du
répertoire analysé). Une règle par ligne, `#` ouvre un commentaire ; un mot-clé inconnu arrête la
génération, et une règle restée sans effet est signalée en fin de scan. L'essentiel :

```
strategy public                 # ce que les boîtes montrent, sans annoter chaque classe
hide get*, set*, toString       # des motifs de noms : méthodes et attributs indifféremment
hide with*, is*, log, LOGGER    #   « * » remplace n'importe quelle suite de caractères
omit return void                # un retour void ne s'écrit pas
omit visibility                 # ni « + » ni « - » devant les membres
omit parameter names            # « from(KeyboardMap) » au lieu de « from(map : KeyboardMap) »
omit abstract                   # pas d'italique sur une opération sans implémentation
type ImmutableList -> List      # le nom sous lequel un type s'affiche
order declaration               # les membres dans l'ordre du code, plutôt qu'alphabétique
multiplicity brackets           # Set<X> s'écrit « X [0..*] », Optional<X> « X [0..1] »
title Architecture du module    # le titre de la vue d'ensemble
direction left-to-right         # son sens de lecture
show empty members              # rétablit les compartiments vides
view "Mapping" = KeysymEntry, KeyboardMap - XkbSymbolsReader
view "Mapping" direction left-to-right
view "Autour de X" = around X   # une classe et ses voisins déclarés
views by package                # une vue par paquetage, sans rien déclarer
```

Onze mots-clés, et **aucun** qui refasse ce qu'une annotation sait faire. Le critère est unique :
*si la décision désigne un élément par son nom, c'est une annotation ; sinon c'est une règle.* D'où
la règle de priorité, sans exception à retenir : **l'annotation l'emporte toujours sur la règle.**

Trois conséquences : aucune règle ne désigne un membre nommément — `hide Representation.string`
est refusé, `@UMLExclude` est fait pour cela ; aucune règle ne range une classe nommée —
`@UMLPackage` seul ; aucune règle ne fabrique une boîte ni un lien — ce qui n'est pas dans le code
n'est pas sur le diagramme. Et ce qui ne dit rien du modèle se règle à l'appel plutôt qu'ici :
`.keepPrevious(true)`, ou `-Dumlg.keepPrevious=true`, conserve la génération précédente dans un
dossier voisin numéroté au lieu de la remplacer.

Deux verbes, et ce qui les sépare : `hide` fait disparaître un membre, `omit` retire un morceau de
la ligne d'un membre qui reste affiché. Le mot dit à lui seul si `@UMLInclude` peut le contredire —
il le peut pour `hide`, et il n'y a rien à ramener pour `omit`.

## Les couleurs

`@UMLClass(color = ...)` accepte un code hexadécimal (`"#DDEEFF"`, `"#ABC"`) ou un nom de
couleur PlantUML (`"red"`, `"LightBlue"`). Le catalogue complet s'obtient par
`UMLGenerator.couleursConnues()`.

## Ce que la génération signale

La génération dit ce qu'elle a fait — combien de diagrammes, où, combien de classes dessinées et
ignorées — et avertit sur ce qui vaut d'être vu sans arrêter le dessin : boîtes vides faute de
stratégie, compilation sans `-parameters`, règles restées lettre morte, vue `around` sans voisin,
diagramme aux proportions illisibles. Les fautes, elles, arrêtent : annotation contradictoire,
couleur inconnue, fichier de règles fautif ou qui n'est pas en UTF-8 — et le processeur
d'annotations dit les mêmes choses dès la compilation, à la ligne près.
