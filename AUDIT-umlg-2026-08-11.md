# Audit d'`umlg` — 11 août 2026

Troisième audit de la bibliothèque, mené sur `UMLGenerator/` à l'état de travail courant (le
passage du fichier de règles à JSON n'est pas commité). Sept lectures parallèles couvrant
l'intégralité des 11 679 lignes de code principal et des 13 605 lignes de test, plus deux points
transversaux. Chaque trouvaille a été vérifiée dans le code ; la plupart l'ont été en exécutant
la bibliothèque sur un cobaye fabriqué pour l'occasion, et les sorties observées sont citées.

**État de départ** : `mvn verify` vert, 965 tests, 96,31 % d'instructions et 85,80 % de branches
couvertes, seuils JaCoCo (0,92 / 0,80) tenus.

---

## Ce qu'il faut retenir

**Dix-huit défauts réels, dont quinze partagent un même mode de défaillance : l'utilisateur écrit
quelque chose, la bibliothèque l'accepte, et le dessin ne le montre pas.** Pas de message, pas
d'erreur, pas de test rouge. Pour un outil dont le métier est de documenter, c'est le pire mode
de panne possible : il produit un diagramme plausible et faux, que personne ne songe à vérifier.

Trois observations structurent le reste.

**1. Les défauts sont aux jointures, pas dans les algorithmes.** Aucun calcul n'est faux. Ce qui
casse, c'est le passage d'une couche à l'autre : un nom de vue qui devient un nom de fichier sans
que personne ne rapproche les deux mondes ; un membre qui change de compartiment sans que
l'extracteur de notes en soit informé ; un type d'exception oublié à la frontière de la
réflexion. Les deux audits précédents avaient déjà conclu cela — c'est la troisième fois, et cela
cesse d'être une coïncidence : **c'est la forme que prennent les défauts de ce projet.**

**2. La hiérarchie des décisions est écrite en prose, jamais dans le code.** `package-info.java`
et les commentaires d'`estVisible` énoncent un ordre de priorité clair — `@UMLExclude` d'abord,
puis `@UMLInclude`, puis la stratégie, puis les motifs `hide`. Mais les quatre points d'entrée de
l'extraction (`extractFields`, `extractMethods`, `methodesEnAttributs`, `extractLiterals`)
réimplémentent chacun leur propre séquence de gardes, et les quatre diffèrent. Trois des bugs
tiennent là. Les tests ne le voient pas parce qu'ils exercent chaque mécanisme séparément, jamais
leur croisement — un motif `hide` **et** un record, un `@UMLExclude` **et** une interface
générique.

**3. La règle écrite deux fois finit toujours par diverger.** Le projet le sait et l'a déjà
combattu deux fois avec succès, en nommant `Multiplicites` et `ColorCodec` pour que la grammaire
n'existe qu'à un endroit. Les règles qui n'ont pas reçu ce traitement — l'axe des motifs `hide`,
la déduction de cible, les textes des messages — sont exactement celles où le processeur et la
génération ne disent plus la même chose.

Le corollaire encourageant : **le geste correctif est connu, et il a déjà marché ici.**

---

## Ce qui va bien, et qu'il ne faut pas abîmer

Il faut le dire, parce que cela conditionne la façon de corriger : ce code est nettement
au-dessus de la moyenne, et plusieurs trouvailles ci-dessous ne sont visibles *que* parce que le
reste est soigné.

- **Le déterminisme est tenu**, et sérieusement : tri des entités après `Files.walk`, tri des
  listes de messages, `Locale.ROOT` partout où une casse change, `LinkedHashMap` là où l'ordre
  porte du sens. Aucune `HashMap` de la zone d'extraction ne laisse son ordre d'itération
  atteindre la sortie.
- **L'encodage est correct de bout en bout et indépendant de la locale** — vérifié sous `LANG=C`
  et `-Dfile.encoding=ISO-8859-1`, sortie identique.
- **`module-info.java` est exemplaire** : les trois exports sont exactement les bons, aucun type
  exporté ne rend un type d'un paquetage fermé, Guava est `transitive` parce qu'elle doit
  l'être, Jackson ne l'est pas parce qu'elle ne doit pas l'être.
- **Le modèle est sans cycle** : les relations désignent leur cible par nom, jamais par
  référence — les `toString()` engendrés ne peuvent pas boucler.
- **La lecture JSON est robuste.** Environ 65 fichiers fautifs lui ont été soumis : chaque refus
  nomme le chemin exact (`views[0].retain[0]`, `types.ImmutableList`) et rappelle la forme
  attendue.
- **Les tests assertent sur la sortie, pas sur l'exécution**, et les pièges déjà rencontrés sont
  documentés là où ils ont mordu. Rien ne dépend de l'ordre d'exécution ni d'un état partagé.
- **Le README résiste à la confrontation au code** : les onze clés annoncées sont les onze clés
  lues, et tout ce qu'il promet existe. Un seul manque, signalé plus bas.

Et deux soupçons levés, qu'il est utile d'avoir écartés : les alias engendrés ne peuvent pas
entrer en collision avec un mot-clé PlantUML (dix-sept éprouvés), et la fusion des associations
réciproques fonctionne bel et bien.

---

# I. Les défauts qui perdent en silence ce que l'utilisateur a écrit

## 1. Deux vues peuvent s'écrire dans le même fichier — l'une écrase l'autre

**Gravité : Bug.** *Trouvé indépendamment par trois lectures sur sept, chacune par un chemin
différent. C'est la trouvaille la mieux établie de l'audit.*

`extraction/rules/LecteurDeRegles.java:285` — `extraction/ConstructeurDeVues.java:84` —
`rendering/PlantUmlWriter.java:110`

Le lecteur refuse deux vues **de nom identique** :

```java
checkArgument(!vues.containsKey(vue.nom()), "%s: view \"%s\" is already defined...", ou, vue.nom());
```

Mais l'identité qui compte en aval est le **nom de fichier**, produit par
`UmlView.nomDeFichier()` (`model/UmlView.java:63`) : accents décomposés puis retirés, tout ce qui
n'est ni lettre ni chiffre remplacé par un tiret, minuscules. La réduction fait converger des
noms parfaitement distincts :

```
"Le mapping" → le-mapping     "Élément"      → element      "Mapping"   → mapping
"Le Mapping" → le-mapping     "Element"      → element      "Mapping !" → mapping
"Le-mapping" → le-mapping     "Autour, de X" → autour-de-x
```

