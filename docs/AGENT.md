# Mobile chat agent

Enable web and/or personal knowledge under Chat → Tools. The local model writes
queries, reads returned evidence, and chooses whether to refine or answer.
Explicit search and current-news requests require a web lookup when web is
allowed. A follow-up such as «Поищи через поиск» retains the previous topic.
An explicit offline instruction disables web for that request; greetings skip
planning. These rules never create a query from raw user text: the model writes it.

## Mobile budget

- At most **2 sequential tool calls and 3 planning decisions**, including repair.
- The planner uses at most **1,024 actual model tokens** and generates at most
  **96 tokens**, with thinking disabled. It sees a short recent exchange and
  bounded evidence, without resending full documents or image payloads.
- JSON grammar admits only enabled actions. For a required search, `answer` is
  excluded until web has run. Malformed output gets one repair. Duplicate
  queries are not executed again.
- The next decision reads earlier results. An empty successful lookup can be
  refined once. Hard network/provider failures stop directly when no evidence
  exists; the app shows a localized error without an extra model generation.
- Final answers stream normally with the chosen model profile. Sources are
  fitted using the selected model's real tokenizer and keep distinct `[S#]` and
  `[W#]` IDs across lookups. Dates and headline-only limits accompany news.

The final answer retains the original local conversation. The compact planner
has intentionally less context; long-document tasks work best with personal
knowledge enabled. No separate planner model or simultaneous inference is used.
Personal retrieval unloads chat before EmbeddingGemma and restores it afterward.
The Python child is reused across news refinements, then retired before final
answer generation or embedding. Browser views are destroyed after extraction.
Stop, consent revocation and vault lock cancel the entire turn.

Query quality and answer language still depend on the selected GGUF. Automated
protocol tests do not establish every model's reasoning quality or phone speed.

## Search and privacy

[Search](SEARCH.md) uses local SearXNG for news RSS and an actual Android WebView
for normal search pages. CAPTCHA confirmation is manual. The model never gets
browser credentials, native bridges or arbitrary code execution.

Web remains off until native confirmation. Generated queries can contain private
information selected from recent messages, attachments or personal results.
Search engines see the query and IP; browser pages can load third-party resources.
Full files and the full conversation are not attached to requests. Actions and
source snippets are kept in the existing encrypted chat history.
