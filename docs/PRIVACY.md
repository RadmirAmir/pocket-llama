# Privacy policy draft — publisher details required before hosting

This draft describes the implementation in 0.8.0. The publisher must supply their identity, contact details, the report-server operator and retention policy before publishing it as the app's policy.

Pocket processes recordings, photographs, document text, transcripts, summaries and conversations on the Android device. Their app-managed storage is encrypted and protected by the local PIN and Android Keystore. The app has no cloud inference service, advertising SDK or automatic analytics/crash-reporting integration.

Network activity occurs for requested model/catalog downloads, user-started video URL imports, opening a model's source page, explicitly enabled local-network API use, and voluntary content reports. Download and video providers receive the network requests needed to serve the requested content. Do not describe all app network activity as model downloads only.

Web search is an optional chat tool, disabled by default and enabled only after
a native confirmation. The local model decides when to search and generates
queries from the conversation, attachments and previous tool results. Google,
Brave, DuckDuckGo and fallback provider Bing receive these queries and the connection IP address.
Queries can contain personal information, including details from local knowledge
passages, even though the bridge does not upload full files, vectors or the
complete chat. The consent dialog explains this broader scope. Search snippets,
queries and the action history can be retained in the encrypted chat history.
Engine caches stay in memory, with query logging disabled. A new chat or unlock
session turns permission off. Opening an Internet source is an explicit
external-browser action. See [built-in search](SEARCH.md) and [agent loop](AGENT.md).

The report action opens a review dialog. The user can edit the answer excerpt and add a comment. Only the reviewed excerpt, comment, selected category and app version are sent after pressing Send. Other conversation messages, recordings and documents are not attached automatically. Reports require HTTPS; redirects are not followed. Failed reports remain in the open dialog and are not silently queued for later transmission. The publisher must identify the receiving service, purpose, retention and deletion contact.

Exports and clipboard actions are explicit user actions. Exported copies are outside Pocket's encryption and can be accessed by the chosen destination app. Enabling LAN API exposes the selected local model's HTTP API to network clients with the API token; the app warns that this transport has no TLS. It does not expose the recorder or document database.

Uninstalling or clearing application data removes the app's access to the encrypted vault. There is no cloud PIN recovery. Public and personal package IDs are separate installations; installing the public preview does not migrate the personal vault.

Permissions: microphone for recording; camera for scanning; notifications and foreground services for visible recording, processing and model downloads; internet for the user-requested transfers described above. Android's document picker is used for file import and export.

Personal knowledge is an optional chat tool. Its index contains copies of saved note/transcript/OCR passages, FTS search postings, vectors, and a bounded cache of query hashes and vectors inside SQLCipher. When connected, relevant passages are passed to the selected local chat model. Original edits and deletions are reconciled before lookup. Clearing the index preserves the original materials; deleting an original does not erase quotes already saved in chat history. The EmbeddingGemma download contacts Hugging Face for public model files only; queries, passages and vectors are not sent to it. The embedded MCP handler has no network listener or access for other applications.

The Play Data safety form must reflect the final SDKs and the voluntary report endpoint. Do not mark “no data collected” solely because inference is local: a submitted report contains user-selected text. Review Google Play's definitions and exceptions for the actual published configuration.


### Background search in 0.13.2

Search uses embedded SearXNG feeds and public APIs without opening remote
browser pages. No search cookies, provider local storage or third-party browser
resources are created. Providers receive model-written queries and the public
IP; queries can include private context selected by the model. Full chat/file
objects are not attached. Search starts off for a new chat, requires native
consent and stops on cancellation, consent revocation or vault lock.
