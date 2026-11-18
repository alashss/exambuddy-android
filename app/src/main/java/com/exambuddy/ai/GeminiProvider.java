package com.exambuddy.ai;

import okhttp3.*;
import org.json.*;
import java.io.IOException;

public class GeminiProvider implements AIProvider {

    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    private final String apiKey;

    public GeminiProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() { return "Gemini Flash"; }

    @Override
    public String getModelId() { return "gemini-1.5-flash"; }

    @Override
    public void askQuestion(String question, AICallback callback) {
        String prompt = buildExamPrompt(question);
        try {
            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            body.put("contents", contents);

            // Safety settings — allow all for exam use
            JSONArray safety = new JSONArray();
            String[] cats = {"HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"};
            for (String cat : cats) {
                JSONObject s = new JSONObject();
                s.put("category", cat);
                s.put("threshold", "BLOCK_NONE");
                safety.put(s);
            }
            body.put("safetySettings", safety);

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.1);
            genConfig.put("maxOutputTokens", 1024);
            body.put("generationConfig", genConfig);

            Request request = new Request.Builder()
                .url(BASE_URL + apiKey)
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
                            String msg = json.optJSONObject("error") != null
                                ? json.getJSONObject("error").optString("message", bodyStr)
                                : bodyStr;
                            callback.onError("API error: " + msg);
                            return;
                        }
                        String text = json
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
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

    private String buildExamPrompt(String question) {
        return "Kamu adalah asisten ujian. Jawab soal berikut dengan singkat dan tepat. " +
            "Langsung berikan jawaban yang benar beserta penjelasan singkat.\n\n" +
            "Soal: " + question;
    }
}