Exécuté de bout en bout avec deux vues `"Modèle"` et `"Modele"` :

```
RapportDeGeneration[..., fichiers=[sortie/projet.svg, sortie/vues/modele.svg,
                                   sortie/vues/modele.svg], ...]
--- fichiers réellement présents ---
sortie/vues/modele.puml   sortie/vues/modele.svg   (contenu : la vue « Modele »)
```

Trois dégâts d'un coup : la vue « Modèle » a disparu sans un mot ; le rapport rendu à l'appelant
annonce un fichier qui n'existe pas ; et `PlantUmlWriter.detailleePar` fait pointer la navigation
des boîtes de « Modèle » vers `modele.svg`, qui montre maintenant autre chose — **les liens
cliquables mènent au mauvais diagramme.**

`viewsByPackage` aggrave le risque : il ajoute des noms de vue que l'auteur n'a jamais écrits,
donc jamais relus.

**Correction.** Le contrôle appartient à `ConstructeurDeVues.construire`, seul endroit où toutes
les vues sont réunies — déclarées, engendrées par paquetage, et l'intégrale. Y refuser deux
`nomDeFichier()` identiques, en nommant les deux vues et en disant qu'elles se ramènent au même
fichier. Un second garde-fou dans `writeViews` (un `Set<Path>`) coûte trois lignes et ferme le
cas définitivement.

---

## 2. `@UMLExclude` posé sur une interface générique ne franchit pas l'effacement de type

**Gravité : Bug.**

`extraction/member/MemberExtractor.java:569`

```java
if (surtype.getDeclaredMethod(method.getName(), method.getParameterTypes())
    .isAnnotationPresent(UMLExclude.class)) {
```

`getDeclaredMethod` exige des types de paramètres **identiques**. Or une interface générique
déclare les siens effacés : `Boite<T>.ranger(T)` s'écrit `ranger(Object)` dans le `.class`,
tandis que la réalisation `BoiteDeTexte.ranger(String)` porte `String`. La recherche lève
`NoSuchMethodException`, avalée silencieusement ligne 573, et l'exclusion est perdue. Le pont
synthétique `ranger(Object)` — qui, lui, correspondrait — a déjà été écarté ligne 435 par
`isSynthetic()`.

```java
interface Boite<T> { @UMLExclude void ranger(T o); @UMLExclude T lire(); }
class BoiteDeTexte implements Boite<String> { ... public void metier() {} }

extractMethods(BoiteDeTexte.class, PUBLIC) → [metier, ranger]
```

`lire()` (sans paramètre) est bien exclue ; `ranger` reparaît. La fonctionnalité marche donc pour
les interfaces non génériques — ce que couvre `RecordsEtExclusionsHeriteesTest` — et se tait dès
qu'un paramètre est un paramètre de type, c'est-à-dire pour `Comparator`, `Function`, et toute
interface métier paramétrée. Le symptôme est le pire possible : **un membre que l'auteur croit
exclu réapparaît sur chaque réalisation.**

**Correction.** Remplacer `getDeclaredMethod` par un parcours de `surtype.getDeclaredMethods()`
retenant les méthodes de même nom, de même arité, dont chaque paramètre déclaré est assignable
depuis celui de la méthode candidate — c'est exactement la relation qu'établit un pont.

---

## 3. Un motif `hide` bat le composant de record et l'association en notation attribut

**Gravité : Bug.**

`extraction/member/MemberExtractor.java:136-139`, à comparer à `:159-177`

`estVisible` (ligne 159) est écrite avec soin pour poser qu'un défaut général ne défait pas une
demande explicite : un composant de record et une `@UMLAssociation(notation = ATTRIBUTE)`
paraissent quelle que soit la stratégie, « seul `@UMLExclude` garde le dernier mot ». Mais le
filtre `regles.membreMasque` s'exécute **avant**, ligne 136, sur tous les champs sans distinction.
Or un motif du fichier de règles est, tout autant que la stratégie, un défaut général.

Avec `{ "hide": ["log*"] }` :

```
record Sac(String logTexte, String garde)                     → [garde]
@UMLAssociation(notation = ATTRIBUTE) List<Cible> logCibles   → []        (le lien disparaît)
@UMLAssociation(notation = ARROW)     List<Cible> logCibles   → 1 lien    (il reste)
```

Deux conséquences. Un `record` dont un composant tombe sous un motif rend une boîte qui ne dit
plus ce que le record **est** — le contresens que le commentaire des lignes 164-171 dit vouloir
éviter. Et passer de `notation = ARROW` à `notation = ATTRIBUTE` fait disparaître le lien, alors
que la documentation présente les deux notations comme deux écritures de la même chose.

Le processeur croit d'ailleurs à la version documentée et donne un conseil destructeur — voir le
défaut **12**.

**Correction.** Faire descendre le test des motifs dans `estVisible`, au même rang que la
stratégie. Les deux branches privilégiées gardent leur `return !field.isAnnotationPresent(...)`,
la branche ordinaire devient `!masque(field, regles) && VisibilityPolicy.estVisible(...)`.

---

## 4. Un littéral d'énumération échappe à tout ce qui vaut pour les autres membres

**Gravité : Bug.**

`extraction/member/MemberExtractor.java:58` et `:83`

`extractLiterals` est le seul chemin d'extraction qui ne reçoit pas de `Regles` (comparer aux
signatures des lignes 94, 429, 510). Deux effets :

- ligne 83, `result.add(field.getName())` : `@UMLInclude(name = "…")` sur un littéral est **lu
  pour décider de sa visibilité** (lignes 78-80) puis **ignoré pour son nom**. Vérifié :
  `@UMLInclude(name = "Euro") EURO` produit `[EURO, DOLLAR]`. L'auteur écrit une annotation, le
  processeur l'accepte, le dessin l'oublie.
- aucun motif `hide` n'atteint jamais un littéral, ce que rien ne documente.

**Correction.** Donner un paramètre `Regles` à `extractLiterals`, y appliquer `membreMasque`
(sauf `@UMLInclude`, comme partout) et remplacer `field.getName()` par `nomAffiche(field)` — la
méthode existe déjà ligne 287.

---

## 5. Une dépendance absente dans une signature générique fait échouer tout le scan

**Gravité : Bug.**

`extraction/ProjectScanner.java:170`

```java
} catch (ClassNotFoundException | LinkageError e) {
```

`TypeNotPresentException` est une `RuntimeException` : elle n'est pas rattrapée. Or c'est
exactement ce que lève la réflexion quand un type manquant apparaît dans une **signature
générique** — `getGenericType()` sur `List<B>` avec `B` absent du classpath ne lève pas
`NoClassDefFoundError`.

