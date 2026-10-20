# Agent loop in chat

Enable the tools you want under **Chat → Tools**. The device date and time zone are supplied for current-news queries. The selected local model now
chooses whether to search, writes a query, reads the tool result and decides
whether to search again or answer. A simple greeting can be answered without
any lookup. Personal knowledge and web search can be combined in one reply.
The app never executes arbitrary tool names, shell commands or model-written code.

The action history shows tool names, exact queries, result counts and errors.
Planner output is internal control JSON, not a chat answer or a displayed thought
transcript. Final answers stream normally, with the existing thinking toggle.
Local sources use `[S1]`; web snippets use `[W1]`. Repeated searches keep distinct
IDs. Source cards contain the passages actually fitted into the final request.
Action records and source cards use the existing encrypted chat storage.

## Limits and failures

- At most four executed tool calls and six model decisions per reply. Repeated
  identical queries do not run again. After the budget, the model answers using
  available evidence; the limit is visible in the action history.
- Decisions use a constrained JSON schema, at most 256 output tokens each and
  queries up to 1,000 characters. A malformed decision gets one correction, then
  a visible fallback to the final answer. No parser guesses a tool from prose.
- Tools run sequentially. Personal search releases the chat model before loading
  EmbeddingGemma and restores it before the next model decision, preserving the
  existing memory boundary. No embedder runs alongside chat inference.
- The running model's tokenizer bounds the combined evidence and reserves space
  for the answer. Oversized evidence is shortened or omitted; the model receives
  explicit omission metadata and the UI marks context limits.
- Tool failures are shown and fed back to the planner. Missing results are not
  turned into invented citations. Stop, a locked vault, consent revocation or
  leaving the chat cancels the running operation and prevents later steps.

No native function-calling template is required: the app uses llama.cpp's JSON
schema response format over the same authenticated loopback API. A model's
ability to choose useful queries still depends on its instruction following.
Tests use deterministic model decisions to check the complete HTTP/SSE loop;
they do not establish answer quality for every downloadable GGUF.

## Web privacy

Web search stays off until native confirmation and resets in a new chat or
unlock session. The planner sees the conversation, attachments and prior search
results. Its generated web queries **may include personal information from any
of these sources**, including your local knowledge. Search engines receive the
queries and your public IP. Full files and the complete history are not uploaded
by the search bridge. Keep this tool off for information you do not want to share.
See [search details](SEARCH.md).

## По-русски

Включённый инструмент доступен помощнику, но не запускается автоматически на
каждое сообщение. Модель выбирает действие, составляет запрос, получает
результат и решает, нужен ли следующий шаг. Можно несколько раз искать в
интернете, уточнять запрос к базе знаний и сочетать оба инструмента.

В блоке «Действия помощника» видны запросы и результаты. На ответ отведено до
четырёх вызовов инструментов; кнопка остановки прерывает весь цикл. Инструменты
без разрешения не вызываются. При выключенных инструментах чат работает как
раньше. Интернет-запросы могут содержать личные сведения из контекста: перед
включением веб-поиска приложение показывает предупреждение.
