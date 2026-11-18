package com.exambuddy.ai;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class AIManager {

    public static final String PREF_NAME       = "exambuddy_prefs";
    public static final String PREF_PROVIDER   = "selected_provider";
    public static final String PREF_GEMINI_KEY = "gemini_api_key";
    public static final String PREF_GROQ_KEY   = "groq_api_key";
    public static final String PREF_OPENROUTER_KEY = "openrouter_api_key";
    public static final String PREF_GROQ_MODEL = "groq_model";
    public static final String PREF_OR_MODEL   = "or_model";

    private final SharedPreferences prefs;
    private final List<AIProvider> providers = new ArrayList<>();
    private int currentIndex = 0;

    public AIManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        reload();
    }

    public void reload() {
        providers.clear();
        currentIndex = 0;

        String geminiKey = prefs.getString(PREF_GEMINI_KEY, "");
        String groqKey   = prefs.getString(PREF_GROQ_KEY, "");
        String orKey     = prefs.getString(PREF_OPENROUTER_KEY, "");

        if (!geminiKey.isEmpty()) {
            providers.add(new GeminiProvider(geminiKey));
        }
        if (!groqKey.isEmpty()) {
            String groqModel = prefs.getString(PREF_GROQ_MODEL, GroqProvider.MODEL_LLAMA_8B);
            providers.add(new GroqProvider(groqKey, groqModel));
            // Add second groq model variant
            if (!groqModel.equals(GroqProvider.MODEL_LLAMA_70B)) {
                providers.add(new GroqProvider(groqKey, GroqProvider.MODEL_LLAMA_70B));
            }
        }
        if (!orKey.isEmpty()) {
            String orModel     = prefs.getString(PREF_OR_MODEL, OpenRouterProvider.MODEL_MISTRAL_7B);
            String orModelName = getORModelName(orModel);
            providers.add(new OpenRouterProvider(orKey, orModel, orModelName));
        }

        // Restore saved selection
        int saved = prefs.getInt(PREF_PROVIDER, 0);
        currentIndex = Math.min(saved, Math.max(0, providers.size() - 1));
    }

    private String getORModelName(String id) {
        switch (id) {
            case OpenRouterProvider.MODEL_LLAMA3_8B:  return "Llama3 8B";
            case OpenRouterProvider.MODEL_GEMMA_7B:   return "Gemma 7B";
            case OpenRouterProvider.MODEL_PHI3_MINI:  return "Phi-3 Mini";
            case OpenRouterProvider.MODEL_QWEN2_7B:   return "Qwen2 7B";
            default: return "Mistral 7B";
        }
    }

    public List<AIProvider> getProviders() { return providers; }
    public boolean hasProviders() { return !providers.isEmpty(); }

    public AIProvider getCurrent() {
        if (providers.isEmpty()) return null;
        return providers.get(currentIndex);
    }

    public int getCurrentIndex() { return currentIndex; }

    public void setCurrentIndex(int idx) {
        currentIndex = Math.max(0, Math.min(idx, providers.size() - 1));
        prefs.edit().putInt(PREF_PROVIDER, currentIndex).apply();
    }

    public void nextProvider() {
        if (providers.isEmpty()) return;
        currentIndex = (currentIndex + 1) % providers.size();
        prefs.edit().putInt(PREF_PROVIDER, currentIndex).apply();
    }

    public void prevProvider() {
        if (providers.isEmpty()) return;
        currentIndex = (currentIndex - 1 + providers.size()) % providers.size();
        prefs.edit().putInt(PREF_PROVIDER, currentIndex).apply();
    }

    public void ask(String question, AIProvider.AICallback callback) {
        AIProvider provider = getCurrent();
        if (provider == null) {
            callback.onError("Belum ada API key. Buka Settings untuk menambahkan.");
            return;
        }
        provider.askQuestion(question, new AIProvider.AICallback() {
            @Override
            public void onSuccess(String answer) {
                callback.onSuccess(answer);
            }
            @Override
            public void onError(String error) {
                // Auto-fallback to next provider
                int tried = 1;
                int total = providers.size();
                if (tried < total) {
                    int nextIdx = (currentIndex + tried) % total;
                    providers.get(nextIdx).askQuestion(question, callback);
                } else {
                    callback.onError("Semua provider gagal. Error: " + error);
                }
            }
            @Override
            public void onStream(String chunk) {
                callback.onStream(chunk);
            }
        });
    }
}
