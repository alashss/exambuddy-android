package com.exambuddy.ai;

public interface AIProvider {
    String getName();
    String getModelId();
    void askQuestion(String question, AICallback callback);

    interface AICallback {
        void onSuccess(String answer);
        void onError(String error);
        void onStream(String chunk); // partial streaming
    }
}