```
@UMLClass class C { public List<B> bs; }     (sans B.class)
→ THROWN java.lang.TypeNotPresentException : Type com.ex.B not present
```

Le scan entier avorte, et le message ne nomme ni la classe, ni le fichier, ni le remède —
précisément la panne totale que le mécanisme des « classes illisibles » a été écrit pour éviter.
Le même répertoire avec un champ **non générique** (`public B b;`) est correctement absorbé : la
faille ne tient qu'à la forme de la signature, ce que rien ne permet de deviner à la lecture.

**Correction.** Ajouter `TypeNotPresentException` (et par prudence `AnnotationFormatError`) à la
clause de capture.

---

## 6. Une `@UMLNote` sur une méthode écrite en notation attribut est jetée

**Gravité : Bug.**

`extraction/NoteExtractor.java:65`

Une méthode portant `@UMLInclude(asAttribute = true)` quitte `extractMethods` pour devenir une
`UmlField` via `methodesEnAttributs`. Elle figure donc dans `champsVisibles`, **jamais** dans
`methodesVisibles`. Or la boucle sur les méthodes teste `!methodesVisibles.contains(affiche)` et
écarte la note — alors que le membre est bel et bien dessiné.

```
fields  = [UmlField[name=nom, ...]]                              ← le membre est là
notes   = [UmlNote[text=la note d'une methode ordinaire, ...]]   ← la note de nom() a disparu
```

Le silence est complet des deux côtés : `VerificateurNote.signalerAncrageIntrouvable`
(`processing/VerificateurNote.java:128`) voit `@UMLInclude` présent, conclut `visible = true` et
ne dit rien à la compilation.

**Correction.** Tester l'union des deux ensembles dans la boucle sur les méthodes.

---

## 7. Le balisage Creole de début de ligne n'est pas neutralisé

**Gravité : Bug.** *Vérifié en exécutant le vrai moteur PlantUML 1.2024.4.*

`rendering/syntax/PlantUmlEscaper.java:43` et `:65`

`label()` ne casse que les marqueurs **doublés** (`**gras**`, `[[lien]]`, `----`). Or PlantUML
applique aussi un balisage de liste, de titre et de tableau à un caractère **unique en début de
ligne rendue** : `#`, `*`, `=`, `|`. Aucun n'est couvert. Partout où le texte de l'utilisateur
ouvre une ligne, il est altéré :

| Position | Fichier | Entrée | Ce qui est dessiné |
|---|---|---|---|
| note d'une ligne | `element/NoteRenderer.java:33` | `# Attention` | `1. Attention` |
| note d'une ligne | idem | `* voir le contrat` | `voir le contrat` |
| titre du diagramme | `PlantUmlSyntaxBuilder.java:90` | `=== Facturation ===` | `Facturation ===` |
| nom de paquetage | `element/PackageRenderer.java:96` | `\|a\|b\|` | un tableau à deux cellules |
| nom de boîte | `element/ClassBoxRenderer.java:68` | `= titre` | `titre` |
| membre, `"omit": ["visibility"]` | `element/ClassBoxRenderer.java:167` | `#total : int` | `total : int` |

C'est exactement le défaut que le commentaire de `PlantUmlEscaper.java:27-30` dit avoir corrigé
(« un nom valant `**Compte**` s'affichait `Compte`… La bibliothèque promet pourtant de dessiner
ce qui est écrit ») — mais la correction s'est arrêtée aux paires. Un `@UMLNote("* point
important")` est une écriture des plus plausibles.

**Correction.** Le remède est déjà dans le fichier : `neutraliserPremierCaractere` (ligne 140),
employé par `noteBloc` pour une raison voisine. Ajouter `debutDeLigne(String)` =
`neutraliserPremierCaractere(label(texte))` et l'appeler aux cinq positions.

---

## 8. Un nom de lien sur deux lignes fait apparaître `</i></color>` sur le dessin

**Gravité : Bug.**

`rendering/element/RelationRenderer.java:67-70`

`label()` traduit un retour à la ligne en `\n`, puis `nomDeLien` enveloppe le tout dans
`<i>…</i>` (et `<color:…>` sous `UmlStyle.UMLG`). PlantUML coupe au `\n` **sans reporter les
balises ouvertes** : la seconde ligne perd son italique et affiche les balises fermantes.

```
X --> Y : <color:#3C5F82><i>lig1\nlig2</i></color>
textes du SVG : [X] [Y] [lig1] [lig2</i></color>]
```

Rien ne l'attrape : la source est valide, donc `verifierAcceptation` ne voit pas d'erreur, et
`UmlAssociation.validateTexteLibre` n'interdit pas le retour à la ligne.

**Correction.** Découper sur `\\n` dans `nomDeLien` et habiller chaque ligne séparément.

---

## 9. L'échappement peut vider la chaîne, et la chaîne vide est fautive à trois positions

**Gravité : Bug.**

`rendering/syntax/PlantUmlEscaper.java:177` — `extraction/ClassHeaderValidator.java:18,27`

Les gardes en amont emploient `isBlank()`, qui ne tient pas les caractères de contrôle pour des
blancs. Un nom ou un stéréotype fait d'un caractère de contrôle traverse donc la validation, puis
`appendSiImprimable` le réduit à `""` :

```
nom = ""         → class "" as C {
stéréotype = ""  → class "E" as E <<>> {
```

Les deux formes sont **refusées par PlantUML** (`PSystemErrorV2`), et la garde
`verifierAcceptation` lève alors une `IllegalStateException` qui accuse « un défaut de la
bibliothèque ». Troisième manifestation, silencieuse : un label réduit à `""` produit `<i></i>`,
que PlantUML dessine comme `</i>` sur le trait.

**Correction.** Faire porter les tests de vacuité sur le texte **après** échappement.

---

## 10. Le garde-fou de `"*"` se contourne en écrivant `"**"`

**Gravité : Bug.**

`extraction/rules/LecteurDeRegles.java:467`

Le refus compare la chaîne écrite à `"*"`, alors que ce qu'il veut interdire est *un motif qui
prend tout*. `Motif.enRegex` traduit `**` en `.*.*`, qui correspond à n'importe quel nom.

```
motif **  : nom=true calculer=true LOGGER=true
motif *   : REFUS — the pattern "*" would hide every member of every class.
```

