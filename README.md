# IX Skins for Fabric

The command requires prem lvl 2 (OP) on the server or cheats enabled on the LAN host.

```mcfunction
/ixskins set <player> player <username>
/ixskins set <player> rawurl <url>
/ixskins clear <player>
/ixskins sync
```

Exemples:

```mcfunction
/ixskins set @s player Notch
/ixskins set @s rawurl https://raw.example.com/devix7/mcSkins/skin.png
```

## Build

Requirements Java 21 and Gradle 8.14+.

```bash
gradle build
```

Output:

```text
build/libs/ixskins-fabric-*.jar
```

Gradle Wrapper:

```bash
gradle wrapper --gradle-version 8.14.3
./gradlew build
```

## Structure

```text
src/main/java/dev/devix7/ixskins/IxSkinsCommands.java       --->        commands
src/main/java/dev/devix7/ixskins/ServerSkinState.java       --->        save and sync
src/main/java/dev/devix7/ixskins/IxSkinsSyncPayload.java    --->        network payload
src/client/java/dev/devix7/ixskins/client/ClientSkinRegistry.java
src/client/java/dev/devix7/ixskins/mixin/PlayerListEntryMixin.java
```


## License MIT (c) 2026 DEVIX7