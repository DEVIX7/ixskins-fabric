# IX Skins для Fabric 1.21.8

Мод добавляет серверную команду `/ixskins`, которая меняет видимый скин игрока у всех клиентов, где установлен этот же мод.
Подходит для обычного Fabric-сервера и для LAN-мира, открытого хостом через "Открыть для сети".

## Установка

1. Поставь Fabric Loader для Minecraft 1.21.8.
2. Положи `ixskins-fabric-1.0.2.jar` и Fabric API в `mods` у хоста/сервера.
3. Положи этот же jar и Fabric API в `mods` у каждого клиента, который должен видеть замененные скины.

Игрок без клиентской части мода не увидит замененный скин, потому что сам рендер скина меняется на стороне клиента.

## Команды

```mcfunction
/ixskins set <player> player <username>
/ixskins set <player> rawurl <url>
/ixskins clear <player>
/ixskins sync
```

Примеры:

```mcfunction
/ixskins set @s player _ix7
/ixskins set @p rawurl https://raw.githubusercontent.com/devix7/template/main/_ix7skin.png
/ixskins set DevIx7 rawurl raw.githubusercontent.com/devix7/template/main/_ix7skin.png
```

После установки скина в чат пишется системное сообщение с префиксом мода:

```text
[IXSKINS] Steve set skin via nickname _ix7
[IXSKINS] Alex set skin via url https://raw.githubusercontent.com/devix7/template/main/_ix7skin.png
```

## Как это работает

- `player <username>` берет URL скина через Mojang API и применяет его к выбранному игроку.
- `rawurl <url>` скачивает PNG напрямую. Если протокол не указан, мод добавит `https://`.
- PNG должен быть обычным Minecraft-скином `64x64` или старым `64x32`. Старые `64x32` скины автоматически конвертируются в новый `64x64` формат перед загрузкой текстуры.
- Данные сохраняются на стороне хоста/сервера в `config/ixskins/skins.json`.
- При входе игрока и после каждой команды сервер отправляет клиентам полный список замен через `CustomPayload` / `PayloadTypeRegistry.playS2C()`.
- На клиенте mixin перехватывает `PlayerListEntry#getSkinTextures()` и возвращает загруженную замену скина.

## Права

Команда требует уровень прав 2, то есть OP на сервере или включенные читы у LAN-хоста.

## Сборка

Нужны Java 21 и Gradle 8.14+.

```bash
gradle build
```

Готовый jar появится в:

```text
build/libs/ixskins-fabric-1.0.2.jar
```

Чтобы добавить Gradle Wrapper внутри проекта:

```bash
gradle wrapper --gradle-version 8.14.3
./gradlew build
```

## Структура

```text
src/main/java/dev/devix7/ixskins/IxSkinsCommands.java       серверная команда
src/main/java/dev/devix7/ixskins/ServerSkinState.java       сохранение и синхронизация
src/main/java/dev/devix7/ixskins/IxSkinsSyncPayload.java    сетевой payload
src/client/java/dev/devix7/ixskins/client/ClientSkinRegistry.java
src/client/java/dev/devix7/ixskins/mixin/PlayerListEntryMixin.java
```
