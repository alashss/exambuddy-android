package com.exambuddy.ai;

import okhttp3.*;
import org.json.*;
import java.io.IOException;

public class GroqProvider implements AIProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    private final String apiKey;
    private final String model;

    // Available free models on Groq
    public static final String MODEL_LLAMA_70B  = "llama-3.1-70b-versatile";
    public static final String MODEL_LLAMA_8B   = "llama-3.1-8b-instant";
    public static final String MODEL_MIXTRAL    = "mixtral-8x7b-32768";
    public static final String MODEL_GEMMA2     = "gemma2-9b-it";

    public GroqProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public GroqProvider(String apiKey) {
        this(apiKey, MODEL_LLAMA_8B);
    }

    @Override
    public String getName() {
        switch (model) {
            case MODEL_LLAMA_70B: return "Groq Llama 70B";
            case MODEL_MIXTRAL:   return "Groq Mixtral";
            case MODEL_GEMMA2:    return "Groq Gemma2";
            default:              return "Groq Llama 8B";
        }
    }

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
                "Kamu adalah asisten ujian cerdas. Jawab setiap soal dengan akurat dan singkat. " +
                "Format: [JAWABAN] pilihan yang benar, kemudian [PENJELASAN] singkat kenapa.");
            messages.put(sys);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", question);
            messages.put(user);

            body.put("messages", messages);

            Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                    MediaType.parse("application/json")))
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network error: " + e.getMessage());
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
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request error: " + e.getMessage());
        }
    }
}
