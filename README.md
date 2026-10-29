# Список приложений (RecentsTV)

Плитки недавних приложений для Android TV.
OK — открыть, ↑ — закрыть выбранное, ↓ — кнопка «Закрыть всё».

## Установка

```
adb connect 192.168.50.183:5555
adb -s 192.168.50.183:5555 install -r RecentsTV.apk
adb -s 192.168.50.183:5555 shell appops set app.recentstv GET_USAGE_STATS allow
```

Запуск: Button Mapper → нужная кнопка → «Открыть приложение» → «Недавние».

## Сборка

Заливаете проект на GitHub: вкладка Actions → Build APK → артефакт `RecentsTV-apk`.
