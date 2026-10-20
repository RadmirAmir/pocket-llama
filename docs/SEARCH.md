# Built-in SearXNG web search

In chat, open **Tools → Web search** and accept the native confirmation. The
local model decides whether a search would help, creates a query, inspects the
returned snippets and can refine the query or use personal knowledge before
answering. Enabling a tool does not run it on every message. The bounded
[agent loop](AGENT.md) allows at most four tool calls per reply.

Responses cite Internet sources as `[W1]`; personal knowledge retains `[S1]`.
Citations stay distinct across repeated lookups. The action history shows tool
queries, completion counts and failures. Source cards show the exact snippets
supplied to the final answer, all outgoing queries, partial engine failures and
context truncation. Opening a source launches the external browser after a tap.

Search is off by default. A new conversation, a new chat Activity, or a changed
vault unlock session requires enabling it again. Restoring chat JSON cannot
grant network consent. Native code checks consent before searching; the WebView
has no network permission through its content security policy.

## What goes online

Google, Brave, DuckDuckGo and fallback provider Bing receive **model-generated search queries** and the
connection's public IP address. The local planner reads the conversation,
attachments and earlier tool results. Its queries can therefore contain personal
information from any of those sources, including personal-knowledge results.
The native consent explicitly explains this before enabling web search. Full
files, embeddings and the complete conversation are not uploaded by the search
bridge, but generated queries are not guaranteed to omit their private content.
Keep web search off when working with information you do not want to share.

Only generated query strings (up to 1,000 characters) and the interface language
reach the native search bridge. Search snippets, rather than full pages, are
returned. Search does not crawl result URLs or load favicons.

The native confirmation explains this before enabling the tool. A visible badge
and composer label remain while search is enabled. Offline/network/CAPTCHA
failures are shown in the action history and returned to the model. It can try
another query within the same budget or explain the missing evidence. A provider outage can produce useful partial results with a notice. If the
primary group produces no usable snippets, one Bing lookup is attempted. DNS,
certificate/TLS, CAPTCHA, rate-limit, timeout and internal runtime failures have
separate fixed diagnostics, without raw URLs or exception text.

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
unauthenticated search route is exposed. Only the three primary engines and backup Bing
are initialized; there are no startup network probes. Engine caches use shared
in-memory SQLite; HTTP/query logging is disabled. Search output is stored only
as part of the app's existing encrypted chat history.

The process is killed after lookup, cancellation, Activity destruction, consent
revocation or a vault session change. A 60-second alarm bounds orphaned work.
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
DNS, verified HTTPS, untrusted-certificate rejection, SQLite cache and cancellation. Fixtures are included only in the diagnostic
test APK. Live provider availability and performance on an ARM64 phone remain
separate device acceptance checks.
