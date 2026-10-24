# Architecture and extension boundaries

This describes the existing Android application and the bounded refactoring on
`feature/inference-performance`. The accepted 0.13.4 remains on `main` and the
frozen branch recorded in [AGENTS.md](../AGENTS.md). No model, database schema,
application ID, distribution policy or release version changes in this work.

## Current responsibilities

| Area | Policy / orchestration | Platform and runtime adapters |
| --- | --- | --- |
| Speech | `Transcription`, `SignalFeatures`, `SpeakerClusters`, `SpeechRecognizer.Word` | `SpeechService`, `VoiceStore`, encrypted `PcmFile`, `SpeechModels.Asr` (GigaAM/ORT), `MoonshineAsr` (JNI) |
| OCR | `SuryaPipeline`, `SuryaProtocol`, `SuryaPageHtml`, output/deadline guards, `ScanGeometry` | `OcrService`, `SuryaRuntime`, `OcrEngine`, `ScanImages`, `PhotoStore` / `DocumentStore` |
| Summaries | `LocalSummary`, `SummarySource`, `VideoSummaryEvidence` | `SummaryService`, `VoiceSummarySource`, `ChatApi`, inference service |
| Chat and knowledge | `ChatAgent`, intent/budget rules, `RagText` | activities, search/embedding runtimes, encrypted stores |
| UI | HTML/CSS, screen controllers, `native-rpc.js` request correlation | activity bridges, `UiHost`, recording/playback, pickers and permissions |

These are boundaries inside one application, not a new framework or module
hierarchy. Some orchestration (especially OCR and activities) still depends on
Android. The table does not claim that all domain code is portable today.

## Speech: replace the runtime without rewriting the job

`SpeechRecognizer` owns no Android or ONNX types. It consumes mono 16 kHz PCM
and returns words with absolute millisecond timestamps. Both existing language
engines implement it. Persistence consumes this contract instead of depending
on the ONNX wrapper's nested classes. Historical schema test fixtures retain
their old types intentionally.

`Transcription` handles one attempt, one window at a time, through four small
ports: reading audio, querying/committing checkpoints, checking cancellation and
device pressure, and reporting progress. There is no queue of audio windows,
extra model session, executor or coroutine runtime. It does not own or close the
recognizer, database or audio file. `SpeechService` owns these resources, Android
notifications, wake lock, thermal/memory checks and the cross-process model lease.
The recognizer has one try-with-resources owner, including failure paths.

Persisted compatibility rules:

- Feed VAD regions in stored order, including completed regions. Part IDs start
  at zero and advance for every core, including skipped checkpoints.
- Cores remain 192,000 samples (12 seconds); context is 3,840 samples (240 ms)
  on each side, clipped against the recording, not against the VAD region.
- The input offset and core bounds retain integer sample-to-millisecond
  truncation. A partial final core is still a complete checkpoint.
- `VoiceStore.part` atomically stores words and the completion marker. It keeps
  the existing midpoint filter and overlap deduplication. Empty recognition
  results are also completed parts.
- Check cancellation before work and after inference, before committing. Some
  native calls finish before they can observe cancellation; their late results
  must not be persisted. Restart a failed attempt with a new `Transcription`
  instance and the same regions.

The window constants are deliberately not exposed as user settings. Changing
window geometry, VAD regions or their order while reusing old part IDs would
silently corrupt resume behavior. Such experiments need versioned checkpoints
or an explicit clean restart policy first. The stored language remains the
source of truth for resumed jobs.

## UI: one request lifecycle, separate screen behavior

`PocketRpc.create` accepts a transport function. Android's existing adapters
still send `call(requestId, action, JSON.stringify(args))`; each screen routes
the `data` of a `reply` event to `receive`. Action names, response shapes,
native event handlers and UI state stay in their existing screen controllers.

The shared client owns unique per-page IDs, pending promises, deadlines and
cleanup. Replies may arrive out of order; unknown, duplicate and timed-out
replies are ignored. Serialization and synchronous transport errors release
pending state. This fixes the scanner's retained callback/timer after a thrown
native call. Studio/notes keep 30-second deadlines; scanner keeps 60 seconds.
Screens retain their existing localized error messages.

A timeout only stops waiting in the UI. It does not roll back a native change,
cancel model inference, or automatically repeat an import/delete/save. UI code
must refresh authoritative state before offering to repeat a mutation.

Load the shared script before each screen controller and include it in
`UiHost`'s explicit asset allowlist. Keep the closed local origin, restrictive
CSP and native vault/argument checks. The common client is not an authorization
boundary. Notes' synchronous draft staging, initial `ready` calls, playback and
unsolicited events remain native contracts outside this request client.

## Deliberately retained boundaries

- **Different model lifecycles:** ASR, autoregressive OCR, chat and embeddings
  have different input/output, deadlines, cancellation and checkpoint behavior.
  A universal inference interface now would hide those distinctions. Add a
  narrow runtime adapter when an actual second implementation needs it.
- **OCR orchestration:** Surya already has separate protocol, HTML parsing,
  geometry, output guards and recovery tests. `SuryaPipeline` still uses Android
  bitmaps/stores and a concrete runtime. A future second OCR backend can justify
  a typed runtime boundary; moving those dependencies now adds risk without
  enabling a current feature.
- **Process lifetime and encryption:** keep isolated workers, `ModelLease`,
  encrypted storage, PIN gates and current database migrations. A generic
  service superclass or combined model process would change resource/privacy
  behavior and needs separate device evidence.
- **Large screen controllers:** move a cohesive operation behind a tested
  contract when changing that operation. Avoid a bulk rewrite or formatting-only
  churn across unrelated activities while their behavior is accepted.

## Performance experiments

Use a separate candidate and one measurable change at a time. Record model and
input hashes, runtime/backend, phone/OS, threads, warm/cold state, thermal state,
stage times, peak memory, time to first useful result and output quality. For
speech include timestamp alignment, diarization and resume/cancel cases; for
OCR include full-page text, tables, reading order and partial recovery.

Preserve a portable CPU route. Accelerator-specific setup belongs in the
relevant runtime adapter; it must not leak vendor checks into stores or UI.
Graph conversion, numerical changes and changed windowing are experiments,
not behavior-preserving refactoring. Host timings do not establish phone speed,
and mock tests do not establish model accuracy. This refactor claims neither
a measured speedup nor NPU support.

## Future iOS work

The web assets and documented request/reply protocol provide a reusable UI
boundary. An iOS host would need its own transport, lifecycle/events, secure
storage, audio import/recording/playback, background-work policy and runtime
integration. In particular, notes' synchronous staging needs explicit design
for an asynchronous host bridge.

Plain Java is Android-independent here, not automatically Swift-compatible.
The ASR contract and fixtures can guide an iOS implementation, or inform a
future decision to share a suitable native core. Do not introduce a second
language/framework or pretend Android service/process APIs exist on iOS before
that implementation is chosen. No iOS app or adapter is included in this change.

## Verification

See [BUILDING.md](BUILDING.md) for toolchain setup. The extraction adds host
tests for window boundaries, unchanged part IDs, resume, empty output, corrupt
regions, read/model/store failures and cancellation during inference. The RPC
tests cover correlation, errors, deadlines, cleanup, wire compatibility and
Android asset inclusion. Existing UI scenarios exercise real screen assets
against scripted native responses. They complement, rather than replace,
Android/device testing and owner acceptance before a merge.
