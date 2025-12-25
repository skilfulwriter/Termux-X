package com.termux.zerocore.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.view.TerminalView;

public class AIAssistantManager {
    private static final String PREFS_NAME = "com.termux.ai_prefs";
    private static final String KEY_AI_API_KEY = "ai_api_key";
    private static final String KEY_AI_API_URL = "ai_api_url";
    private static final String KEY_AI_MODEL_NAME = "ai_model_name";

    private final Context mContext;
    private final SharedPreferences prefs;

    public AIAssistantManager(Context context) {
        this.mContext = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void showConfigDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext);
        builder.setTitle(mContext.getString(R.string.ai_config_title));

        View dialogView = LayoutInflater.from(mContext).inflate(R.layout.fragment_ai_assistant, null);
        // Reuse fragment layout or create a new simple one? 
        // The fragment layout has buttons for "Code Audit" etc. which we might not want in a pure config dialog.
        // But for now, let's reuse it and hide/ignore non-config parts or just accept it's a bit cluttered.
        // Actually, let's look at the fragment layout. It has API Key, URL, Model inputs at the top.
        // We can use it.
        
        TextInputEditText etApiKey = dialogView.findViewById(R.id.etApiKey);
        TextInputEditText etApiUrl = dialogView.findViewById(R.id.etApiUrl);
        TextInputEditText etModelName = dialogView.findViewById(R.id.etModelName);
        Button btnSaveApiKey = dialogView.findViewById(R.id.btnSaveApiKey);
        Button btnPresetOpenAI = dialogView.findViewById(R.id.btnPresetOpenAI);
        Button btnPresetDeepSeek = dialogView.findViewById(R.id.btnPresetDeepSeek);

