package com.example.httpreading.service;

import okhttp3.*;
import com.example.httpreading.service.ai.ChatStream;
import okio.BufferedSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ModelClient {
    private static final Logger log = LoggerFactory.getLogger(ModelClient.class);
    private static final int MAX_CHAT_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 800L;

    private static final OkHttpClient HTTP_CLIENT =
            new OkHttpClient.Builder()
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();

    private static final OkHttpClient SINGLE_ATTEMPT_CLIENT = HTTP_CLIENT.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    @Value("${model.apiKey:}")
    private String apiKey;

    @Value("${model.baseUrl:https://api.deepseek.com/chat/completions}")
    private String baseUrl;

    @Value("${model.chatModel:deepseek-chat}")
    private String chatModel;

    @Value("${model.dashscope.apiKey:${model.embedding.apiKey:}}")
    private String dashscopeApiKey;

    @Value("${model.dashscope.embeddingModel:${model.embedding.embeddingModel:text-embedding-v3}}")
    private String dashscopeEmbeddingModel;

    public String chat(String question) {
        return chat(question, ChatOptions.defaults());
    }

    public String chat(String question, ChatOptions options) {
        return chat(question, options, null);
    }

    public String chat(String question, ChatOptions options, ChatStream stream) {
        ChatOptions resolvedOptions = options == null ? ChatOptions.defaults() : options;
        ModelClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_CHAT_ATTEMPTS; attempt++) {
            try {
                return doChat(question, attempt, resolvedOptions, HTTP_CLIENT, stream);
            } catch (ModelClientException exception) {
                if (stream != null) stream.checkActive();
                lastException = exception;
                if (!exception.retryable() || attempt >= MAX_CHAT_ATTEMPTS) {
                    throw exception;
                }
                sleepBeforeRetry(attempt, exception);
            }
        }
        throw lastException == null
            ? new ModelClientException("模型接口请求失败", -1, true)
            : lastException;
    }

    public String chatOnce(String question) {
        try {
            String answer = doChat(question, 1, ChatOptions.defaults(), SINGLE_ATTEMPT_CLIENT, null);
            if (answer.isBlank()) {
                throw new ModelClientException("模型未返回回答", -1, false);
            }
            return answer;
        } catch (JSONException error) {
            throw new ModelClientException("模型返回格式不符合预期", -1, false, error);
        }
    }

    private String doChat(String question, int attempt, ChatOptions options,
                          OkHttpClient client, ChatStream stream) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", chatModel());
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.put(userMsg);
            root.put("messages", messages);
            root.put("stream", false);
            if (options.temperature() != null) {
                root.put("temperature", options.temperature());
            }

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody jsonBody = RequestBody.create(mediaType, root.toString());
            RequestBody body = client == SINGLE_ATTEMPT_CLIENT ? oneShot(jsonBody) : jsonBody;

            Request.Builder builder = new Request.Builder()
                    .url(chatUrl())
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.addHeader("Authorization", "Bearer " + apiKey);
            }

            Call call = client.newCall(builder.build());
            if (stream != null) stream.attach(call);
            String raw;
            try (Response response = call.execute()) {
                raw = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    boolean retryable = isRetryableStatus(response.code());
                    log.warn("模型 HTTP 请求失败 - code:{}, message:{}, body: {}",
                        response.code(), response.message(), truncateLog(raw));
                    throw new ModelClientException(
                        "模型接口请求失败: " + response.code() + " attempt=" + attempt,
                        response.code(),
                        retryable);
                }
            } finally {
                if (stream != null) stream.detach(call);
            }

            log.debug("模型原始返回: {}", truncateLog(raw));

            JSONObject json = new JSONObject(raw);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject first = choices.getJSONObject(0);
                JSONObject msg = first.optJSONObject("message");
                if (msg != null) {
                    return msg.optString("content", "");
                }
            }
            if (client == SINGLE_ATTEMPT_CLIENT) {
                throw new ModelClientException("模型返回格式不符合预期", -1, false);
            }
            return "模型返回格式不符合预期";
        } catch (IOException e) {
            log.warn("调用模型接口异常: {}", e.getMessage());
            throw new ModelClientException("调用模型接口异常: " + e.getMessage(), -1, true, e);
        }
    }

    public String streamChat(String prompt, ChatStream stream) {
        JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
        JSONObject payload = new JSONObject().put("model", chatModel())
            .put("messages", new JSONArray().put(message)).put("stream", true);
        RequestBody body = oneShot(RequestBody.create(MediaType.parse("application/json"), payload.toString()));
        Request.Builder builder = new Request.Builder().url(chatUrl()).post(body)
            .header("Accept", "text/event-stream");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        Call call = SINGLE_ATTEMPT_CLIENT.newCall(builder.build());
        stream.attach(call);
        try (Response response = call.execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                throw new ModelClientException("模型流式请求失败", response.code(), false);
            }
            StringBuilder answer = new StringBuilder();
            String line;
            boolean ended = false;
            var source = responseBody.source();
            while ((line = source.readUtf8Line()) != null) {
                stream.checkActive();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    ended = true;
                    break;
                }
                JSONObject chunk = new JSONObject(data);
                if (chunk.has("error")) {
                    throw new ModelClientException("模型流式响应错误", -1, false);
                }
                JSONArray choices = chunk.optJSONArray("choices");
                if (choices == null || choices.isEmpty()) continue;
                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                String text = delta == null ? "" : delta.optString("content", "");
                if (!text.isEmpty()) {
                    answer.append(text);
                    stream.delta(text);
                }
            }
            if (!ended || answer.toString().isBlank()) {
                throw new ModelClientException("模型未返回完整回答", -1, false);
            }
            return answer.toString();
        } catch (IOException | JSONException error) {
            stream.checkActive();
            throw new ModelClientException("读取模型流式响应失败", -1, false, error);
        } finally {
            stream.detach(call);
        }
    }

    private static RequestBody oneShot(RequestBody body) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() throws IOException {
                return body.contentLength();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                body.writeTo(sink);
            }

            @Override
            public boolean isOneShot() {
                return true;
            }
        };
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500;
    }

    private String chatUrl() {
        return firstNonBlank(System.getProperty("model.baseUrl"),
            System.getProperty("model.chat.baseUrl"), baseUrl, "https://api.deepseek.com/chat/completions");
    }

    private String chatModel() {
        return firstNonBlank(System.getProperty("model.chatModel"),
            System.getProperty("model.chat.model"), chatModel, "deepseek-chat");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void sleepBeforeRetry(int attempt, ModelClientException exception) {
        long delayMs = RETRY_BASE_DELAY_MS * attempt;
        log.warn("模型调用失败，准备重试 attempt={}/{} delayMs={} statusCode={}",
            attempt + 1, MAX_CHAT_ATTEMPTS, delayMs, exception.statusCode());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("模型调用重试等待被中断", exception.statusCode(), false, interruptedException);
        }
    }

    private String truncateLog(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000) + "...";
    }

    /**
     * 调用 Kimi embedding API，将文本转为 1024 维向量
     * @param text 输入文本（最长支持 512 字符，超过则截断）
     * @return 1024 维向量列表
     */
    public List<Float> embedding(String text) {
        try {
            // 截断超长文本（embedding 模型最大输入 512 字符）
            if (text != null && text.length() > 512) {
                text = text.substring(0, 512);
            }

            JSONObject root = new JSONObject();
            root.put("model", "moonshot-text-embedding-v1");
            root.put("input", text);

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, root.toString());

            Request.Builder builder = new Request.Builder()
                    .url("https://api.moonshot.cn/v1/embeddings")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                builder.addHeader("Authorization", "Bearer " + apiKey);
            }

            Request request = builder.build();
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    System.out.println("Embedding HTTP error: " + response.code() + " " + response.message());
                    return new ArrayList<>();
                }

                String raw = response.body().string();
                JSONObject json = new JSONObject(raw);
                JSONArray data = json.optJSONArray("data");
                if (data != null && data.length() > 0) {
                    JSONArray embedding = data.getJSONObject(0).optJSONArray("embedding");
                    if (embedding != null) {
                        List<Float> result = new ArrayList<>(embedding.length());
                        for (int i = 0; i < embedding.length(); i++) {
                            result.add((float) embedding.getDouble(i));
                        }
                        return result;
                    }
                }
                return new ArrayList<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 调用 DashScope embedding API，将文本转为向量
     * @param text 输入文本（DashScope 支持较长的输入）
     * @return 向量列表
     */
    public List<Float> embeddingByDashScope(String text) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", dashscopeEmbeddingModel);
            root.put("input", text);

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, root.toString());

            Request.Builder builder = new Request.Builder()
                    .url("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + dashscopeApiKey);

            Request request = builder.build();
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    System.out.println("DashScope Embedding HTTP error: " + response.code() + " " + response.message());
                    return new ArrayList<>();
                }

                String raw = response.body().string();
                JSONObject json = new JSONObject(raw);
                JSONArray data = json.optJSONArray("data");
                if (data != null && data.length() > 0) {
                    JSONArray embedding = data.getJSONObject(0).optJSONArray("embedding");
                    if (embedding != null) {
                        List<Float> result = new ArrayList<>(embedding.length());
                        for (int i = 0; i < embedding.length(); i++) {
                            result.add((float) embedding.getDouble(i));
                        }
                        return result;
                    }
                }
                return new ArrayList<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public record ChatOptions(Double temperature) {
        public ChatOptions {
            if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
                throw new IllegalArgumentException("temperature must be between 0 and 2");
            }
        }

        public static ChatOptions defaults() {
            return new ChatOptions(null);
        }

        public static ChatOptions deterministic() {
            return new ChatOptions(0.0);
        }
    }
}