`{ "hide": ["**"] }` vide donc silencieusement tous les compartiments de toutes les boîtes, et le
relevé des règles mortes reste vide puisque le motif a bien servi.

**Correction.** Tester le résultat plutôt que la lettre :
`motif.chars().allMatch(c -> c == '*')`, avec le même message.

---

## 11. La convention des clés `_` s'arrête au premier niveau

**Gravité : Bug.**

`extraction/rules/LecteurDeRegles.java:531` (`lireTypes`)

Le filtre `cle.startsWith("_")` existe dans `lire` (ligne 162) et dans `lireVue` (ligne 308), mais
pas dans `lireTypes`, pourtant le second objet à clés libres du format. La convention est
pourtant énoncée sans restriction dans les trois documents (`LecteurDeRegles.java:49`,
`Regles.java:61`, `README.md:162`).

```
{ "types": { "_note": "pourquoi", "ImmutableList": "List" } }
  → accepté ; règles mortes = [types: _note -> pourquoi, ...]

{ "types": { "_note": "une note dans types" } }
  → REFUS : types._note: a type name cannot contain a space
```

Une note d'un mot devient une règle de renommage morte ; une note ordinaire est refusée par un
message qui ne mentionne jamais la convention `_`.

**Correction.** Sortir le filtre dans une méthode partagée et l'appliquer dans `lireTypes`.

---

# II. Les divergences processeur ↔ génération

*L'invariant du projet : le processeur ne doit jamais refuser ce que la génération accepte, ni
accepter ce qu'elle refuse. Trois violations, sur trois axes différents.*

## 12. « `@UMLInclude` has no effect » est un faux reproche, et le suivre efface la ligne

**Gravité : Bug.**

`processing/VerificateurMembre.java:201-209` et `:184-191` — `processing/Strategie.java:41-43`

`Strategie.connue()` rend vrai dès que la classe déclare sa stratégie en toutes lettres, **même
sans `-Aumlg.rules`** :

```java
boolean connue(UMLClass classe) {
    return classe.strategy() != UMLVisibility.PROJECT || regles != null;
}
```

La garde protège donc l'axe « stratégie » et laisse grand ouvert l'axe « motifs `hide` », qui
décide pourtant du même dessin.

```java
@UMLClass(strategy = UMLVisibility.PRIVATE)
class Client { @UMLInclude private String log; }
```

Sans `-Aumlg.rules` :

```
WARNING : Client.log: @UMLInclude has no effect, the field is already shown by
          strategy = PRIVATE on the declaring class. Remove it, or narrow the strategy.
```

Or avec `{ "hide": ["log"] }`, ce `@UMLInclude` est le **seul** moyen de garder le champ : le
retirer, comme le processeur le conseille, efface la ligne du diagramme.

La branche voisine (ligne 184) est pire, car elle se trompe **même quand les règles sont
connues** : elle affirme « already shown in the attribute compartment, *whatever the strategy* »
pour un champ `@UMLAssociation(notation = ATTRIBUTE)` — alors que le défaut **3** montre que le
filtre anti-bruit l'écarte aussi.

**Correction.** Ajouter à `Strategie` une question distincte, `boolean reglesConnues()`
(`regles != null`), et n'émettre ces deux avertissements que lorsqu'elle rend vrai ; consulter en
outre `champMasqueParUneRegle` dans la branche `notation = ATTRIBUTE`. Les avertissements de sens
inverse (« `@UMLExclude` sans effet », etc.) n'ont pas ce défaut et doivent rester tels quels.

---

## 13. Un `@UMLAssociation` sur un champ de type variable : refusé à la compilation, dessiné à la génération

**Gravité : Bug.**

Processeur : `processing/TypeAttribut.java:148-153` → `:104` → `processing/Cible.java:49-56`
Génération : `extraction/relation/CibleDeduite.java:44-62`

```java
@UMLClass class Commande {}
@UMLClass class Boite<T extends Commande> {
    @UMLAssociation(targetCardinality = "1") private T contenu;
}
```

```
Processeur : ERROR — target() is not written, and the field's type does not name a single
                     class of the diagram. Write it explicitly.
Génération : UmlAssociation[fieldName=contenu, targetQualifiedName=Commande, ...]
```

Le processeur **arrête la compilation** sur un code que la génération dessine sans broncher :
c'est la violation la plus coûteuse de l'invariant, puisqu'elle bloque l'utilisateur. La cause :
`indetermine` rend vrai pour `TYPEVAR` côté processeur, tandis que `field.getType()` rend
l'effacement — c'est-à-dire la borne — côté réflexion.

**Correction.** Dans `TypeAttribut.elementSeul`, traiter `TYPEVAR` comme la génération :
`types.erasure(type)` donne déjà la borne. Ne pas toucher à `indetermine`, dont le rôle est
légitime ailleurs (`VerificateurAssociation.java:185`).

---

## 14. Deux contrôles où le processeur refuse ce que la génération ignore

**Gravité : Conception.**

**(a)** `@UMLInclude(asAttribute = true)` sur un attribut — `processing/VerificateurMembre.java:109`
lève une erreur. Côté génération, `methodesEnAttributs` ne parcourt que des `Method` et
`extractFields` ne consulte jamais `asAttribute` : la valeur est simplement ignorée.

**(b)** Les paramètres de trait sous `notation = ATTRIBUTE` —
`VerificateurAssociation.verifierValeurs` valide `sourceCardinality`, `sourceRole` et `label`
quelle que soit la notation, alors que `MemberExtractor.champAssocie` (`:344-349`) ne recopie que
`targetRole`, `targetCardinality` et les contraintes. Une valeur fautive dans un paramètre jamais
lu **arrête la compilation**. Le processeur est de surcroît incohérent avec lui-même :
`signalerParametresSansPlace` dit de ces mêmes paramètres qu'ils sont inoffensifs.

**Correction.** Trancher, mais des deux côtés à la fois. Le sens qui paraît juste : durcir la
génération plutôt qu'affaiblir le processeur — refuser tôt une annotation absurde est son métier.

---

## 15. `-nowarn` éteint l'avertissement de configuration que le code veut inéteignable

**Gravité : Bug.**

`processing/Rapporteur.java:60`

Le commentaire est sans ambiguïté : « faire taire les avertissements veut dire *ne me dis pas ce
que tu as trouvé dans mon code* ; cela ne peut pas vouloir dire *ne me dis pas que tu es mal
configuré*. » La méthode contourne donc bien `-Aumlg.warnings=none` — mais elle émet un
`Diagnostic.Kind.WARNING`, que `-nowarn` supprime :

