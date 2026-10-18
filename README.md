# Dark Entity (NeoForge 1.21.1)

Чёрный глитч-босс на GeckoLib + Veil.

## Зависимости (нужны в папке mods для игры)
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.7.x для NeoForge 1.21.1
- [Veil](https://modrinth.com/mod/veil) 4.4.x для NeoForge 1.21.1 (только на клиенте, нужен для глитч-шейдера)

## Запуск из исходников
- Java 21.
- `gradle runClient` (или `./gradlew runClient`, если добавишь wrapper).
- `gradle build`: jar в `build/libs/`.
- В игре: яйцо призыва во вкладке Spawn Eggs или `/summon darkentity:dark_entity`.

## Способности
1. Световые столбы: белые кольца-предупреждения, затем толстый луч с уроном и взрывом.
2. Бирюзовая сфера с шлейфом, со 2-й фазы самонаводится.
3. Красный лазер (со 2-й фазы): красное небо, глитч-шейдер, разрушение блоков.
4. Глитч-телепорт за спину.
5. Катаклизм (3-я фаза): зарядка, белая вспышка, серия огромных взрывов, тряска камеры.

Разрушение блоков зависит от gamerule `mobGriefing`.

## Файлы эффектов
- Модель/анимации: `assets/darkentity/geo`, `assets/darkentity/animations`
- Veil-шейдер: `assets/darkentity/pinwheel/post/glitch.json` и `pinwheel/shaders/program/glitch.*`
- Лучи и тряска: `client/ClientFx.java`
