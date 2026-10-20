# Built-in SearXNG web search

In chat, open **Tools → Web search** and accept the native confirmation. Each
message then searches its current question through the bundled SearXNG engine
and passes bounded search snippets to the local language model. Responses cite
Internet sources as `[W1]`; personal knowledge retains `[S1]` citations. Source
cards show the exact snippets supplied to the model, the outgoing query, partial
engine failures and context truncation. Opening a source launches the external
browser only after the user taps it.

Search is off by default. A new conversation, a new chat Activity, or a changed
vault unlock session requires enabling it again. Restoring chat JSON cannot
grant network consent. Native code checks consent before searching; the WebView
has no network permission through its content security policy.

## What goes online

Google, Brave and DuckDuckGo receive the **current question text** and the
connection's public IP address. Personal data typed into that question also goes
to those search engines. Chat history, attachments, local knowledge passages,
embeddings and generated answers are not used to generate search queries and
are not sent to search services. The current question is used literally: no
extra LLM query-planning pass reads private context. Search snippets, rather than
full pages, are returned. Search does not crawl result URLs or load favicons.

The native confirmation explains this before enabling the tool. A visible badge
and composer label remain while search is enabled. Offline/network/CAPTCHA
failures produce an explicit error; the app does not silently invent a searched
answer. A provider outage can produce useful partial results with a notice.

## Android runtime

This is upstream SearXNG running in the app, with its actual engine request
builders, parsers, aggregation and duplicate handling. No public SearXNG instance,
user-supplied server URL, Termux installation, container or companion app is
required. `search-runtime.lock.json` pins SearXNG revision
`15b0c8ef3ab02a86131040e5eabc0d5b82bb9470` (2026-09-04), the last revision
before its curl_cffi transport migration, so the existing bundled CPython 3.12
can be reused. All dependencies and source archives have exact SHA-256 pins.

The build cross-compiles msgspec 0.21.1 and lxml 6.1.2, statically linking
libxml2 2.15.3 and libxslt 1.1.45 with NDK r28c and 16 KB segment alignment.
Target Python headers are generated from the bundled Android interpreter's own
configuration. Universal Python packages and all executable bytes ship inside
the APK. No code is downloaded, installed by pip or compiled on the phone.

The short-lived child listens on a random `127.0.0.1` port. Its 256-bit
capability is passed on stdin and required in a POST header. Browser Origin,
unrecognized arguments and oversized requests are rejected. No web UI or
unauthenticated search route is exposed. Only three configured search engines
are initialized; there are no startup network probes. Engine caches use shared
in-memory SQLite; HTTP/query logging is disabled. Search output is stored only
as part of the app's existing encrypted chat history.

The process is killed after lookup, cancellation, Activity destruction, consent
revocation or a vault session change. A 45-second alarm bounds orphaned work.
Search runs separately from inference and does not unload the chat model.

## Build and verify

Run `scripts/prepare-video.py`, then set `ANDROID_NDK_HOME` to NDK
`28.2.13676358` and run `scripts/prepare-search.py`. The standard runtime
preparation and all distribution builders include and verify search assets.
The generated bundle contains upstream Python source, licenses and a complete
`SOURCES.json`. The adapter is AGPL-3.0-or-later; see
`notices/SearXNG-AGPL-3.0.txt` and `notices/SearXNG.txt`.

Web search ships in the regular Pocket update. The community APK keeps
`dev.pocketllama`, the existing signing identity and the complete speech/OCR
model set. Install it over your existing community app to keep its encrypted
vault, chat history, recordings, scans, notes and personal knowledge index.

Checks include Java URL/context/cancellation tests, WebView consent and rendering
tests, the real SearXNG pipeline with deterministic upstream-response fixtures,
and an x86_64 Android probe running the actual Bionic interpreter, lxml, msgspec,
SQLite cache and cancellation. Fixtures are included only in the diagnostic
test APK. Live provider availability and performance on an ARM64 phone remain
separate device acceptance checks.
