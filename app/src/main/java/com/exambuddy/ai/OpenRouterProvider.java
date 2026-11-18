package com.exambuddy.ai;

import okhttp3.*;
import org.json.*;
import java.io.IOException;

public class OpenRouterProvider implements AIProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    private final String apiKey;
    private final String model;
    private final String modelName;

    // Free models on OpenRouter (no cost per token)
    public static final String MODEL_MISTRAL_7B   = "mistralai/mistral-7b-instruct:free";
    public static final String MODEL_LLAMA3_8B    = "meta-llama/llama-3-8b-instruct:free";
    public static final String MODEL_GEMMA_7B     = "google/gemma-7b-it:free";
    public static final String MODEL_PHI3_MINI    = "microsoft/phi-3-mini-128k-instruct:free";
    public static final String MODEL_QWEN2_7B     = "qwen/qwen-2-7b-instruct:free";

    public OpenRouterProvider(String apiKey, String model, String modelName) {
        this.apiKey    = apiKey;
        this.model     = model;
        this.modelName = modelName;
    }

    public OpenRouterProvider(String apiKey) {
        this(apiKey, MODEL_MISTRAL_7B, "Mistral 7B");
    }

    @Override
    public String getName() { return "OpenRouter " + modelName; }

    @Override
    public String getModelId() { return model; }

    @Override
    public void askQuestion(String question, AICallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("max_tokens", 1024);

            JSONArray messages = new JSONArray();

            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content",
                "You are a smart exam assistant. Answer every question accurately. " +
                "Be concise. For multiple choice, clearly state the correct option first.");
            messages.put(sys);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", question);
            messages.put(user);

            body.put("messages", messages);

            Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("HTTP-Referer", "https://github.com/exambuddy")
                .addHeader("X-Title", "ExamBuddy")
                .post(RequestBody.create(body.toString(),
                    MediaType.parse("application/json")))
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String bodyStr = response.body().string();
                    try {
                        JSONObject json = new JSONObject(bodyStr);
                        if (!response.isSuccessful()) {
                            JSONObject err = json.optJSONObject("error");
                            callback.onError(err != null ? err.optString("message") : bodyStr);
                            return;
                        }
                        String text = json
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                        callback.onSuccess(text.trim());
                    } catch (Exception e) {
                        callback.onError("Parse: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request: " + e.getMessage());
        }
    }
}
