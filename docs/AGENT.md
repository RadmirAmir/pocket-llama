# Mobile chat agent

Enable web and/or personal knowledge in Chat → Tools. The local model chooses
an action, the app validates and executes it, and the result becomes a separate
quoted turn before the next model decision. The model can refine a query, switch
tools or finish. Search never opens a results window.

This follows the action/result continuation pattern documented by
[Pi agent core](https://github.com/earendil-works/pi/tree/main/packages/agent).
Pocket implements the loop natively in Java. A small JSON action adapter works
with GGUF templates that do not implement native tool-call roles; no Pi package
or separate JavaScript agent process is bundled.

## Mobile limits

- At most three sequential tool calls and five planning decisions per turn.
- Each decision uses at most 1,024 actual model tokens and generates at most
  96 tokens, with thinking disabled. Full documents/images are not replayed
  into the planner. Older results get shorter excerpts.
- Every completed action and its result appear as separate assistant/result
  turns in the compact planning transcript. The next action sees that evidence.
- One malformed response gets a repair. Duplicate queries do not execute;
  repeated duplicate decisions terminate the loop. Truncated planner output
  never executes a tool. Hard failures disable the affected tool for the turn.
- A successful empty web lookup can be refined once. Repeated empty results
  stop before another lookup or an invented news answer.
- The final model prompt includes at most six passages across all lookups.
  Repeated web URLs are deduplicated. Current-news requests fit web evidence
  before personal passages; the selected model's tokenizer bounds the result.

Explicit web/current-news requests require a lookup when web is enabled. A short
follow-up such as «Поищи через поиск» retains the previous topic. An offline
instruction or explicit personal-source scope excludes web. Greetings bypass
planning. A no-tool decision returns the original answer prompt immediately.

Only actual fitted web evidence enables the instruction that Internet search
has completed. Personal results never imply online verification. If required
web search fails after local retrieval, its true failure remains visible rather
than being called a context overflow. Optional web failure can still answer from
relevant personal passages without claiming current facts were checked online.

## Resources and privacy

Tools run sequentially. EmbeddingGemma and chat inference are not loaded
simultaneously by personal retrieval. The SearXNG child and its HTTP pools are
reused within a turn, then retired before final generation or embedding.
Stop, consent revocation and vault lock cancel the entire turn.

[Background search](SEARCH.md) sends only model-written queries to public
feeds/APIs. Those queries can include personal details selected from recent
messages or connected knowledge. Full chat/file objects are not uploaded.
Actions and snippets stay in the existing encrypted chat history. No hidden
planner reasoning is exposed as user-visible actions.

Protocol tests use deterministic model decisions through the production
HTTP/SSE client. They do not establish every GGUF's answer quality or physical
phone performance.
