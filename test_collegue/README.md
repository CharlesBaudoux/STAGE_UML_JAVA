# Projet de démonstration — bibliothèque `umlg`

Projet Java d'un domaine bancaire fictif, dont la seule raison d'être est d'exercer **chaque
annotation, chaque paramètre et chaque valeur d'énuméré** de la bibliothèque, puis d'en produire
un vrai diagramme.

C'est un test manuel, complémentaire des tests unitaires : ceux-ci vérifient des règles une par
une, celui-ci vérifie que l'ensemble tient debout et se lit.

Il vérifie aussi, gratuitement, le **processeur d'annotations** : la bibliothèque en embarque un,
que javac découvre par `META-INF/services` dès qu'elle est sur le classpath. Une compilation
silencieuse est donc un résultat en soi — aucune des annotations écrites ici n'est fautive, ni
sans effet.

## Lancer

```bash
cd ../Bibliotheque_V0 && mvn install -DskipTests   # publie umlg 1.0-SNAPSHOT
cd ../test_collegue    && mvn compile exec:java    # produit architecture_banque.svg et .png
```

## Ce que le diagramme doit montrer

### `@UMLClass`

| Paramètre | Où le voir |
|---|---|
| `name` | `Client` s'affiche sous le nom **ClientPremium** |
| `stereotype` | `«Entity»`, `«AggregateRoot»`, `«Facade»`, `«Service»`, `«Contract»`, `«Abstraction»`, `«Marker»` |
| `color`, hexadécimal sans `#` | `Client` — fond bleu clair (`ADD8E6`) |
| `color`, hexadécimal avec `#` | `BanqueFacade` — fond gris (`#E2E2E2`) |
| `color`, nom PlantUML | `Personne` — fond jaune pâle (`LightYellow`) |
| `strategy` absente | `Adresse` — **coquille vide**, alors que la classe a trois attributs publics |
| `strategy = PUBLIC` | `CarteBancaire` — `cryptogramme` (privé) absent |
| `strategy = PROTECTED` | `Operation` — `reference` et `montant` présents, `signature` et `commentaire` absents |
| `strategy = PACKAGE` | `Compte` — `numeroIban` (`~`) présent, `soldeSecret` (privé) absent |
| `strategy = PRIVATE` | `Client` — `nom` et `email` présents |

### Nature des boîtes

Les cinq valeurs de `UmlEntityKind`, toutes déduites du code par réflexion :

| Mot-clé émis | Classe |
|---|---|
| `class` | `Compte`, `CarteBancaire`… — et `Devise`, qui est un `record` |
| `abstract class` | `Personne` — nom en italique |
| `interface` | `Identifiable`, `Auditable` |
| `enum` | `TypeCompte` — compartiment de littéraux à part |
| `annotation` | `Confidentiel` — un `@interface`, que la réflexion confond avec une interface |

### Héritage

Aucune annotation ne le déclare : `InheritanceExtractor` relit la signature Java. Une classe mère
hors du diagramme est simplement ignorée, sans erreur.

- **généralisation entre classes** — `Personne <|-- ClientPremium`, trait plein ;
- **généralisation entre interfaces** — `Identifiable <|-- Auditable`, trait plein ;
- **réalisation** — `Auditable <|.. ClientPremium` et `Identifiable <|.. Compte`, trait pointillé.

### `@UMLInclude` / `@UMLExclude`

- sur un **attribut** : `BanqueFacade.secretInterne` et `Compte.plafondAutorise` (privés) sont
  affichés ; `Client.traceInterne` ne l'est pas malgré `strategy = PRIVATE`.
- sur une **méthode**, contre la visibilité : `Operation.calculerEmpreinte()` (privée) est
  affichée ; `BanqueFacade.verifierStatutSysteme()` ne l'est pas malgré `public`.
- sur une **méthode**, contre le filtre anti-bruit : `Compte.getSoldeSecret()` est un getter en
  bonne et due forme, et il est pourtant affiché.

### `@UMLDerived`

La barre oblique qu'UML exige et que Java interdit dans un identifiant.

| Support | Où le voir |
|---|---|
| attribut | `Personne` — `# int /ageEnAnnees` |
| méthode | `ClientPremium` — `+ double /soldeTotal()` |
| lien | `ClientPremium → CarteBancaire` — la barre précède le rôle cible, `/cartesActives` |

### `@UMLConstraint`

Écrite nue ; les accolades sont posées par le générateur.

