# Pocket Llama 0.13.0 RC.1

## Русский

Чат получил агентный цикл. Модель сама решает, нужен ли включённый инструмент,
составляет запрос, читает результаты и может уточнить поиск перед ответом.
Можно сочетать личную базу знаний и интернет. Доступно до четырёх вызовов на
ответ; в чате видны шаги, запросы, ошибки и источники. Остановка прерывает весь
цикл. Отключённые инструменты не вызываются.

Поиск использует резервный Bing, если Google, Brave и DuckDuckGo не дали
подходящих результатов. Вместо общей ошибки показываются отдельные причины:
DNS, HTTPS/сертификат, CAPTCHA, ограничение запросов, тайм-аут или внутренний
сбой. Текущая дата телефона передаётся модели для вопросов вроде «новости за
сегодня». Проверка доступности провайдеров на конкретном телефоне всё ещё нужна.

Предупреждение при включении веб-поиска обновлено: составленные моделью запросы
могут включать сведения из переписки, вложений и результатов личной базы знаний.
Поисковые системы получают запросы и IP-адрес. Для конфиденциальных задач
оставьте веб-поиск выключенным.

Это обновление основного приложения: пакет `dev.pocketllama`, версия 0.13.0,
код 33 и прежняя подпись. Установка поверх community 0.12.0 сохраняет данные.
Диктофон, распознавание речи, OCR, заметки, видео, RAG и шифрование сохранены.

## English

Chat now uses a bounded agent loop. The local model selects enabled tools,
creates queries, reads the results and can refine the search before answering.
Personal knowledge and web search can be combined, with up to four tool calls
per answer. Actions, queries, failures and citations remain visible in chat.
Stop cancels the entire loop; disabled tools cannot execute.

Bing is a fallback when Google, Brave and DuckDuckGo return no usable evidence.
Search failures distinguish DNS, TLS/certificates, CAPTCHA, rate limits,
timeouts and internal errors. The model receives the device date for current
queries. Live provider availability on a physical phone remains unverified.

The revised consent explains that generated web queries may include personal
details from the conversation, attachments and local knowledge results.
Search engines receive the queries and public IP address.

This is the full main app update with the same package, signing identity and
speech/OCR/video/notes/RAG/encryption features. See [validation](VALIDATION-0.13.0.md)
and [agent details](AGENT.md).
