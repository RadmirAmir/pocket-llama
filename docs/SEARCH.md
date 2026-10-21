# Background search on the phone

Web search is an optional tool inside the complete Pocket app. It runs in the
background: no search WebView, browser session, CAPTCHA window or companion app
is created. The chat shows agent actions and source links. Opening a source is
an explicit user action.

## Providers

The real embedded SearXNG scheduler uses public feeds and APIs:

- News: Google News RSS first, Bing News RSS as a fallback. Undated news uses
  the past week; «today» uses a rolling day. Explicit date/range terms remain.
- General queries: Bing RSS and Wikipedia's public search API run together,
  within the same bounded lookup. A failed provider does not discard useful
  results from the other.

Browser search-page scraping and its automatic browser fallback are removed.
Requests identify the app as PocketLlama; no browser/TLS impersonation, CAPTCHA
solving, IP rotation or external scraping proxy is used. Providers can still
limit or reject requests. Such failures remain inline errors; they never open
another window.

At most six public results are returned per lookup. News is sorted by supplied
publication time. Google News descriptions commonly repeat headlines: those
results are marked headline-only, not presented as downloaded articles. Source
cards show publication dates and publisher metadata when supplied. General
results are snippets, not full pages. Linked pages are not fetched automatically.

## Agent and limits

The [native agent](AGENT.md) writes queries, reads results and decides whether to
refine or finish. The whole turn has three tool calls, five planning decisions,
a 1,024-token planning input and 96-token planning output ceiling. Final evidence
is capped at six deduplicated passages and fitted using the actual tokenizer.

The Python child and connection pools are reused within one turn, then closed
before final model generation or embedding. General-provider network requests
can overlap; model inference and tool calls remain sequential. Provider stages
have a seven-second engine timeout; the loopback read is bounded to 24 seconds.
Cancellation/consent/vault checks run throughout. Restored chat history cannot
grant native web consent.

## Privacy

Generated queries, up to 1,000 characters, and the public IP go to the selected
providers. A query can contain private details selected by the model from
conversation, attachments or personal knowledge. Full files, embeddings and
whole chat objects are not attached. Remote browser resources, provider cookies
and browser local storage are not created by search.

Search starts off in a new conversation and requires native confirmation.
Actions and result snippets use the existing encrypted chat history. Keep web
off for information you do not want sent to external sites.

## Runtime and verification

The SearXNG revision and CPython/native dependencies remain pinned in
search-runtime.lock.json. The actual SearXNG scheduler, network stack, parsers
and result container execute each lookup. RSS XML does not load DTDs or resolve
entities. The authenticated loopback server rejects browser Origin, arbitrary
parameters and oversized input. Engine caches are in memory; provider/query
logging is disabled.

The required search bundle has three files: server.py, python.zip and
runtime-id.txt. All distribution builders validate them. Upstream source and
license notices remain in the Python archive and notices/SearXNG*.

Deterministic tests cover real SearXNG parsing, RSS dates/headline limits, API
fallback, private-link filtering, verified TLS and certificate rejection,
consent and cancellation. Live host probes use public synthetic RU news, EN
news and general queries without mocking responses. The Android probe also
makes real public news/general requests through the production Bionic CPython
bridge and checks that search opens no other window. Acceptance results for
this release are in [validation](VALIDATION-0.13.2.md).
