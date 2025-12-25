package com.termux.zerocore.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIClient {
    private static final String TAG = "AIClient";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AIResponseListener {
        void onNext(String chunk);
        void onSuccess(String fullResponse);
        void onError(String error);
    }

    public static void sendMessage(String systemPrompt, String userMessage, String apiKey, String baseUrl, String modelName, AIResponseListener listener) {
        executor.execute(() -> {
            try {
                // Assuming OpenAI compatible endpoint: POST /chat/completions
                String endpoint = baseUrl;
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
                // Only append /chat/completions if the user didn't provide a full path that looks like it
                if (!endpoint.endsWith("chat/completions")) {
                     endpoint += "/chat/completions";
                }

                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                // Build JSON payload
                JSONObject payload = new JSONObject();
                // Use the provided model name, or default to gpt-3.5-turbo if empty
                String model = (modelName != null && !modelName.isEmpty()) ? modelName : "gpt-3.5-turbo";
                payload.put("model", model);
                payload.put("stream", true); // Enable streaming
                
                JSONArray messages = new JSONArray();
                
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    JSONObject systemMsg = new JSONObject();
                    systemMsg.put("role", "system");
                    systemMsg.put("content", systemPrompt);
                    messages.put(systemMsg);
                }

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userMessage);
                messages.put(userMsg);

                payload.put("messages", messages);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder fullResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                try {
                                    JSONObject json = new JSONObject(data);
                                    JSONArray choices = json.getJSONArray("choices");
                                    if (choices.length() > 0) {
                                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.getString("content");
                                            fullResponse.append(content);
                                            mainHandler.post(() -> listener.onNext(content));
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore parse errors for individual chunks
                                }
                            }
                        }
                        String finalResult = fullResponse.toString();
                        mainHandler.post(() -> listener.onSuccess(finalResult));
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        mainHandler.post(() -> listener.onError("Error: " + responseCode + " " + errorResponse.toString()));
                    } catch (Exception e) {
                         mainHandler.post(() -> listener.onError("Error: " + responseCode));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Request failed", e);
                mainHandler.post(() -> listener.onError("Request failed: " + e.getMessage()));
            }
        });
    }
}