        // Hide features buttons if we only want config
        // Or keep them as a "Settings & Features" dialog
        // The user asked for "Config API Key" button in the section. So this dialog should focus on Config.
        // We can hide the feature buttons.
        dialogView.findViewById(R.id.btnCodeAudit).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnPayloadGen).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnPhishing).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnSmartDict).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnWafBypass).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnTrafficDetection).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnLogAnalysis).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btnCommandHelper).setVisibility(View.GONE); // Accessed from main menu now

        // Load saved configuration
        String savedKey = prefs.getString(KEY_AI_API_KEY, "");
        String savedUrl = prefs.getString(KEY_AI_API_URL, "https://api.openai.com/v1");
        String savedModel = prefs.getString(KEY_AI_MODEL_NAME, "gpt-3.5-turbo");

        etApiKey.setText(savedKey);
        etApiUrl.setText(savedUrl);
        etModelName.setText(savedModel);

        builder.setView(dialogView);
        builder.setPositiveButton(mContext.getString(android.R.string.ok), (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();

        btnSaveApiKey.setOnClickListener(v -> {
            String apiKey = etApiKey.getText().toString().trim();
            String apiUrl = etApiUrl.getText().toString().trim();
            String modelName = etModelName.getText().toString().trim();

            if (apiUrl.isEmpty()) {
                apiUrl = "https://api.openai.com/v1";
                etApiUrl.setText(apiUrl);
            }
            if (modelName.isEmpty()) {
                modelName = "gpt-3.5-turbo";
                etModelName.setText(modelName);
            }

            prefs.edit()
                .putString(KEY_AI_API_KEY, apiKey)
                .putString(KEY_AI_API_URL, apiUrl)
                .putString(KEY_AI_MODEL_NAME, modelName)
                .apply();
            Toast.makeText(mContext, mContext.getString(R.string.ai_msg_config_saved), Toast.LENGTH_SHORT).show();
        });

        btnPresetOpenAI.setOnClickListener(v -> {
            etApiUrl.setText("https://api.openai.com/v1");
            etModelName.setText("gpt-3.5-turbo");
            Toast.makeText(mContext, mContext.getString(R.string.ai_msg_preset_openai_applied), Toast.LENGTH_SHORT).show();
        });

        btnPresetDeepSeek.setOnClickListener(v -> {
            etApiUrl.setText("https://api.deepseek.com");
            etModelName.setText("deepseek-chat");
            Toast.makeText(mContext, mContext.getString(R.string.ai_msg_preset_deepseek_applied), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    public void showCommandHelperDialog() {
        String apiKey = prefs.getString(KEY_AI_API_KEY, "");
        String apiUrl = prefs.getString(KEY_AI_API_URL, "https://api.openai.com/v1");
        String modelName = prefs.getString(KEY_AI_MODEL_NAME, "gpt-3.5-turbo");

        if (apiKey.isEmpty()) {
            Toast.makeText(mContext, mContext.getString(R.string.ai_msg_api_key_required), Toast.LENGTH_LONG).show();
            showConfigDialog(); // Prompt to config first
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext);
        builder.setTitle(mContext.getString(R.string.ai_btn_command_helper));

        View dialogView = LayoutInflater.from(mContext).inflate(R.layout.dialog_ai_command_helper, null);
        EditText etInput = dialogView.findViewById(R.id.etCommandInput);
        TextView tvOutput = dialogView.findViewById(R.id.tvCommandOutput);
        Button btnGenerate = dialogView.findViewById(R.id.btnGenerate);
        Button btnRunTermux = dialogView.findViewById(R.id.btnRunTermux);
        Button btnRunKali = dialogView.findViewById(R.id.btnRunKali);

        builder.setView(dialogView);
        builder.setNegativeButton(mContext.getString(R.string.ai_cmd_helper_btn_close), (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        btnGenerate.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) return;

            tvOutput.setText(mContext.getString(R.string.ai_cmd_helper_generating));
            btnGenerate.setEnabled(false);

            String systemPrompt = "You are a Kali Linux/Termux expert. Translate the following user request into a specific, executable command line command. Return ONLY the command, no markdown, no explanation, no code blocks. Just the raw command.";

            AIClient.sendMessage(systemPrompt, input, apiKey, apiUrl, modelName, new AIClient.AIResponseListener() {
                private boolean isFirstChunk = true;

                @Override
                public void onNext(String chunk) {
                    if (isFirstChunk) {
                        tvOutput.setText("");
                        isFirstChunk = false;
                    }
                    tvOutput.append(chunk);
                }

                @Override
                public void onSuccess(String response) {
                    if (isFirstChunk) {
                         tvOutput.setText(response.trim());
                    }
                    btnGenerate.setEnabled(true);
                    btnRunTermux.setVisibility(View.VISIBLE);
                    btnRunKali.setVisibility(View.VISIBLE);
                }

                @Override
                public void onError(String error) {
                    tvOutput.setText(error);
                    btnGenerate.setEnabled(true);
                }
            });
        });

        View.OnClickListener runListener = v -> {
            String command = tvOutput.getText().toString();
            if (!command.isEmpty() && !command.startsWith("Error")) {
                if (mContext instanceof TermuxActivity) {
                    TermuxActivity activity = (TermuxActivity) mContext;
                    String sessionName = (v.getId() == R.id.btnRunKali) ? "Kali-Root" : "Termux";
                    
                    activity.ensureSessionAndRunCommand(sessionName, command);
                    
                    Toast.makeText(mContext, "Command sent to " + sessionName, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    
                    if (activity.getDrawer() != null && activity.getDrawer().isOpened()) {
                        activity.getDrawer().smoothClose();
                    }
                } else {
                    Toast.makeText(mContext, "Terminal not available", Toast.LENGTH_SHORT).show();
                }
            }
        };

        btnRunTermux.setOnClickListener(runListener);
        btnRunKali.setOnClickListener(runListener);

        dialog.show();
    }

    public void showExplanationDialog(String textToExplain) {
        String apiKey = prefs.getString(KEY_AI_API_KEY, "");
        String apiUrl = prefs.getString(KEY_AI_API_URL, "https://api.openai.com/v1");
        String modelName = prefs.getString(KEY_AI_MODEL_NAME, "gpt-3.5-turbo");

        if (apiKey.isEmpty()) {
            Toast.makeText(mContext, mContext.getString(R.string.ai_msg_api_key_required), Toast.LENGTH_LONG).show();
            showConfigDialog();
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext);
        builder.setTitle(mContext.getString(R.string.ai_ask));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(mContext);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(mContext);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);
        scrollView.addView(layout);

        TextView tvQuery = new TextView(mContext);
        tvQuery.setText("Query: " + textToExplain);
        tvQuery.setTypeface(null, android.graphics.Typeface.BOLD);
        tvQuery.setTextColor(android.graphics.Color.BLACK);
        layout.addView(tvQuery);

        TextView tvResponse = new TextView(mContext);
        tvResponse.setText("Waiting for AI response...");
        tvResponse.setPadding(0, 30, 0, 0);
        tvResponse.setTextColor(android.graphics.Color.BLACK);
        tvResponse.setTextIsSelectable(true);
        layout.addView(tvResponse);

        builder.setView(scrollView);
        builder.setPositiveButton(mContext.getString(android.R.string.ok), (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        String systemPrompt = "You are a helpful assistant. Explain the following terminal command or text concisely. Please answer in Chinese.";

        AIClient.sendMessage(systemPrompt, textToExplain, apiKey, apiUrl, modelName, new AIClient.AIResponseListener() {
            private boolean isFirstChunk = true;

            @Override
            public void onNext(String chunk) {
                if (mContext instanceof TermuxActivity) {
                    ((TermuxActivity) mContext).runOnUiThread(() -> {
                        if (isFirstChunk) {
                            tvResponse.setText("");
                            isFirstChunk = false;
                        }
                        tvResponse.append(chunk);
                    });
                }
            }

            @Override
            public void onSuccess(String response) {
                 if (mContext instanceof TermuxActivity) {
                    ((TermuxActivity) mContext).runOnUiThread(() -> {
                        if (isFirstChunk) {
                             tvResponse.setText(response.trim());
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                 if (mContext instanceof TermuxActivity) {
                    ((TermuxActivity) mContext).runOnUiThread(() -> {
                        tvResponse.setText("Error: " + error);
                    });
                }
            }
        });
    }
}
