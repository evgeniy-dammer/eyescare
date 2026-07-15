# EyesCare — подготовка к релизу

Черновики и чек-лист для публикации в Google Play. Тексты политики/Data Safety выверить с юристом
перед публикацией.

## 1. Сборка и подпись

- Release-сборка использует **R8** (`isMinifyEnabled = true`) и **shrinkResources** (`isShrinkResources = true`).
  Consumer-правила библиотек (CameraX, ML Kit, WorkManager, haze, Compose) достаточны — специальных keep'ов
  не потребовалось; `proguard-rules.pro` содержит только `-keepattributes SourceFile,LineNumberTable` для
  читаемых стек-трейсов. Release-сборка проверена на устройстве (онбординг, камера, ML Kit — дистанция
  считается, крашей нет).
- **Подпись:** сейчас release временно подписан debug-ключом (`signingConfig = signingConfigs.debug`) — только
  для внутренних сборок. **Перед публикацией** создать production-keystore и настроить:
  ```kotlin
  signingConfigs {
      create("release") {
          storeFile = file(System.getenv("EYESCARE_KEYSTORE") ?: "release.keystore")
          storePassword = System.getenv("EYESCARE_STORE_PASSWORD")
          keyAlias = System.getenv("EYESCARE_KEY_ALIAS")
          keyPassword = System.getenv("EYESCARE_KEY_PASSWORD")
      }
  }
  // buildTypes.release { signingConfig = signingConfigs.getByName("release") }
  ```
  Ключ и пароли — вне репозитория (env/CI-секреты). Рекомендуется Play App Signing.
- `versionCode`/`versionName` — поднимать при каждой публикации (сейчас 1 / "1.0").
- Сборка для загрузки: `./gradlew :app:bundleRelease` (AAB для Play).

## 2. Privacy policy (черновик)

**EyesCare не собирает, не хранит вне устройства и не передаёт никакие персональные данные.**

- Приложение **не имеет доступа к интернету** (нет разрешения `INTERNET`) — данные физически не могут покинуть
  устройство.
- **Камера** используется исключительно локально для измерения расстояния от глаз до экрана средствами
  on-device ML Kit Face Detection. Кадры обрабатываются в реальном времени в ОЗУ и **не сохраняются и не
  передаются**.
- Настройки (порог, IPD, статистика использования, флаги) хранятся **локально в зашифрованном виде**
  (`EncryptedSharedPreferences`, AES-256, ключ в Android Keystore) и исключены из резервных копий.
- Нет аналитики, рекламы, трекеров, аккаунтов.

Политику нужно разместить по публичному URL и указать его в Play Console.

## 3. Google Play Data Safety (ответы для формы)

- Собираются ли данные? — **Нет.**
- Передаются ли данные третьим сторонам? — **Нет.**
- Шифрование при передаче — **N/A** (передачи нет).
- Возможность удаления данных — данные только локальные; удаляются при удалении приложения / очистке данных.
- Обработка камеры — on-device, не покидает устройство.

## 4. Обоснование разрешений (для проверки Play)

| Разрешение | Зачем |
|---|---|
| `CAMERA` | Измерение расстояния до глаз (фронтальная камера, локально). |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA` | Мониторинг работает как foreground-сервис типа `camera`, пока пользователь его включил. |
| `POST_NOTIFICATIONS` | Постоянное уведомление сервиса, предупреждения, напоминания о перерывах. |
| `SYSTEM_ALERT_WINDOW` | Полноэкранный баннер-предупреждение «Слишком близко» поверх других приложений. **Требует явного обоснования в Play** (используется только для предупреждения о здоровье глаз, не для рекламы/оверлеев). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Чтобы ОС не выгружала мониторинг в фоне. **Чувствительное для Play** — обосновать «непрерывный мониторинг по запросу пользователя» либо перейти на `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. |
| `RECEIVE_BOOT_COMPLETED` | Показать напоминание возобновить мониторинг после перезагрузки. |
| `VIBRATE` | Вибрация при предупреждении «слишком близко». |

## 5. Чек-лист перед публикацией

- [ ] Настроить production-keystore + Play App Signing; убрать debug-подпись из release.
- [ ] Поднять `versionCode`/`versionName`.
- [ ] Разместить privacy policy по URL, указать в Console.
- [ ] Заполнить Data Safety (раздел 3).
- [ ] Обосновать `SYSTEM_ALERT_WINDOW` и `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` в декларациях разрешений.
- [ ] Скриншоты (обе темы, ключевые экраны), иконка, описание — на 7 поддерживаемых языков.
- [ ] Проверить AAB на нескольких устройствах/версиях Android (мин. 8.0 / API 26).
- [ ] `./gradlew :app:lintRelease` без ошибок; юнит-тесты зелёные.
