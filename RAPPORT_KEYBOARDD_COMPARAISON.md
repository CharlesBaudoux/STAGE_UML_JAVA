# Keyboardd annoté : ce que la comparaison avec les diagrammes du professeur nous apprend

*Le projet `Keyboardd/` a été annoté et doté d'un `umlg.rules`. Les diagrammes engendrés sont
comparés, ligne à ligne, à ceux que le professeur a écrits à la main. Analyse au 7 août 2026.*

---

## 1. Ce qui a été fait sur Keyboardd

| Élément | État |
|---|---|
| Classes annotées | 16 (elles l'étaient déjà) |
| `src/main/resources/umlg.rules` | **créé** : 4 vues du README, 2 vues de voisinage, `views by package` |
| `maven.compiler.parameters` | **ajouté** au `pom.xml` |
| `@UMLMultiplicity` | **ajoutées** (5), là où le README en montre |
| `notation = ATTRIBUTE` | **posée** sur `RectangularKeyboard.keys` |
| Diagrammes produits | **11**, aucune image d'erreur |

Le projet compile sans un seul avertissement du processeur d'annotations.

## 2. La comparaison, vue par vue

Lignes de texte comparées après normalisation (visibilité et paramètres de type ignorés, pour
mesurer le fond plutôt que la forme) :

| Vue | Professeur | Nous | Communes | Chez lui seulement |
|---|---|---|---|---|
| Mapping | 15 | 16 | **12** | 3 |
| Mnemonics | 20 | 21 | **13** | 7 |
| XKeys | 7 | 8 | 3 | 4 |
| Representable | 26 | 24 | 10 | 16 |

Les deux premières vues sont reproduites à 80 % et 65 %. Les deux dernières décrochent, et pour
des raisons qu'il faut distinguer : certaines tiennent à notre bibliothèque, d'autres à ses
diagrammes.

---

## 3. Le défaut principal : le document se trompe sur l'effacement de type

Le document affirme, à deux endroits, que les paramètres de type ne survivent pas à la
compilation :

> « \texttt{List<Commande>} ne se distingue plus de \texttt{List} »
> « trois informations ont déjà disparu […] les **paramètres de type des classes génériques**, et
> le **contenu des collections** »

**C'est faux, et c'est mesurable.** L'effacement de type affecte l'exécution — un `List<String>`
et un `List<Integer>` sont le même objet en mémoire — mais **les signatures déclarées conservent
leurs paramètres de type**, dans l'attribut `Signature` du fichier `.class`. La réflexion les
expose :

```
getReturnType()         ->  ImmutableBiMap
getGenericReturnType()  ->  ImmutableBiMap<String, Short>      ← mesuré sur Xkeys

getType()               ->  ImmutableSet
getGenericType()        ->  ImmutableSet<RectangularKey>        ← mesuré sur RectangularKeyboard
```

C'est l'écart numéro un avec les diagrammes du professeur : il écrit
`xKeycodeByXKeyName: Map<String, Short>`, nous écrivons `Map`. Presque chaque ligne diffère par
cela seul.

**Conséquences, par ordre d'importance :**

1. **Afficher les paramètres de type** (`Map<String, Short>`) rapprocherait immédiatement nos
   quatre vues des siennes. C'est le changement au plus fort rendement de toute l'analyse.
2. **`target` sur `@UMLAssociation` devient déductible** quand le type le porte :
   `ImmutableSet<RectangularKey>` dit la classe visée. L'annotation la plus lourde du jeu — dix
   paramètres — pourrait rendre son seul paramètre obligatoire facultatif. Ce ne serait pas
   deviner : ce serait *lire*, ce que le principe « rien n'est deviné » n'interdit pas.
3. **La multiplicité, elle, reste indéductible**, et le document a raison sur ce point : rien dans
   `ImmutableSet<String>` ne dit `0..*` plutôt que `1..*`.

Le document doit être corrigé sur ce point : il justifie l'obligation de `target` par une
impossibilité qui n'en est pas une.

---

## 4. Un contresens sémantique de notre rendu

Le professeur écrit :

```
deprecatedAliases: String [0..*]        ucp: int [0..1]
representations(name: String): Representation [0..*]
```

Nous écrivons :

```
-deprecatedAliases: Set [0..*]          -ucp: Optional [0..1]
+representations(name: String): List [0..*]
```

Ce n'est pas une simple différence de goût. `Set [0..*]` se lit « **zéro ou plusieurs Set** », ce
qui est faux : il y a un seul ensemble, contenant zéro ou plusieurs chaînes. En UML, dès qu'une
multiplicité est écrite, le type qui la précède est celui de l'**élément**, jamais celui du
conteneur.

Notre rendu est donc, ici, sémantiquement incorrect — c'est le défaut le plus sérieux que la
comparaison ait révélé, et il passait inaperçu tant qu'on ne confrontait pas le résultat à un
diagramme écrit par quelqu'un qui connaît UML.

**Correction** : lorsqu'un membre porte une multiplicité et que son type est une collection
paramétrée, afficher le paramètre de type. `ImmutableSet<String>` + `[0..*]` donne
`String [0..*]`. Cela découle directement du point 3 et se traite d'un même geste.

---

## 5. Ce que le professeur dessine et que le code ne dit pas

Trois écarts ne viennent pas de nous, et méritent d'être nommés parce qu'ils fixent une limite.

**Une hiérarchie qui n'existe pas.** Il dessine `Representation` avec deux sous-classes,
`StringRepresentation` et `SvgRepresentation`. Le code n'en a qu'une : `Representation` est une
classe unique à deux champs (`string`, `svg`) et deux fabriques statiques. Son diagramme décrit
une **intention de conception**, pas le code.

**Des attributs sur une interface.** Il montre `Xkeys` avec `xKeycodeByXKeyName: Map<…>` comme
attribut. `Xkeys` est une interface : elle n'a que des méthodes. Il a dessiné le concept, pas la
signature.

**Des noms périmés.** Il écrit `byKeysymMnemonic`, `xKeycodeByXKeyName`, `canonicalXKeyNameByAlias`.
Le code dit aujourd'hui `byMnemonic`, `codeByName`, `canonicalByAlias`. Ses diagrammes ont
divergé du code depuis leur écriture.

**Ce que cela signifie.** Nous ne « dépasserons » jamais ses diagrammes sur ce terrain, parce que
nous ne faisons pas la même chose : il dessine ce qu'il *veut dire*, nous montrons ce qui *est
écrit*. Mais c'est précisément l'argument de la bibliothèque — le troisième écart est une
démonstration en acte : **ses diagrammes mentent déjà, les nôtres ne peuvent pas.**

---

## 6. Ce que nous faisons et qu'il ne fait pas

- **11 diagrammes au lieu de 4**, dont 6 calculés sans rien déclarer (une vue par paquetage) et
  2 vues de voisinage — « tout ce qu'il faut regarder pour comprendre `SvgKeyboard` ».
- **Toujours à jour** : un renommage suit, une classe ajoutée entre dans sa vue de paquetage.
- **Vérifiés à la compilation** : une cible non annotée, une multiplicité impossible, une couleur
  inconnue arrêtent `javac`.
- **Le fichier de règles remplace la répétition** : `hide accessors` et les six lignes `type`
  valent pour les 31 classes.

---

## 7. Ce qu'il faut changer dans la bibliothèque

Par ordre de rapport valeur/coût.

| # | Changement | Pourquoi | Coût |
|---|---|---|---|
| 1 | **Lire les paramètres de type** (`getGenericType`) et les afficher | Écart n°1 avec les diagrammes du professeur ; corrige une affirmation fausse du document | Moyen |
| 2 | **Type de l'élément quand une multiplicité est écrite** | Notre rendu est aujourd'hui un contresens UML | Faible, une fois 1 fait |
| 3 | **Rendre `target` facultatif** quand le type paramétré le donne | Allège l'annotation la plus lourde du jeu | Moyen |
| 4 | Une règle **`hide visibility`** | Le professeur n'affiche pas `+`/`-` ; c'est un choix de lecture qui vaut pour tout le projet | Faible |
| 5 | Corriger le document sur l'effacement de type | Il justifie une contrainte par une impossibilité inexistante | Faible |

**Ce que je ne changerais pas.** L'obligation de déclarer la multiplicité : la comparaison
confirme que `0..*` et `1..*` ne se lisent nulle part. Et le refus de dessiner ce qui n'est pas
dans le code : c'est ce qui distingue nos diagrammes des siens.

---

## Annexe — le `umlg.rules` de Keyboardd

```
title Keyboardd — vue d'ensemble
strategy none

hide accessors
hide object-methods
hide record-accessors
hide enum-methods
hide return void

type ImmutableSet -> Set
type ImmutableList -> List
type ImmutableMap -> Map
type ImmutableBiMap -> BiMap
type ImmutableSetMultimap -> SetMultimap
type ImmutableListMultimap -> ListMultimap

view "XKeys" = Xkeys
view "Mapping" = KeysymEntry, Mnemonic, Ucp, Code, KeyboardMap, XkbSymbolsReader
view "Mapping" direction left-to-right
view "Mnemonics" = CanonicalKeysymEntry, CanonicalMnemonic, ImplicitUcp, Mnemonics, CanonicalKeyboardMap
view "Representable" = Representation, XKeyNamesRepresenter, XKeyNamesAndRepresenter, CanonicalKeyboardMapRepresenter, RectangularKeyboard, RectangularKey, SvgKeyboard
view "Representable" direction left-to-right

view "Autour de SvgKeyboard" = around SvgKeyboard
view "Autour de CanonicalKeyboardMap" = around CanonicalKeyboardMap

views by package
output replace
```

Noter `strategy none` : les boîtes du README sont très sélectives — `SvgKeyboard` a quarante
méthodes publiques, dont six sont montrées. C'est `@UMLInclude`, membre par membre, qui le permet,
et aucune règle générale ne peut prendre cette décision éditoriale à la place de l'auteur.