| Support | Où le voir |
|---|---|
| classe | `{email unique dans la banque}`, dessinée comme une bulle à côté de `ClientPremium` |
| classe, répétée | `Compte` — `{un compte clôturé n'accepte plus d'opération}` et `{devise immuable après ouverture}` |
| attribut | `Compte` — `- double plafondAutorise {plafondAutorise > 0}` |
| attribut, répétée | `Operation` — `# double montant {montant != 0} {arrondi à deux décimales}` |
| lien | `comptes 1..* {ordered} {un compte courant au moins}` et `consignées 0..* {ordered} {lecture seule}` |

La règle d'arbitrage se lit sur `Client.comptes` : l'attribut porte `@UMLAssociation`, donc la
contrainte part sur le trait et non dans la boîte — où l'attribut ne figure plus.

### `@UMLNote`

| Paramètre | Où le voir |
|---|---|
| `text` | les douze bulles jaunes — les trois autres viennent de `@UMLConstraint` |
| `direction = UP` | `BanqueFacade` — bulle au-dessus |
| `direction = DOWN` | `Compte` — bulle en dessous |
| `direction = LEFT` | `ClientPremium` |
| `direction = RIGHT` | `Adresse` |
| `direction` absente (`NONE`) | `Devise`, `Identifiable`, `Confidentiel` — à droite, le défaut choisi par le rendu |
| sur une **classe** | toutes celles ci-dessus |
| sur un **attribut** | `CarteBancaire.numeroMasque` |
| sur une **méthode** | `ClientPremium.soldeTotal()`, `Operation.calculerEmpreinte()` |
| **répétée** | `ClientPremium` en porte deux |

### Filtre anti-bruit

Aucune des méthodes suivantes ne doit apparaître : constructeurs, `getNom`/`setNom`/`isMajeur`
(`Client`), `getNomComplet` (`Personne`), `setSoldeSecret` et `hashCode` (`Compte`), accesseurs
canoniques du record `Devise`, `values()` et `valueOf()` de l'enum `TypeCompte`.

En revanche `Client.getaway()` **est** affichée : le préfixe seul ne fait pas l'accesseur.

### `@UMLAssociation`

| Paramètre | Où le voir |
|---|---|
| `target` explicite | `ClientPremium *-- Compte` |
| `type = COMPOSITION` | `ClientPremium` → `Compte`, `Compte` → `Operation` (losange plein) |
| `type = AGGREGATION` | `ClientPremium` → `Adresse` (losange creux) |
| `type = ASSOCIATION` | `ClientPremium` → `CarteBancaire` (flèche simple) |
| `sourceCardinality` | `"titulaire 1"`, `"journal 1"`, et `"*"` côté `Compte`/`Devise` |
| `targetCardinality` | `"comptes 1..*"`, `"domicile 0..1"`, `"historique 0..*"` |
| `sourceRole` / `targetRole` | `titulaire`, `journal`, `comptes`, `domicile`, `historique`, `consignées`, `cartesActives` |
| `isOrdered` | `{ordered}` sur trois liens |
| `qualifier` | `[dateOperation]` et `[numeroClient]`, entre crochets côté source |
| `label` | `détient`, `enregistre`, `expose`, `de type` |
| `direction` | `DOWN`, `RIGHT`, `LEFT`, `UP`, et `NONE` — les cinq sont utilisées |

**Cible toujours explicite** : `target` n'a pas de valeur par défaut, la bibliothèque ne déduit
rien du type de l'attribut. Le projet couvre malgré tout la variété des types porteurs —
`List<Compte>`, `Optional<Adresse>`, tableau `CarteBancaire[]`, valeur d'une
`Map<String, Operation>`, attribut simple `Devise`, enum `TypeCompte`, et joker borné
`List<? extends Operation>` sur `JournalAudit` — chacun avec son `target` écrit.

Un attribut annoté **quitte la boîte** : `comptes`, `adresse`, `cartes`, `operations` ne sont
plus listés comme attributs. `Compte.etiquettes`, non annoté, reste un attribut ordinaire.

### `@UMLDependency`

Traits **pointillés**, à distinguer des traits pleins :

- répétée trois fois sur `BanqueFacade` → `ServiceNotification` (`notifie via`), `JournalAudit`
  (`trace dans`), et `Devise` **sans label** ;
- unique sur `JournalAudit` → `Compte` (`audite`), et sur `Compte` → `Confidentiel`
  (`marque ses champs`).

Ces liens n'existent que par l'annotation : aucun n'est porté par un attribut.

### Hors périmètre

`com.banque.utils.Calculateur` n'est pas annotée et **ne doit pas apparaître**, bien qu'elle
référence `Devise`.

## Défauts trouvés par ce projet, et corrigés

Quatre, dont trois relevés en lisant l'image produite. C'est la raison d'être d'un test manuel :
aucun d'eux n'était visible dans les tests unitaires, qui ne les combinaient pas.

