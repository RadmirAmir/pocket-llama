# Search on the phone

Web search is an optional tool in the complete Pocket app (`dev.pocketllama`).
No remote SearXNG instance, API key, companion app or container is needed.

## News

The real embedded SearXNG processor loads Google News RSS first and Bing News RSS
as a fallback. Feeds include dates and titles without depending on browser SERP
markup. Undated queries use the past week; «today» uses a rolling day. Explicit
date/range terms are retained. The six newest usable results are selected.

Google News descriptions often repeat related headlines: they are labeled
**headline only**, never presented as downloaded article text. Source cards show
publication dates, publisher metadata where supplied, and the feed's article
link. Redirect decoding and full-page crawling are not performed.

## Normal browser search

General queries open a **visible Android WebView**, using its default user agent,
Chromium networking, JavaScript and a real browser session on the phone. Google,
Brave and DuckDuckGo can be selected on the page. News also falls back to this
browser if its feeds fail. There is no user-agent/TLS impersonation, automated
CAPTCHA solving, IP rotation or use of an external scraping proxy.

If a provider asks for confirmation, the page stays open for the user. Extraction
resumes after confirmation. The reader collects at most six visible result
headings/snippets, deduplicates URLs and excludes ads. It does not click article
links. The browser session is separate from Chrome and does not import Chrome
cookies or passwords. A successful browser request is still not guaranteed:
providers can reject an IP or require a verification the WebView cannot complete.

No native JavaScript interface is attached to remote pages. File/content access,
mixed content, downloads, popups, camera/microphone/geolocation permissions and
third-party cookies are disabled. Search navigation is limited to the supported
search/consent origins. The temporary browser view is destroyed after extraction;
cache/history are cleared. Search cookies and provider storage are cleared when
web consent is revoked, the chat closes or the vault locks. The browser window
also uses screen-capture protection.

## Agent and resource limits

The model writes each query and inspects returned evidence within the
[mobile agent budget](AGENT.md). Search is not automatically run for greetings
or offline instructions. Normal failure messages are produced by the app rather
than paraphrased by a small model into technical error codes.

The SearXNG child and connection pools are reused only within the current turn.
It is retired before the final LLM generation or local embedding. Engine stages
have bounded timeouts. Browser confirmation allows up to 90 seconds and can be
cancelled immediately. Native consent, vault epoch and cancellation are checked
throughout; restoring chat history cannot grant consent.

## Privacy

Generated queries (up to 1,000 characters) and the public IP go to the selected
providers. Queries may include private details from conversation, attachments
or knowledge results. Browser pages also load their normal remote resources.
No complete file, embedding or full chat object is uploaded by the search bridge.
Keep the tool off for information you do not want shared. Actions and snippets
are saved in the existing encrypted chat history.

## Runtime and verification

`search-runtime.lock.json` pins upstream SearXNG revision
`15b0c8ef3ab02a86131040e5eabc0d5b82bb9470` and its CPython dependencies. The
RSS adapter uses the real SearXNG scheduler, network stack, parsers and result
container. XML parsing does not fetch DTDs or expand entities. Its authenticated
loopback server rejects browser Origin, unknown parameters and oversized input;
engine caches are in memory and provider/query logging is disabled.

Build with `scripts/prepare-video.py` and `scripts/prepare-search.py` using pinned
NDK 28.2.13676358. All distribution builders validate all four search assets,
including the browser reader. Licenses/source pins remain in the Python bundle
and `notices/SearXNG*`.

Tests cover the HTTP/SSE agent protocol, real TLS and certificate rejection,
RSS dates/headline limits, browser DOM reading, consent, cancellation and safe
source rendering. The Android probe executes actual Bionic CPython and an actual
Android WebView. Its browser pages and manual-confirmation button are synthetic;
it never solves a real CAPTCHA. `scripts/test-search-live.py --news-only` separately
queries real public news providers with no mocked responses and writes sanitized
counts/latency evidence. Physical-phone availability still depends on its network.
