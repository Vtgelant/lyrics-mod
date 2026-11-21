# Что нужно для работы (полный список)

Мод клиентский, серверу ничего ставить не надо. Других модов, кроме Fabric API, он не требует.

| Что | Версия | Где взять |
|---|---|---|
| Minecraft Java Edition | 1.16.5 | лаунчер |
| Fabric Loader | 0.14.21 или новее (стабильный) | https://fabricmc.net/use/installer/ |
| Fabric API | 0.42.0+1.16 (`fabric-api-0.42.0+1.16.jar`) | https://modrinth.com/mod/fabric-api/version/IQ3UGSc2 или https://www.curseforge.com/minecraft/mc-mods/fabric-api/files/3516413 |
| Lyrics (этот мод) | 1.0.0 | собирается из этого проекта: `build/libs/lyrics-fabric-1.0.0.jar` |
| Java для запуска игры | 8 или новее (лаунчер Minecraft ставит свою) | — |
| Java для сборки мода | JDK 17 | https://adoptium.net/ |

## Установка
1. Запусти установщик Fabric, выбери Minecraft 1.16.5 и установи Loader.
2. Открой `%appdata%\.minecraft\mods` (Linux: `~/.minecraft/mods`, macOS: `~/Library/Application Support/minecraft/mods`).
3. Положи туда два файла: `fabric-api-0.42.0+1.16.jar` и `lyrics-fabric-1.0.0.jar`.
4. Запусти профиль Fabric 1.16.5, включи музыку на ПК.

Вместо лаунчера Mojang можно использовать Prism Launcher или Modrinth App: создай экземпляр 1.16.5 с Fabric и перетащи те же два jar в папку модов.

## Для твоей системы
- **Windows 10/11**: ничего дополнительно, нужен встроенный Windows PowerShell 5.1.
- **Linux**: поставь `playerctl` (`sudo apt install playerctl`).
- **macOS**: ничего, поддерживаются Spotify и Music.

## Если мод не видит музыку
- Spotify desktop даёт самое точное время; браузеры иногда отдают позицию с задержкой, тогда подстрой сдвиг в настройках.
- В Chrome/Edge/Brave должна быть включена поддержка медиаклавиш (по умолчанию включена).