### 1. `@UMLNote(direction = UP | DOWN)` sur un membre cassait tout le diagramme

`NoteRenderer.placement` traduisait `UP` par `top` et `DOWN` par `bottom` sans distinguer la note
de classe de la note de membre. Or PlantUML n'accepte `top` et `bottom` que pour une classe : sur
un membre, seuls `left` et `right` existent.

```
note bottom of ClientPremium::soldeTotal   → « Not known: ClientPremium: »
```

PlantUML rejetait alors le document **entier**, et `PlantUmlWriter` écrivait sans broncher une
image d'erreur à la place du diagramme. Une seule annotation mal orientée, et tout le rendu était
perdu.

**Corrigé** : `NoteRenderer.placementSurMembre` rabat `UP` et `DOWN` sur la droite, et
`VerificateurNote` le signale à la compilation, à l'endroit exact où la direction est écrite.
Perdre l'orientation demandée est un moindre mal ; le taire n'en serait pas un.

### 2. Les retours à la ligne d'une note de membre s'affichaient en clair

`PlantUmlEscaper.noteBloc` s'appuyait sur `label`, qui traduit un vrai retour à la ligne en `\n` —
la notation attendue par PlantUML dans une note **d'une seule ligne**. Mais une note de membre
s'écrit en **bloc**, et PlantUML n'y interprète pas `\n` : il l'affichait tel quel.

```
Quatre derniers chiffres seulement :\nle reste ne quitte jamais la base.
```

**Corrigé** : `noteBloc` découpe le texte, échappe chaque ligne séparément et les rassemble par
de vrais sauts. La protection contre une ligne imitant le terminateur `end note` — seule raison
d'être de l'échappement d'origine — porte désormais sur **toutes** les lignes, puisque le
découpage fait de chacune une ligne du bloc.

### 3. Le PNG était tronqué sans avertissement

PlantUML plafonne ses images matricielles à 4096 px (`PLANTUML_LIMIT_SIZE`) et **rogne** le reste.
`BanqueFacade`, `Devise`, `TypeCompte`, `ServiceNotification` et `Confidentiel` disparaissaient du
`.png`, `Compte` et `JournalAudit` y étaient coupés en plein milieu, alors que le `.svg` — qui est
vectoriel, donc sans limite — était complet.

**Corrigé** : `PlantUmlWriter` pose un plafond de 16384 px sur le fil courant, puis le retire —
c'est une `ThreadLocal` chez PlantUML, et la bibliothèque ne laisse pas derrière elle un réglage
que l'appelant n'a pas demandé. Le plafond n'est pas supprimé pour autant : il existe pour éviter
qu'une image démesurée épuise la mémoire, et le retirer remplacerait un dessin tronqué par une
panne.

Le même passage a reçu son filet de sécurité : `outputImage` ne lève rien devant une source
fautive, il dessine une image d'erreur qu'aucun test de taille de fichier ne distingue d'un
diagramme réussi. Le writer vérifie donc que PlantUML accepte la source **avant** de dessiner, et
lève une `IllegalStateException` portant la source rejetée. C'est ce qui manquait pour que le
défaut n°1 se voie.

La largeur elle-même vient pour partie de `PlantUmlLayout` : `nodesep` est calculé sur la plus
longue étiquette de lien, ici `comptes 1..* {ordered} {un compte courant au moins}`, et sature
donc à 220. Chaque bulle étant par ailleurs une boîte flottante, les quinze du diagramme écartent
considérablement les classes. Ce n'est pas un bogue, mais cela reste à surveiller.

### 4. Les avertissements du processeur n'atteignaient personne

Ce projet-ci, et non la bibliothèque. `maven-compiler-plugin` passe `-nowarn` à javac tant que
`showWarnings` n'est pas demandé : **aucun** message `Diagnostic.Kind.WARNING` du processeur ne
sortait, et une compilation silencieuse ne prouvait donc rien. Le `pom.xml` l'active désormais,
ce qui rend au silence sa valeur de preuve.

## Limites connues du rendu

- Les paramètres de type sont perdus à l'affichage d'un attribut (`List` et non `List<String>`) :
  l'extraction travaille sur des classes compilées, où l'effacement a déjà eu lieu.
- PlantUML n'a pas de notation pour le carré d'une association qualifiée ; le qualificatif est
  rendu entre crochets, faute de mieux.
- Un `record` est dessiné comme une `class`. Ce n'est pas un manque : UML ne lui réserve aucune
  notation, et un record *est* une classe finale.

## Annotations de la spécification non encore implémentées

`@UMLAssociationClass`, `@UMLPackage`, `@UMLLayout`. Ce fichier est à compléter au fur et à
mesure.
