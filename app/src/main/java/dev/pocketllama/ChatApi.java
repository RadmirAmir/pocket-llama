package dev.pocketllama;

import dev.pocketllama.core.SseReader;
import dev.pocketllama.core.TextAttachment;
import dev.pocketllama.core.LoopbackHttp;
import dev.pocketllama.core.GenerationProfile;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated, loopback-only OpenAI chat client. API key never enters the WebView. */
public final class ChatApi {
    private final int port;
    private final String apiKey;
    private volatile HttpURLConnection active;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final int streamIdleTimeoutMs;
    private long startedAt, firstOutputAt, firstTextAt;
    private JSONObject timings = new JSONObject();

    public interface Listener {
        void chunk(String content, String reasoning);
        void complete(String finishReason, JSONObject usage);
        default void progress(JSONObject prompt, JSONObject metrics) { }
    }
    public ChatApi(int port, String apiKey) {
        this(port, apiKey, 120000);
    }
    public ChatApi(int port, String apiKey, int streamIdleTimeoutMs) {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Некорректный порт");
        if (streamIdleTimeoutMs < 1 || streamIdleTimeoutMs > 180000) throw new IllegalArgumentException("Некорректный тайм-аут");
        this.port = port; this.apiKey = apiKey; this.streamIdleTimeoutMs = streamIdleTimeoutMs;
    }
    public JSONObject capabilities() throws Exception {
        HttpURLConnection connection = open("/props", 3000);
        active = connection;
        try {
            if (cancelled.get()) throw new IOException("Ответ остановлен");
            if (connection.getResponseCode() != 200) throw new LoopbackHttp.HttpFailure(connection.getResponseCode(),error(connection));
            JSONObject props = new JSONObject(read(connection.getInputStream(), 2 * 1024 * 1024));
            JSONObject modalities = props.optJSONObject("modalities");
            JSONObject defaults = props.optJSONObject("default_generation_settings");
            return new JSONObject().put("ready", true)
                    .put("vision", modalities != null && modalities.optBoolean("vision", false))
                    .put("context", defaults == null ? 0 : defaults.optInt("n_ctx", 0))
                    .put("alias", props.optString("model_alias", "mobile-llama"));
        } finally { if (active == connection) active = null; connection.disconnect(); }
    }
    public static JSONObject request(JSONArray messages, boolean thinking, int maxTokens, boolean vision) throws Exception {
        return request(messages,thinking,maxTokens,vision,GenerationProfile.STANDARD);
    }
    public static JSONObject request(JSONArray messages, boolean thinking, int maxTokens, boolean vision, GenerationProfile profile) throws Exception {
        if (messages.length() == 0 || messages.length() > 64) throw new IOException("Слишком длинная история. Начните новый диалог.");
        boolean hasUser = false;
        int imageCount = 0, totalChars = 0;
        JSONArray validated = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            String role = message.getString("role");
            if (!role.equals("user") && !role.equals("assistant") && !role.equals("system")) throw new IOException("Некорректная роль сообщения");
            hasUser |= role.equals("user");
            Object content = message.get("content");
            if (content instanceof String) totalChars += ((String) content).length();
            else if (content instanceof JSONArray) {
                JSONArray parts = (JSONArray) content;
                if (!role.equals("user") || parts.length() > 9) throw new IOException("Некорректное вложение");
                for (int j = 0; j < parts.length(); j++) {
                    JSONObject part = parts.getJSONObject(j);
                    String type = part.getString("type");
                    if (type.equals("text")) totalChars += part.getString("text").length();
                    else if (type.equals("image_url")) {
                        if (!vision) throw new IOException("Запущенная модель не принимает изображения. Выберите совместимый mmproj и перезапустите API.");
                        String url = part.getJSONObject("image_url").getString("url");
                        if (!url.startsWith("data:image/jpeg;base64,") && !url.startsWith("data:image/png;base64,"))
                            throw new IOException("Изображение должно быть локальным JPEG или PNG");
                        if (url.length() > 3 * 1024 * 1024) throw new IOException("Изображение слишком большое");
                        totalChars += url.length(); imageCount++;
                    } else throw new IOException("Неподдерживаемый тип вложения");
                }
            } else throw new IOException("Некорректное содержимое сообщения");
            validated.put(new JSONObject().put("role", role).put("content", content));
        }
        if (!hasUser || imageCount > 24 || totalChars > 10 * 1024 * 1024) throw new IOException("Слишком большой диалог. Начните новый.");
        int limit = Math.max(64, Math.min(2048, maxTokens));
        JSONObject body = new JSONObject().put("model", "mobile-llama").put("messages", validated).put("stream", true)
                .put("max_tokens", limit).put("temperature", profile==GenerationProfile.STANDARD && thinking ? 0.6 : profile.temperature)
                .put("top_p", profile.topP).put("top_k", profile.topK).put("min_p",0.0).put("repeat_penalty",1.0)
                .put("chat_template_kwargs", new JSONObject().put("enable_thinking", thinking))
                .put("cache_prompt", true).put("return_progress", true).put("timings_per_token", true)
                .put("stream_options", new JSONObject().put("include_usage", true));
        // Keep room for a final answer. The native sampler closes the model's own thinking tag.
        body.put("reasoning_budget_tokens",thinking ? Math.min(256,limit/2) : 0);
        if(!thinking) body.put("reasoning_effort","none");
        return body;
    }
    public void stream(JSONObject body, Listener listener) throws Exception {
        startedAt = System.nanoTime(); firstOutputAt = firstTextAt = 0; timings = new JSONObject();
        HttpURLConnection connection = open("/v1/chat/completions", streamIdleTimeoutMs);
        active = connection;
        String finish = "";
        JSONObject usage = new JSONObject();
        boolean done = false;
        try {
            if (cancelled.get()) throw new IOException("Ответ остановлен");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 12 * 1024 * 1024) throw new IOException("Слишком большой запрос");
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            if (connection.getResponseCode() != 200) throw new LoopbackHttp.HttpFailure(connection.getResponseCode(),error(connection));
            try (InputStream input = connection.getInputStream();
                 InputStreamReader text = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                SseReader events = new SseReader(text,true);
                String event;
                while (!cancelled.get() && (event = events.next()) != null) {
                    if (event.equals("[DONE]")) { done = true; break; }
                    JSONObject chunk = new JSONObject(event);
                    if (chunk.has("error")) throw new LoopbackHttp.Failure(message(chunk));
                    JSONObject receivedUsage = chunk.optJSONObject("usage");
                    if (receivedUsage != null) usage = receivedUsage;
                    JSONObject receivedTimings = chunk.optJSONObject("timings");
                    if (receivedTimings != null) timings = receivedTimings;
                    JSONObject prompt = chunk.optJSONObject("prompt_progress");
                    if (prompt != null || receivedTimings != null) listener.progress(prompt, statistics());
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.getJSONObject(0), delta = choice.optJSONObject("delta");
                    if (delta != null) {
                        String content = nullableString(delta, "content"), reasoning = nullableString(delta, "reasoning_content");
                        if (!content.isEmpty() || !reasoning.isEmpty()) {
                            if (firstOutputAt == 0) firstOutputAt = System.nanoTime();
                            if (!content.isEmpty() && firstTextAt == 0) firstTextAt = System.nanoTime();
                            listener.chunk(content, reasoning);
                        }
                    }
                    if (!choice.isNull("finish_reason")) finish = choice.optString("finish_reason", "");
                }
            }
            if (cancelled.get()) listener.complete("cancelled", usage);
            else if (done || !finish.isEmpty()) listener.complete(finish.isEmpty() ? "stop" : finish, usage);
            else throw new EOFException("Соединение закрылось до завершения ответа");
        } catch (Exception error) {
            if (cancelled.get()) listener.complete("cancelled", usage);
            else if (!finish.isEmpty() && LoopbackHttp.disconnected(error)) listener.complete(finish,usage);
            else if (error instanceof SocketTimeoutException)
                throw new IOException("Сервер долго не присылает обновления. Попробуйте новый диалог, контекст 2048 и ответ 256 токенов. Проверьте логи и состояние API.", error);
            else throw error;
        } finally {
            active = null; connection.disconnect();
        }
    }
    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }
    public boolean isCancelled() { return cancelled.get(); }
    public JSONObject statistics() {
        JSONObject result = new JSONObject();
        try {
            if (startedAt > 0) result.put("request_ms", (System.nanoTime() - startedAt) / 1_000_000.0);
            if (firstOutputAt > 0) result.put("first_output_ms", (firstOutputAt - startedAt) / 1_000_000.0);
            if (firstTextAt > 0) result.put("first_text_ms", (firstTextAt - startedAt) / 1_000_000.0);
            for (String key : new String[]{"prompt_n", "prompt_ms", "prompt_per_second", "predicted_n", "predicted_ms", "predicted_per_second", "cache_n"}) {
                double value = timings.optDouble(key, Double.NaN);
                if (Double.isFinite(value) && value >= 0) result.put(key, value);
            }
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
        return result;
    }
    private HttpURLConnection open(String path, int timeout) throws IOException {
        return LoopbackHttp.open(port,path,apiKey,4000,timeout);
    }
    private static String nullableString(JSONObject value, String key) { return value.isNull(key) ? "" : value.optString(key, ""); }
    private static String read(InputStream input, int limit) throws IOException {
        try (InputStream source = input) { return new String(TextAttachment.readLimited(source, limit), StandardCharsets.UTF_8); }
    }
    private static String error(HttpURLConnection connection) {
        try {
            InputStream input = connection.getErrorStream();
            String text = input == null ? "" : read(input, 256 * 1024);
            String result = message(new JSONObject(text));
            String lower = result.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("context") && (lower.contains("exceed") || lower.contains("too large")))
                return "Текст, изображения и история не помещаются в контекст модели. Начните новый диалог или увеличьте контекст в настройках.\n" + result;
            return "API " + connection.getResponseCode() + ": " + result;
        } catch (Exception ignored) { return "Ошибка API. Проверьте состояние сервера и его логи."; }
    }
    private static String message(JSONObject object) {
        JSONObject error = object.optJSONObject("error");
        return error != null ? error.optString("message", "Ошибка модели") : object.optString("message", object.optString("error", "Ошибка модели"));
    }
}
