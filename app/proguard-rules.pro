# Правила R8/ProGuard для release-сборки.
#
# Библиотеки (CameraX, ML Kit, WorkManager, haze, Compose) поставляют свои consumer-правила,
# поэтому специальных keep'ов обычно не требуется. Если release-сборка начнёт падать из-за
# вырезанного класса — добавлять точечный -keep здесь.

# Сохраняем имена исходников и номера строк для читаемых стек-трейсов в release-логах/крашах.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
