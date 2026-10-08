# Проверки ServerMineCities

## Автоматическая сборка

Из корня репозитория запустите:

```powershell
.\gradlew.bat projects clean build
```

Linux/macOS:

```bash
./gradlew projects clean build
```

`build` компилирует все 16 модулей и API-контракт Economy, запускает модульные тесты и включает `verifyPackaging`. Проверка упаковки подтверждает, что Cities JAR содержит plugin entry point и Cities API, не содержит классы Economy, а ZIP ресурспака содержит метаданные и шрифтовые ресурсы книги.

CI на GitHub выполняет эту сборку на Java 25 для push и pull request. Сборка не заменяет проверку на работающем Minecraft-сервере.

## Проверка на Purpur/Paper

Полный ручной сценарий находится в [server-smoke-test.md](server-smoke-test.md). Проверяйте на отдельном тестовом сервере с совместимыми версиями `ServerMineEconomy` и `ServerMineCities`, а для GUI — с включённым и выключенным клиентским ресурспаком.

Текущие области ручной проверки: основание, claims и защита; роли и права; казна и платежи; налоги; улучшения и стадии; рынок и дипломатия; рестарт и восстановление SQLite; все кнопки книги и служебные клики.