```
sans -nowarn : WARNING — -Aumlg.rules=...: no such file...
avec -nowarn : (aucun diagnostic, compilation réussie)
```

Or `README.md:73-77` explique lui-même que Maven passe `-nowarn` par défaut. **Le cas courant est
donc le cas cassé** : un projet sans `<showWarnings>true</showWarnings>` qui s'est trompé de
chemin obtient un processeur à moitié aveugle, sans un mot — exactement ce que le commentaire dit
vouloir empêcher.

**Correction.** `Diagnostic.Kind.MANDATORY_WARNING`, et là seulement. Un mot à changer.

---

## 16. Le projet de référence n'exerce pas la moitié « compilation » de la preuve

**Gravité : Conception.**

`Keyboardd/pom.xml` — `Keyboardd/src/main/resources/umlg.json`

`Keyboardd` est le projet versé au dépôt pour porter la preuve de bout en bout. Son pom déclare
bien la dépendance et `showWarnings`, mais **ni `annotationProcessorPaths`, ni
`-Aumlg.rules`** — alors que `README.md:82-97` prescrit les deux comme le pom complet à écrire.

Deux conséquences. Le processeur n'est découvert que par `META-INF/services`, la forme dont javac
annonce la disparition et que le README dit justement de ne plus employer. Et surtout : le
fichier de règles déclare `"strategy": "none"`, que le processeur ne voit pas — il se tait donc
sur tout ce qui en dépend, dans le projet même où ces contrôles seraient les plus utiles
(sous `strategy: none`, aucun membre ne paraît sans demande explicite).

**Correction.** Aligner `Keyboardd/pom.xml` sur le README. C'est aussi le meilleur test de ce
même README.

---

# III. Conception

## 17. Trois réglages du `Builder` se contredisent en silence

**Gravité : Conception.** `UMLGenerator.java:126`, `:310`, `:332`, `:370`, `:423`

`generate()` tranche par `if (outputDirectory != null)` et rien en amont ne vérifie la cohérence
de ce qui a été demandé :

- `.outputFile("archi.png").outputDirectory("docs")` : `archi.png` n'est jamais écrit, aucune
  erreur ;
- `.outputFile("a.png").outputFormat("svg")` : `outputFormat` n'est lu que dans la branche
  dossier — l'utilisateur obtient du PNG après avoir demandé du SVG ;
- un projet qui déclare des vues et sort en fichier unique **les perd toutes**, sans un mot.

La Javadoc du Builder promet pourtant l'inverse (`:266` : « une erreur de chemin ou d'extension se
signale dès l'appel fautif »), et `sourceDirectory` et `outputFile` valident bien, eux, dès
l'appel.

**Correction.** Deux `checkState` dans `build()`, et un avertissement quand `project.views()`
n'est pas vide en mode fichier unique.

## 18. `write(project, outputFileName)` ne valide rien et laisse un fichier vide derrière lui

**Gravité : Conception.** `rendering/PlantUmlWriter.java:254`, `:289`

Contrairement à `writeViews:90`, la méthode ne fait pas `checkArgument(supporte(...))` : un nom
sans point lève `StringIndexOutOfBoundsException: Range [0, -1)`, message incompréhensible pour
un appelant de bibliothèque. Et le `FileOutputStream` est ouvert **avant** `verifierAcceptation`
et `verifierMoteurDeDessin` : toute panne postérieure laisse un fichier de zéro octet à sa place
— vérifié.

## 19. `UmlField` et `UmlMethod` sont les deux seuls records du modèle à ne pas tenir leurs invariants

**Gravité : Conception.** `model/UmlField.java:44` — `model/UmlMethod.java:46`

Leurs constructeurs compacts ne vérifient que la multiplicité et les contraintes. `name`, `type`,
`visibility` ne sont jamais contrôlés :

```
UmlField[name=null, type=null, cardinality=, visibility=null, ...]   ← construit sans broncher
UmlField[name=   , type=  , visibility=?, ...]                       ← idem
```

