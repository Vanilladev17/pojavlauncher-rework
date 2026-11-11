# PojavLauncher Rework

Форк [PojavLauncher](https://github.com/TeamPojavLauncher/PojavLauncher) с нормальной работой с файлами игры: импорт модов в пару кликов, изоляция версий и встроенный проводник — без рута, adb и сторонних файловых менеджеров.

**Minecraft: Java Edition на Android. Как у себя дома.**

[![Скачать APK](https://img.shields.io/badge/Скачать-APK-e0a458?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Vanilladev17/pojavlauncher-rework/releases/latest/download/PojavLauncher-Rework.apk)
[![Сайт](https://img.shields.io/badge/Сайт-Landing-e0a458?style=for-the-badge)](https://vanilladev17.github.io/pojavlauncher-rework/)

---

## Что добавлено поверх оригинала

### 📥 Импорт модов в пару кликов
Скачали мод, ресурспак, карту или шейдер в браузере? Нажмите **«Открыть с помощью» → PojavLauncher Rework**, выберите версию игры и папку назначения — лаунчер сам скопирует файл на место.

- шаг 1 — выбор установленной версии (список `.minecraft/versions/*`, у которых есть `<name>.json`) или папки профиля;
- шаг 2 — выбор папки: `mods`, `resourcepacks`, `shaderpacks`, `saves` (как файл или с распаковкой), `config`, корень профиля;
- поддержаны `VIEW`, `SEND`, `SEND_MULTIPLE`;
- имена не перезаписываются: при совпадении добавляется `(1)`;
- распаковка zip-архивов с защитой от zip-slip.

### 🗂 Изоляция версий
У каждой версии — своя игровая папка `.minecraft/versions/<versionId>/` со своими `mods`, `resourcepacks`, `shaderpacks`, `saves`, `config`. Forge/Fabric видят только моды своей версии, сборки не конфликтуют между собой.

- включается и выключается галочкой в редакторе профиля;
- при первом включении содержимое профиля аккуратно копируется в новую папку (только в пустые места);
- профили с включёнными «общими данными» (shared data) изоляцию не используют.

### 🎛 Включение и выключение модов
Во встроенном проводнике мод выключается одним тапом: файл переименовывается в `.jar.disabled` и игра его не загружает. Ещё тап — мод снова включён. Удалять и качать заново не нужно.

### 📁 Проводник файлов игры
Отдельный экран с файлами игры: просмотр, создание папок, переименование, удаление, импорт. Работает на собственной папке приложения, поэтому не упирается в Scoped Storage на Android 13–14+.

---

## Установка

1. Скачайте [`PojavLauncher-Rework.apk`](https://github.com/Vanilladev17/pojavlauncher-rework/releases/latest/download/PojavLauncher-Rework.apk).
2. Разрешите установку из этого источника.
3. Войдите в аккаунт Microsoft (для сетевой игры) или запустите оффлайн-режим.

**Сборка:** Android, `arm64-v8a`, подпись debug (`net.kdt.pojavlaunch.debug`) — ставится рядом с официальным PojavLauncher, не заменяя его. Требуется Android 8.0+.

---

## Сборка из исходников

```bash
git clone --recurse-submodules https://github.com/Vanilladev17/pojavlauncher-rework.git
cd pojavlauncher-rework
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app_pojavlauncher:assembleFullDebug
```

Нужно: JDK 21, Android SDK (platform 36, build-tools 36, NDK 29.0.14206865, CMake 3.22.1), подмодули `glfw`, `mojoexec`, `sdl`, `NoBlocklist`.

Готовый APK: `app_pojavlauncher/build/outputs/apk/full/debug/`.

---

## Лицензия и правовая часть

- Код основан на PojavLauncher, распространяется под **GNU LGPL-3.0** — исходники доступны в этом репозитории (см. [`LICENSE`](LICENSE)).
- Проект **не связан** с Mojang Studios и Microsoft. Minecraft — товарный знак Mojang Synergies AB.
- Проект некоммерческий: без рекламы, подписок и внутренних покупок.

## Оригинальный проект

- PojavLauncher: https://github.com/TeamPojavLauncher/PojavLauncher
