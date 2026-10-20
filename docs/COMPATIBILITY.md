# Platforms, memory and models

[← Pocket](../README.md) · [User guide](GETTING-STARTED.md) · [Русский ↓](#русский)

## Platform support

| Platform | Current release |
| --- | --- |
| Android 10+ / API 29+, ARM64 (`arm64-v8a`) | Public APK preview |
| Snapdragon, MediaTek, Exynos, Tensor | Intended targets of the portable ARMv8-A CPU build; physical device validation is pending |
| Android x86 / x86-64 / 32-bit ARM | No published app build |
| iOS / iPadOS | No released app |
| Windows / macOS / Linux | No released Pocket desktop app; host runtime tools are for development |
| Google Play | AAB and build workflow prepared; not yet published in the store |

A 64-bit chip with a 32-bit Android installation is not sufficient. No Qualcomm-only runtime or dedicated NPU is required by the public CPU build. This is a build target statement, not a promise of identical performance across chipsets.

## Choose your first model

Practical starting points are a **6 GB phone for 0.8B**, **8 GB for 2B**, and **12 GB for 4B**. These leave more margin than matching the label on a phone's box to a binary memory threshold. The app makes its own decision using actual device memory, compatibility and current availability.

The pinned catalog's exact configuration is:

| Qwen 3.5 model | File size (decimal) | Required total RAM reported by Android | Estimated working budget | Default context |
| --- | --- | --- | --- | --- |
| 0.8B · Q5_K_M | 646,093,920 bytes / 646 MB | 4 GiB | 1,500 MiB | 2,048 tokens |
| 2B · Q5_K_M | 1,568,476,256 bytes / 1.57 GB | 6 GiB | 2,800 MiB | 2,048 tokens |
| 4B · Q4_K_M | 3,013,027,808 bytes / 3.01 GB | 10 GiB | 4,800 MiB | 2,048 tokens |

Source of truth: [model-catalog.json](../distribution/model-catalog.json). Working budgets are compatibility estimates, not measured peak RAM or performance results. The largest model is intended for a higher memory budget and is not automatically recommended as the default. The catalog also considers currently free memory. The OS and other apps share that memory; virtual RAM does not make a low-memory phone equivalent to a higher-memory one.

## Storage

The public APK is approximately **641 MB**, including GigaAM v3 INT8, Moonshine Streaming Medium with word alignment, Silero VAD, CAM++ and PaddleOCR. Installation and local preparation need more space than the compressed download. Chat models are separate.

Plan on **6 GB free** for setup with the 0.8B or 2B model, or **8 GB** with 4B, then allow more for your recordings and scans. These are convenient planning allowances, not guaranteed upper bounds. The downloader checks for the remaining model bytes plus **256 MiB** of headroom; Android can also need room for installation and other work.

## Languages and quality

| Tool | Current scope |
| --- | --- |
| Interface | English / Russian, switch in Settings |
| Speech recognition | English: bundled Moonshine Streaming Medium; Russian: bundled GigaAM v3 |
| OCR | Russian and English, bundled PaddleOCR |
| Chat and summaries | Depends on the chosen LLM; Russian and English are practical starting languages for the supplied catalog |
| Speaker labels | Approximate automatic grouping; names can be edited |

Public scanner features include page capture, crop/perspective correction, photo/PDF import, text recognition, editing, search and searchable PDF export. Surya layout/table recognition is excluded from the public release pending redistribution rights.

No published phone benchmark matrix is available yet. Automated host, UI and packaging checks are documented in [validation notes](VALIDATION-0.9.0.md); they do not establish battery life, real-time transcription or latency on a physical device. Start small and use the default CPU profile before tuning advanced settings.

## Русский

**Сейчас выпущен APK только для Android 10+ с 64-битной ARM-системой.** Переносимый CPU-движок рассчитан на разные семейства процессоров, включая Snapdragon, MediaTek, Exynos и Tensor. Специальный NPU не нужен. Физическая проверка конкретных телефонов ещё впереди. Для 32-битного Android, x86, iOS и настольных систем готовых приложений нет. Google Play ещё не опубликован.

Для первого выбора ориентируйтесь на **6 ГБ ОЗУ → 0.8B**, **8 ГБ → 2B**, **12 ГБ → 4B**. Это рекомендации с запасом, а не результаты замеров. Каталог проверяет реальную память, которую сообщает Android: минимум **4 / 6 / 10 GiB** соответственно, плюс доступную память и совместимость. Рекламируемые гигабайты, занятая системой память и «расширение ОЗУ» могут отличаться от этих чисел. Оценка рабочего бюджета — **1 500 / 2 800 / 4 800 MiB**; это не измеренный пик потребления.

Загрузки моделей: **646 МБ / 1,57 ГБ / 3,01 ГБ**. Контекст по умолчанию — **2 048 токенов**. Для установки приложения и одной модели удобно иметь **6 ГБ свободного места** для 0.8B/2B либо **8 ГБ** для 4B, затем дополнительное место для своих файлов. APK занимает около **641 МБ**, но установленное приложение и подготовленные модели больше сжатого файла. Проверка загрузчика — остаток файла плюс **256 MiB** запаса; это не полная оценка места для Android.

Интерфейс переключается EN/RU в настройках. Новые расшифровки на EN используют встроенную Moonshine Streaming Medium, на RU — GigaAM v3. OCR читает русский и английский. Незавершённая расшифровка продолжает прежнюю модель; для смены языка нужно запустить её заново. Автоматического определения языка и переключения моделей внутри одной записи нет. Языки чата и конспекта зависят от LLM. Разделение голосов приблизительное. Распознавание таблиц и разметка Surya в публичную сборку не входят.

Скорость, нагрев и расход батареи зависят от телефона; сравнительных замеров на устройствах пока нет. Начните с небольшой модели и стандартных настроек CPU. [Инструкция на русском →](GETTING-STARTED-RU.md)

## Personal-knowledge search

EmbeddingGemma 300M Q8_0 is a separate 328,577,056-byte download, plus encrypted index storage. Vectors alone use about 31 MB per 10,000 passages; text, FTS postings and database overhead add more. The CPU embedder and chat model run sequentially. Available-memory and thermal guards can stop indexing safely. Initial indexing and model reloads take time; physical-phone performance is not yet certified. [Setup and limits →](RAG.md).
