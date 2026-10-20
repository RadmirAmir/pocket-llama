# Ask your own notes, recordings and scans

[← Pocket](../README.md) · [Русский ↓](#русский) · [Model](https://huggingface.co/unsloth/embeddinggemma-300m-GGUF)

In Chat, open **Tools → Personal knowledge**. Download the search model, review and accept its Gemma terms, then enable the switch for this conversation. You can build the index with **Update index**, or let the first connected question build it. For example: “When did we agree to deliver Atlas?” or “Find the amount and payment date for invoice 773311.”

The search covers every saved active note, the complete edited audio/video transcripts, their summaries, and saved OCR text from document pages and older individual photos. It does not transcribe unfinished audio or recognize an unprocessed photo. Notes in Trash are excluded. English and Russian content can be searched together; the original text is preserved.

Answers show source cards and clickable references such as **[S1]**. A source opens its note, document page, or the relevant time in a transcript. Check these originals when accuracy matters. An embedding model finds passages; your selected chat model writes the answer and can still make mistakes.

The tool is **off by default** and a new conversation disconnects it. Connecting it authorizes a local lookup for each subsequent question in that conversation. Disconnect it in Tools to stop adding knowledge to new requests. Existing answers and their quoted source cards remain part of the encrypted conversation history.

## Download, indexing and memory

- **EmbeddingGemma 300M Q8_0:** a separate 328,577,056-byte download, approximately 329 MB. It is not bundled into the APK and does not replace your chat model. Downloads resume and must pass a pinned SHA-256 check.
- **Incremental indexing:** unchanged passages reuse their vectors. Completed vector checkpoints survive interruption; a source update becomes searchable only after its passages are complete. Changed and deleted originals are reconciled before each lookup, and selected sources are checked against their current originals.
- **On-device storage:** source copies, the full-text index, vectors and a bounded query-vector cache are inside SQLCipher. Clearing the search index keeps the original materials. Rebuild it when needed.
- **Memory:** Pocket releases the chat model before running the CPU embedder, then restores the same model and settings. This adds reload time, but avoids keeping two models in memory together. Repeated identical questions can reuse their query vector. Keep Pocket open during indexing; Stop, leaving the screen, or locking the app can interrupt work safely.
- **Context:** only a bounded set of relevant passages is passed to the chat model. Pocket counts tokens using that model's own template and tokenizer. If there is no space, use a shorter answer, a new conversation or a larger context. Shortened evidence is marked. Search questions are limited to 1,000 characters.

The index stores 768-dimensional normalized float vectors. Exact full-text search (FTS5) and cosine search are combined by reciprocal rank fusion, followed by diversity selection. IDs, dates, names and amounts benefit from the text branch; paraphrases and cross-language questions benefit from embeddings. There is no cloud vector database or remote reranker.

## Embedded MCP tool

`RagMcp` implements JSON-RPC `initialize`, `ping`, `tools/list` and `tools/call` for `search_personal_knowledge(query, limit?, type?)`. Types are `all`, `note`, `voice` and `ocr`; the maximum is eight passages. Results include both MCP text content and structured source data. Disconnected clients cannot list or call the search tool.

The app uses an **in-process MCP transport** and a host-managed lookup for each connected turn. This works with small GGUF chat models without requiring reliable function calling. Retrieved evidence is attached transiently as quoted data; it is not added to every stored user message. There is no MCP network listener, external-app access, shell tool or arbitrary URL input. This version does not add a general remote MCP-client configuration screen.

The model is pinned in [rag-model.lock.json](../rag-model.lock.json); its files are fetched directly from the specified Hugging Face revision. Only model bytes are requested from that service. Your query, passages and vectors stay on the phone. Model terms are separate from Pocket's Apache-2.0 code; see [Gemma notice](../notices/Gemma.txt), [terms](../notices/Gemma-terms.txt) and [prohibited-use policy](../notices/Gemma-prohibited-uses.txt).

## Русский

В чате откройте **Инструменты → Личная база знаний**. Скачайте модель поиска, ознакомьтесь с условиями Gemma и включите переключатель для этого диалога. Нажмите **Обновить индекс** или задайте первый вопрос — индекс построится автоматически. Например: «Когда договорились сдать Атлас?» или «Найди сумму и срок оплаты счёта 773311».

Поиск охватывает все сохранённые заметки вне корзины, полные расшифровки аудио и видео с вашими исправлениями и именами спикеров, конспекты и сохранённый OCR страниц и прежних одиночных фото. Ещё не распознанные записи и изображения нужно сначала обработать. Можно искать по русским и английским материалам вместе.

Под ответом появятся фрагменты источников и ссылки **[S1]**. Они открывают заметку, нужную страницу документа или место в расшифровке. Проверяйте важные детали по оригиналу: ответ пишет выбранная модель чата, и она может ошибаться.

Инструмент изначально выключен. Его включение разрешает локальный поиск для следующих вопросов в этом диалоге; новый диалог отключает инструмент. Старые ответы и карточки с цитатами сохраняются в зашифрованной истории чата.

Модель Q8 занимает около **329 МБ**, скачивается отдельно с докачкой и проверкой SHA-256. Индекс зашифрован. При обновлении повторно считаются только изменившиеся фрагменты; удаления учитываются перед поиском. **Удалить индекс** очищает поисковые данные, сохраняя исходные материалы.

Первая индексация может занять время. Во время смыслового поиска модель чата выгружается и затем запускается с прежними настройками — это экономит память, но добавляет время загрузки. Держите приложение открытым; работу можно остановить и продолжить позднее. Если источники не помещаются в контекст, уменьшите длину ответа, начните новый диалог или увеличьте контекст модели. Вопрос для поиска — до 1 000 символов.

MCP-инструмент встроен внутрь приложения и подключается к чату явно. Публичного сетевого MCP-сервера и доступа сторонних приложений к базе нет. В интернет уходят только запросы на скачивание модели; тексты, вопросы и векторы остаются на телефоне.
