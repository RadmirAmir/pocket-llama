# Pocket Llama 0.13.1 RC.1

## Русский

Исправлен поиск последних новостей: при включённом веб-инструменте модель
обязана выполнить поиск. Уточнение «Поищи через поиск» сохраняет тему прошлого
запроса. Новости читаются через встроенный SearXNG из RSS с датами публикации;
заголовки отделены от полного текста статьи.

Обычный поиск использует настоящий Android WebView с браузерным сетевым стеком
и сессией на вашем телефоне. Если нужна CAPTCHA, откроется страница для ручного
подтверждения. Подтверждение не автоматизируется; гарантировать отсутствие
блокировок поисковиков нельзя.

Агентный цикл рассчитан на мобильное устройство: до двух вызовов инструментов,
три решения с учётом исправления, короткий контекст планирования до 1024 токенов
и вывод плана до 96 токенов. Результаты читаются перед уточнением запроса.
Сетевые ошибки без источников показываются приложением без дополнительного
вызова модели. Остановка и блокировка приложения прерывают весь цикл.

Обновляется основное приложение `dev.pocketllama`, код версии 34, прежняя подпись.
CPU/Vulkan, диктофон, ASR, OCR, видео, заметки, RAG и шифрование сохранены.

## English

Current-news requests now require a lookup when web is enabled. News uses local
SearXNG RSS with publication dates and explicit headline-only evidence. General
search uses a visible real Android WebView; any CAPTCHA requires manual user
confirmation. The mobile planner has a 1024-token input ceiling, 96-token output
ceiling, two tool calls and three decisions including repair. Hard failures with
no evidence bypass final model generation. This updates the complete main app
with its original package, signing identity and CPU/Vulkan/features.

See [agent details](AGENT.md), [search/privacy](SEARCH.md) and
[validation](VALIDATION-0.13.1.md).
