# TryCity Content

Fabric-Mod (Minecraft 26.1.2, nur Client) mit Extras für TryCity. Der Server sagt der Mod
per Paket, was sie zeigen soll – ohne Mod merkt niemand etwas.

## Bauen

```bash
JAVA_HOME="C:/Program Files/Amazon Corretto/jdk25.0.4_7" ./gradlew build --offline
```

Ergebnis: `build/libs/trycity-content-1.0.0.jar`. `build` kopiert es danach automatisch
(Aufgabe `installNoRisk`) nach
`C:\Users\leonm\AppData\Roaming\norisk\NoRiskClientV3\data\profiles\noriskclient\new\mods` –
ältere `trycity-content-*.jar` dort werden vorher gelöscht. Fabric API muss im Profil sein.

## Was drin ist

**Morph (`trycity:morph`)** – wer mit `/morph` verwandelt ist, sieht sich selbst als das Mob:

- in Third Person (F5) und im Inventar, in echter Größe (das Scale-Attribut, mit dem
  der Server den Spieler auf Mob-Höhe bringt, zählt für die Figur nicht)
- in der Ego-Sicht als Hand des Mobs (Arm, sonst Vorderbein; beim Geist keine Hand)
- mit eigener Rüstung und den Items in der Hand – Mobs, die Rüstung zeigen können
  (Zombie, Skelett, Piglin …), tragen sie; der Geist nicht (sein Helm ist das Modell)
- mit `/morph name an` mit dem eigenen Namen samt Unterzeile darüber, wie ihn die
  anderen sehen (im Inventar ohne)
- das Sichtfeld zoomt nicht mit, wenn der Morph schneller oder langsamer macht
- die Kamera in F5 steht so weit weg wie beim echten Mob (mindestens 4 Blöcke) – der
  geschrumpfte Spieler-Scale zieht sie nicht mehr in die Figur hinein
- mit denselben Posen, die die anderen sehen (`MorphPoses`): Bogen spannen, Armbrust laden,
  Arme hoch nach Schlag/Schuss, Sitzen beim Schleichen, Golem-Schlag, Creeper bläht sich
  auf … – vom eigenen Spieler abgelesen; die Spezial-Animation (Schleichen + F) meldet der
  Server in `trycity:morph_event`. Der Tick des Mobs läuft mit (nur in F5/Inventar).
- Schritte klingen wie das Mob, im Takt des Mobs (Schildkröte trippelt); Fledermaus,
  Fische, Silberfisch & Co. laufen still
- Hitbox und Augenhöhe wie das Mob – nie enger als der Server erlaubt, sonst würde
  man zurückgesetzt

Der Server (TryusCloudGlobalServer, `MorphManager`) schickt bei jeder Änderung:

```json
{"v":1, "active":true, "entity":"minecraft:zombie", "baby":false, "name":true}
{"v":1, "active":true, "entity":"minecraft:armor_stand", "custom":"tryus:geist"}
{"v":1, "active":false}
```

## Neues hinzufügen

**Ein ganz neues Extra** (z. B. `trycity:hud`):

1. Klasse, die `ContentModule` umsetzt; im `init()`:
   `TryCityChannel.listen("hud", json -> { ... });`
2. In `content/ContentModules.ALL` eintragen.
3. Auf dem Server: `TryCityModChannel.send(player, "hud", json)`; für den Anfangsstand
   `TryCityModChannel.onModReady("hud", player -> ...)`.

Das Paket ist JSON – neue Felder brechen alte Mods nicht, sie lesen einfach, was sie kennen.

**Ein neues eigenes Morph-Aussehen** (wie der Geist):

1. Klasse, die `MorphLook` umsetzt (`create` baut die Figur, optional `sync`,
   `alwaysInvisible`, `mobArm`) – Vorbild `GhostLook`.
2. In `MorphModule.init()`: `MorphLooks.register("tryus:name", new NameLook());`
3. Auf dem Server einen `CustomMorph` mit demselben Schlüssel anlegen.

Normale Mobs brauchen nichts – die Mod baut sie aus dem Entity-Typ.

## Testen

```bash
JAVA_HOME="C:/Program Files/Amazon Corretto/jdk25.0.4_7" ./gradlew runClient
```

Im Log steht bei jeder Änderung `[TryCity] Morph: …`.