Le rendu écrit alors `- null : null` dans la boîte. C'est en contradiction avec ce que la zone
promet en trois endroits (`model/package-info.java:11`, `umlg/package-info.java:117`) et avec le
voisin immédiat `UmlParameter.java:44`, qui refuse bien un type vide. Le commentaire du test
`model/UmlModelTest.java:201` (« `UmlField` n'a aucun constructeur compact ») est périmé — ce qui
explique probablement l'oubli.

## 20. Deux mots pour une même notion, dans la partie qu'on ne pourra plus renommer

**Gravité : Conception.** `annotation/UMLAssociation.java:76`, `model/UmlField.java:29`, contre
`annotation/UMLMultiplicity.java:47` et `model/Multiplicites.java:30`

Le même concept porte deux noms dans le vocabulaire **public** : `cardinality`
(`sourceCardinality`, `targetCardinality`, `UmlField.cardinality`, `UmlMethod.cardinality`) et
`multiplicity` (`@UMLMultiplicity`, `Multiplicites`). Ce ne sont pas deux notions voisines : les
quatre `cardinality` passent par `Multiplicites.valider`, et la Javadoc de chacun traduit
systématiquement le nom en l'autre mot. En UML, cardinalité et multiplicité sont deux termes que
la norme distingue.

C'est le doublon le plus coûteux de l'API, parce qu'il touche des **noms de paramètres
d'annotation et des composantes de record**, gelés dès la première publication. Le mot à retenir
est celui de l'annotation dédiée et de la classe de grammaire, qui sont déjà justes.

## 21. Les avertissements sont hors du rapport, et passent par un global mutable

**Gravité : Conception.** `Avertissements.java:34`, `:57` — `RapportDeGeneration.java:31`

`generate()` rend un rapport exploitable sur trois axes, mais les avertissements — qui portent
l'information la plus actionnable de l'outil — n'y figurent pas. Pour les capter, l'appelant doit
encadrer son appel de `versLeCanal(...)` / `retablirLeDefaut()`, un état global par fil que la
Javadoc reconnaît elle-même comme un piège. On ne peut donc pas écrire
`if (rapport.avertissements().isEmpty())`. Et `Avertissements.dire(String)` est exporté vers
l'utilisateur, qui n'a aucune raison de l'appeler.

**Correction.** Une composante `avertissements` dans `RapportDeGeneration`, remplie par
`generate()` qui pose et retire le canal lui-même ; `versLeCanal` conservée pour le relais temps
réel du plugin Maven, sous forme d'`AutoCloseable`.

## 22. `"order": "declaration"` n'atteint ni les liens ni les bulles

**Gravité : Conception.** *Trouvé indépendamment par deux lectures.*
`extraction/MembresOrdonnes.java:48`, `:67` — `extraction/relation/RelationExtractor.java:11`

Les surcharges à un argument fixent `Regles.aucune()`, donc l'ordre alphabétique quoi que le
projet ait demandé. Leurs seuls appelants sont `AssociationExtractor` et `NoteExtractor` :
**toutes** les utilisations hors `MemberExtractor` ignorent la règle. Un projet obtient donc des
attributs rangés dans l'ordre du code et des traits rangés par alphabet. La cause structurelle
est que `RelationExtractor.extraire(Class<?>)` n'a pas de paramètre `Regles`.

## 23. Une structure commune, réelle, jamais nommée

**Gravité : Conception.** `processing/` — dix vérificateurs

Deux blocs sont copiés à l'identique, commentaires compris :

- **« ce membre sera-t-il dessiné ? »** — quatre copies exactes (`VerificateurDerive:77`,
  `VerificateurMultiplicite:96`, `VerificateurNote:102`, `VerificateurContrainte:83`) plus une
  cinquième, divergente, dans `VerificateurMembre:160`. **C'est dans la copie qui diffère que
  s'est logé le défaut 12.**
- **« la classe porteuse n'est pas sur le diagramme »** — écrit neuf fois ; trois messages sont
  identiques au caractère près, les six autres ne diffèrent que par la proposition finale.

**Correction.** Deux méthodes, pas un cadre : `Strategie.membreAffiche(...)` rendant un tri-état,
et `Rapporteur.exigerBoite(...)` dont seule la clause de conséquence reste au site d'appel. Tout
le reste des dix vérificateurs est légitimement spécifique — la ressemblance y serait fausse.

## 24. Les messages sont recopiés à la main entre `processing/` et `extraction/`

**Gravité : Conception.** `processing/package-info.java:29`

Le paquetage promet : « pour qu'ils ne divergent jamais, ce paquetage ne réécrit aucune règle ».
`Multiplicites` et `ColorCodec` ont été nommées exactement pour cela. Trois endroits y échappent,
et **ont déjà divergé** : `Cible.java:51` contre `CibleDeduite.java:50` (la version du processeur
ne nomme pas le type fautif) ; `VerificateurClasseDAssociation.java:57` contre
`AssociationClassExtractor.java:26` (qui contrôlent en outre dans un ordre différent) ;
`VerificateurContrainte.java:60` contre `model/Contraintes.java:32`. Six autres paires sont
encore identiques — c'est la définition d'une duplication en sursis.

## 25. Le message « classe illisible » ment quand la classe s'est chargée

**Gravité : Conception.** `extraction/ProjectScanner.java:154-185`

Le `try` couvre `loadClass` **et** toute l'extraction. Quand une `LinkageError` survient pendant
l'extraction, l'utilisateur lit « Whether they carried `@UMLClass` cannot be known — the
annotation is only readable on a loaded class », ce qui est faux ligne à ligne : la classe s'est
chargée, l'annotation a été lue, elle y était, et sa boîte a été perdue.

## 26. Le constructeur de `Regles` prend quinze arguments positionnels

**Gravité : Conception.** `extraction/rules/Regles.java:217`

Neuf booléens de suite, dont six `false` alignés dans `AUCUNE`. Les deux appels sont **justes
aujourd'hui** — vérifié argument par argument. Le défaut est l'absence de tout filet : le
compilateur ne distinguera jamais `ordreDeclaration` de `multiplicitesDeduites`. Aggravé par
l'inversion `!montrerMembresVides` et par le couple `strategie == null ? NONE : strategie,
strategie != null` qui encode deux informations à partir d'un `null`.

## 27. Trois points d'harmonisation dans la lecture des règles

**Gravité : Conception.**

- **`strategy` tolère la casse, les quatre autres mots-clés énumérés non**
  (`LecteurDeRegles.java:420`) : `"PUBLIC"` passe, `"Declaration"`, `"Left-To-Right"`,
  `"Visibility"` et `"Brackets"` sont refusés. Aucun test ne fixe cette tolérance, qui ressemble
  à un effet de bord de `valueOf`.
- **`vides()` répond « aucune règle » à un fichier qui écrit un défaut** (`Regles.java:440`), ce
  que `booleen` autorise pourtant explicitement ; et la méthode **n'a aucun appelant hors des
  tests**.
- **`hide` accepte un doublon là où `omit` le refuse** (`:446` contre `:512`), donnant deux
  avertissements de règle morte pour une seule ligne à corriger.

## 28. `UmlProject` ne garantit pas l'unicité de ce que le modèle appelle « l'identité »

**Gravité : Conception.** `model/UmlProject.java:80` — `model/UmlEntity.java:20`

`UmlEntity` documente `qualifiedName` comme « l'identité de l'entité ». Mais `entities` est un
`ImmutableSet` dédoublonné sur les quatorze composantes : deux entités de même nom qualifié ne
différant que par une couleur cohabitent. La faute n'est découverte qu'au rendu, deux couches
plus loin — et pas du tout en usage purement programmatique.

## 29. L'espacement ignore le nom des liens, contrairement à ce qui est annoncé

**Gravité : Conception.** `rendering/element/RelationRenderer.java:160` — `UmlStyle.java:22`

`UmlStyle` promet « l'espacement, calculé sur la plus longue étiquette du diagramme ». En
réalité, seules les extrémités (rôle + multiplicité) sont mesurées : le `label()` du lien — le
texte le plus long d'un trait dans la pratique — ne compte jamais. La largeur est de surcroît
mesurée sur le texte **échappé**, où un guillemet vaut cinq caractères.

---

# IV. Peaufinage

**Code mort.**
`model/UmlEntity.java:235` — `private boolean declared;`, ni écrit ni lu (la passe de
simplification croyait l'avoir retiré avec le reste).
`rendering/element/ClassBoxRenderer.java:47` — la surcharge `boite(entity, alias)` n'a aucun
appelant.
`extraction/ConstructeurDeVues.java:299` — `enchainer` reçoit un paramètre `presentes` qu'il
n'ouvre jamais.
`processing/Rapporteur.java:46` — `avertissement(String)` sans élément n'est appelée nulle part
(59 appels examinés).
`extraction/PackageExtractor.java:28` — un paramètre `Regles` `checkNotNull`é puis jamais employé,
vestige de la règle `group package` retirée.

**Répétitions.**
Trois constructions de `UmlField` quasi identiques dans `MemberExtractor` (`:230`, `:356`,
`:534`) ; `emplacement(Method)` écrit trois fois sans exister comme méthode, et pas exactement de
la même façon. La garde « conteneur à un seul paramètre » écrite trois fois dans `TypeAffiche`
(`:108`, `:139`, `:157`). La notation `{contrainte}` implémentée trois fois
(`ClassBoxRenderer:130`, `Extremite:42`, `NoteRenderer:54`). La reconstruction à quatorze
arguments d'`UmlEntity` écrite deux fois dans `ConstructeurDeVues` (`:268`, `:334`) — une méthode
`UmlEntity.avecRelations(...)` les supprimerait toutes deux.

**Rendu et types.**
Devant une multiplicité, un tableau garde ses crochets (`String[] [1..*]`, qui se lit « un ou
plusieurs tableaux de chaînes ») et un joker sa borne (`? extends CharSequence [0..*]`) —
`TypeAffiche.java:108`. Un paramètre variadique s'écrit `Object[]` alors que
`Parameter.isVarArgs()` répond à la question — `MemberExtractor.java:412`.

**Diagnostics.**
Un diagnostic sur une annotation **répétée** perd sa position (javac synthétise le conteneur, qui
n'a pas de position source — vérifié irrécupérable par l'API) : sur cinq `@UMLNote` dont une
fautive, le message pointe la déclaration de classe et ne dit pas laquelle
(`processing/Rapporteur.java:129`). Le remède est de le dire dans le texte (« the 2nd `@UMLNote`
on X »).
`-Aumlg.warnings` écrit **sans valeur** passe en silence — mot pour mot la faute de frappe que le
commentaire dit avoir corrigée (`UmlgProcessor.java:115`). Un chemin relatif est résolu contre le
répertoire de travail de javac, et le message ne dit pas où il a cherché ; le README devrait
conseiller `${project.basedir}/...`.
L'avertissement de non-fusion des associations réciproques n'énonce qu'une de ses deux causes, et
son remède est inapplicable à l'autre (`AssociationsReciproques.java:90`).

**Performance.**
Le `.class` est relu et réanalysé **trois fois par classe** sous `"order": "declaration"`
(`OrdreDuFichierClasse.java:35`) — et l'avertissement « ordre perdu » est donc dit trois fois pour
la même classe. `Proportions.entete()` fait `Files.readAllBytes` avant de tronquer à 2048 octets,
alors que son commentaire affirme précisément le contraire (`Proportions.java:84`).

**Divers.**
Un BOM UTF-8 produit un message qui renvoie à la convention `_`, sans rapport
(`Regles.java:385`). L'espace ordinaire est refusé dans un motif, mais la tabulation et l'espace
insécable passent — or l'insécable est ce que produit un copier-coller depuis un traitement de
texte (`LecteurDeRegles.java:453`). Aucune des seize annotations n'est `@Documented`, pour une
bibliothèque dont le métier est de documenter. `Multiplicites` promet d'être interrogeable
(`:26`) et n'offre qu'un `valider` qui rend `void` et lève. `fichierDesIgnorees` est le seul
`null` de toute la zone exportée. `UMLDirection` sert à deux choses et n'en documente qu'une.
`AliasRegistry` déclare un champ au milieu du fichier et qualifie `Avertissements` en toutes
lettres, seul endroit de sa zone à le faire.

**Langue.** Une seule chaîne utilisateur en français dans toute la bibliothèque —
`"(défaut)"`, `ConstructeurDeVues.java:135` — au milieu de 23 fichiers dont les messages sont en
anglais. Et ce libellé devient un nom de vue, donc un nom de fichier. Par ailleurs seize méthodes
publiques portent un nom français (`aUnTitre`, `couleursConnues`, `nomDeFichier`, `resume`,
`surClasse`…) au milieu d'une surface exportée par ailleurs anglaise, parfois à l'intérieur d'un
même type : `UmlDiagram` offre `title()`, `direction()`, `defaut()` et `aUnTitre()`. La règle à
poser : **anglais sur la surface exportée, français partout ailleurs** — c'est déjà le cas des
messages et du vocabulaire d'annotation.

**Documentation périmée.** `extraction/rules/package-info.java:4` annonce « deux classes » pour un
paquetage qui en compte cinq, et range parmi les règles « à venir » deux natures déjà arrivées.

---

# V. Le pom et les tests

## Le pom

- **Surefire est rétrogradé sans un mot.** Le pom parent gère `maven-surefire-plugin` en
  **3.5.4** ; le projet écrit `<version>3.2.5</version>`, ce qui annule le parent et redescend de
  dix-huit mois de correctifs. La ligne est inutile : la configuration seule s'ajoute à celle du
  parent. `pom.xml:147`
- **Le commentaire du compilateur accuse le mauvais coupable.** « Le pom parent épingle la
  version 3.1 (2013) » — vérifié : le parent **ne déclare pas** `maven-compiler-plugin`. La 3.1
  vient du super-POM de Maven. Le `README.md:63` dit la chose correctement : c'est le pom qu'il
  faut aligner sur le README. `pom.xml:78`
- Le parent pose `maven.compiler.release=21`, qui l'emporte sur les `source`/`target` du projet —
  inertes, donc, sans conséquence ici mais trompeurs sur ce qui pilote la compilation.
- **Cinq comptes cités dans les commentaires sont déjà faux** : « les 89 `assertCompile` » (86),
  « les 23 rendus SVG » (21), « les quatre-vingt-neuf tests qui passent par lui » (132), « les
  neuf annotations de la bibliothèque » (9 sur 11 non-conteneurs), « 96,9 % des instructions »
  (96,31 %). Un compte exact dans un commentaire est une dette à échéance certaine.

## Les tests

La suite est **nettement meilleure que sa couverture ne le laisse craindre** : les assertions
portent sur la sortie et non sur l'exécution, les pièges déjà rencontrés sont documentés là où ils
ont mordu, rien ne dépend de l'ordre ni d'un état partagé, et 17,6 s suffisent pour 965 cas
malgré ~90 invocations réelles de javac et 21 rendus SVG. Cinq manques ciblés, tout de même :

- **`Avertissements.versLeCanal` / `retablirLeDefaut` ne sont appelés par aucun test.** Les deux
  seuls endroits qui relisent un avertissement détournent `System.err` à la place —
  `PlantUmlWriterTest.java:69`, `PlantUmlPackageTest.java:116`. Double conséquence : la suite ne
  peut pas être parallélisée, et supprimer le `CANAL.remove()` de `retablirLeDefaut()` ne ferait
  échouer aucun test. C'est aussi ce qui explique les 68,8 % d'`Avertissements`, la classe la
  moins couverte.
- **`Builder.classpath(...)` et `Builder.keepPrevious(...)` ne sont appelés par aucun test** —
  vérifié par recherche exhaustive. Or `classpath` est **l'API dont se sert le plugin Maven** :
  personne ne vérifie qu'une classe dont le parent vit dans un jar tiers est chargeable. Le
  comportement de `keepPrevious` est testé un étage plus bas, mais son câblage
  `Builder → generate()` ne l'est pas : inverser le booléen ne casserait rien.
- **`UMLGeneratorTest` est le fichier le plus lent de la suite et le seul aux assertions
  tautologiques** : `assertNotNull(generator)` sur l'objet qu'on vient de créer (`:23`, `:47`,
  `:55`) ; `styleExplicite_estAccepte` ne vérifie **jamais** que le style est appliqué. 5,61 s
  sur 17,6, soit un tiers du temps total, pour quatre générations complètes de
  `target/test-classes`. Les noms (`testConfigurationValide`) ne disent pas ce qui est vérifié, à
  rebours de la convention `sujet_verbeComplement` que tient tout le reste de la suite. Même
  reproche à `ProjectScannerTest`.
- **La branche non couverte qui compte** : `OrdreDuFichierClasse`, 56,8 % de branches, la pire de
  la bibliothèque. Les `case 15` (`MethodHandle`) et `case 5, 6` (`Long`/`Double`, qui occupent
  deux entrées) du réservoir de constantes ne sont **jamais** exécutés — or toute classe portant
  une lambda produit les premiers, et tout littéral `long` les seconds. Un `sauter()` mal
  dimensionné là désordonnerait silencieusement les membres sur un projet réel. **Une seule
  classe cobaye portant un `long` et une lambda couvrirait les deux.**
- **La suite exige Graphviz et ne le dit pas.** Les 21 appels de `PlantUmlAssert.svg()`
  court-circuitent le contrôle que `PlantUmlWriter:343` fait pourtant soigneusement ; sur une
  machine sans `dot`, ils échouent sur des assertions de contenu incompréhensibles. Rien dans le
  README ne mentionne cette dépendance de construction.

Plus, en vrac : `UmlAssociationTest:20` et `:47` sont les deux seuls tests de la suite **sans
aucune assertion** ; le harnais javac est recopié dans quatre fichiers et l'en-tête de cobaye dans
six ; sept assertions sont écrites à l'envers (`assertTrue(a.equals(b))` au lieu d'`assertEquals`,
`assertThrows(Exception.class, ...)` qui accepte une `NullPointerException`) ;
`PlantUmlVuesEcritesTest` écrit quinze fois le même appel en 256 lignes ; deux noms de test
comportent une faute de frappe (`neCassSeQueLaPaire`, `bascculeEnPng`) ; et le commentaire de
`UmlgProcessorTest:441` verrouille un choix de conception qui n'existe plus — `UMLAssociation.java:43`
porte aujourd'hui `default void.class`, et le test passe pour une raison sans rapport avec ce
qu'il prétend garder.

Enfin, `ProjetDeReference` annonce couvrir « les neuf annotations de la bibliothèque » : `@UMLPackage`
et `@UMLAssociationClass` n'y sont pas. Le diagramme de référence — seul test qui fige la sortie
dans son ensemble — ne protège donc ni les rectangles de groupe ni les classes d'association.

---

# VI. Ordre d'implémentation proposé

L'ordre suit l'effet sur un utilisateur réel, pas la difficulté.

**Premier lot — un mot à changer, un effet large.**
15 (`MANDATORY_WARNING`), 5 (`TypeNotPresentException` dans la clause de capture), 6 (l'union des
deux ensembles dans `NoteExtractor`), 10 (`"**"`), 11 (la convention `_` dans `lireTypes`).
Cinq corrections d'une à trois lignes, chacune avec son test.

**Deuxième lot — les pertes silencieuses.**
1 (collision de vues, dans `ConstructeurDeVues` + garde-fou dans `writeViews`), 2 (l'effacement
de type), 7 (le balisage de début de ligne), 8 (le lien multiligne), 9 (l'échappement qui vide).

**Troisième lot — la hiérarchie des décisions, en une fois.**
3, 4, 12 et 23 sont **le même travail** : nommer la décision « ce membre entre-t-il, et sous quel
nom ? » comme une fonction unique que les quatre points d'entrée de l'extraction appellent, et
lui donner son pendant côté processeur (`Strategie.membreAffiche`). Faire les quatre séparément
serait quatre fois plus de travail pour un résultat moins sûr. C'est le chantier structurant de
cet audit.

**Quatrième lot — l'invariant processeur/génération.**
13 (`TYPEVAR`), 14 (trancher les deux contrôles), 24 (sortir les trois messages là où la règle
vit déjà — le geste déjà réussi deux fois avec `Multiplicites` et `ColorCodec`), 16 (aligner le
pom de Keyboardd sur le README, ce qui éprouve les deux).

**Cinquième lot — l'API, tant qu'elle n'est pas publiée.**
20 (`cardinality` → `multiplicity` : à faire **maintenant**, ce sont des noms de paramètres
d'annotation et des composantes de record), 19 (les invariants de `UmlField`/`UmlMethod`), 17 (le
Builder), 21 (les avertissements dans le rapport), 28, 18. Puis la règle de langue, et
`@Documented`.

**Sixième lot — le reste.**
22, 25, 26, 27, 29, le pom, les tests, et le peaufinage de la section IV. La couverture de
`OrdreDuFichierClasse` (une classe cobaye avec un `long` et une lambda) mérite d'être remontée
dans ce lot : c'est trois lignes de test pour la branche la plus risquée de la bibliothèque.

---

*Rapport établi le 11 août 2026. Sept lectures parallèles, chacune bornée à sa zone, plus deux
points transversaux. La collision de noms de fichiers de vues (défaut 1) a été trouvée
indépendamment par trois d'entre elles, et la fuite de `"order": "declaration"` (défaut 22) par
deux — les autres trouvailles sont uniques à une lecture, ce qui invite à les relire avant
correction plutôt qu'à les tenir pour acquises. Toutes ont été revérifiées dans le code avant
d'entrer dans ce rapport.*
