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

import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.TypefaceSpan;
import androidx.annotation.NonNull;

import android.content.DialogInterface;

import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;

import org.json.JSONArray;
import org.json.JSONObject;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.view.ViewGroup;

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

    private void handleCodeClick(String command, String sessionName, DialogInterface dialogToDismiss) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;

            new AlertDialog.Builder(mContext)
                    .setTitle("执行命令")
                    .setMessage("您想要执行此命令吗？\n\n" + command)
                    .setPositiveButton("执行", (dialog, which) -> {
                        activity.ensureSessionAndRunCommand(sessionName, command);
                        Toast.makeText(mContext, "命令已发送到终端", Toast.LENGTH_SHORT).show();
                        if (dialogToDismiss != null) {
                            dialogToDismiss.dismiss();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .setNeutralButton("复制", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Command", command);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(mContext, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        }
    }

    public void showExplanationDialog(String textToExplain, String sessionName) {
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

        // Main Container
        LinearLayout mainLayout = new LinearLayout(mContext);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        // Chat History ScrollView
        android.widget.ScrollView scrollView = new android.widget.ScrollView(mContext);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        // Chat Content Container
        LinearLayout chatContentLayout = new LinearLayout(mContext);
        chatContentLayout.setOrientation(LinearLayout.VERTICAL);
        chatContentLayout.setPadding(10, 10, 10, 10);
        scrollView.addView(chatContentLayout);
        mainLayout.addView(scrollView);

        // Input Area
        LinearLayout inputLayout = new LinearLayout(mContext);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(0, 20, 0, 0);
        inputLayout.setGravity(Gravity.CENTER_VERTICAL);

        EditText etInput = new EditText(mContext);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        etInput.setLayoutParams(inputParams);
        etInput.setHint("输入问题继续对话...");
        inputLayout.addView(etInput);

        Button btnSend = new Button(mContext);
        btnSend.setText("发送");
        inputLayout.addView(btnSend);

        mainLayout.addView(inputLayout);

        builder.setView(mainLayout);
        builder.setPositiveButton(mContext.getString(android.R.string.ok), (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Chat History State
        JSONArray messages = new JSONArray();
        String systemPrompt = "你是一个专业的 Termux-x 和 Linux 专家。请用中文回答。如果用户提供的是报错信息，请解释原因并给出具体的解决命令。如果用户询问如何操作，请给出详细步骤和可执行的代码。注意：涉及 Python 时请使用 python3。不要只解释，要给出解决方案。";
        try {
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.put(systemMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Helper to add message bubble
        Runnable addMessageBubble = () -> {
            // This is just a placeholder, logic below
        };

        Markwon markwon = Markwon.builder(mContext)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
                        builder.on(FencedCodeBlock.class, (visitor, fencedCodeBlock) -> {
                            CharSequence code = fencedCodeBlock.getLiteral().trim();
                            int start = visitor.length();
                            visitor.builder().append(code);
                            int end = visitor.length();

                            visitor.builder().setSpan(new BackgroundColorSpan(0xFFEEEEEE), start, end);
                            visitor.builder().setSpan(new TypefaceSpan("monospace"), start, end);
                            visitor.builder().setSpan(new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View widget) {
                                    handleCodeClick(code.toString(), sessionName, dialog);
                                }

                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    ds.setColor(0xFFC2185B);
                                    ds.setUnderlineText(false);
                                }
                            }, start, end);

                            visitor.ensureNewLine();
                        });
                    }

                    @Override
                    public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                        builder.setFactory(Code.class, (configuration, props) -> new Object[]{
                                new BackgroundColorSpan(0xFFEEEEEE),
                                new TypefaceSpan("monospace"),
                                new ClickableSpan() {
                                    @Override
                                    public void onClick(@NonNull View widget) {
                                        TextView tv = (TextView) widget;
                                        Spanned spanned = (Spanned) tv.getText();
                                        int start = spanned.getSpanStart(this);
                                        int end = spanned.getSpanEnd(this);
                                        CharSequence code = spanned.subSequence(start, end);
                                        handleCodeClick(code.toString(), sessionName, dialog);
                                    }

                                    @Override
                                    public void updateDrawState(@NonNull TextPaint ds) {
                                        super.updateDrawState(ds);
                                        ds.setUnderlineText(false);
                                        ds.setColor(0xFFC2185B);
                                    }
                                }
                        });
                    }
                })
                .build();

        // Logic to send message
        View.OnClickListener sendAction = v -> {
            String userInput = (v == null) ? textToExplain : etInput.getText().toString().trim();
            if (userInput.isEmpty()) return;

            if (v != null) etInput.setText(""); // Clear input if button clicked

            // Add User Message to UI
            TextView tvUser = new TextView(mContext);
            tvUser.setText("我: " + userInput);
            tvUser.setTypeface(null, android.graphics.Typeface.BOLD);
            tvUser.setTextColor(android.graphics.Color.BLACK);
            tvUser.setPadding(0, 20, 0, 10);
            chatContentLayout.addView(tvUser);

            // Add to history
            try {
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userInput);
                messages.put(userMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Prepare AI Response UI
            TextView tvAI = new TextView(mContext);
            tvAI.setText("AI 思考中...");
            tvAI.setTextColor(android.graphics.Color.DKGRAY);
            tvAI.setTextIsSelectable(true);
            tvAI.setMovementMethod(LinkMovementMethod.getInstance());
            tvAI.setPadding(0, 0, 0, 20);
            chatContentLayout.addView(tvAI);
            
            // Auto scroll to bottom
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));

            btnSend.setEnabled(false);

            AIClient.sendMessage(messages, apiKey, apiUrl, modelName, new AIClient.AIResponseListener() {
                private boolean isFirstChunk = true;
                private StringBuilder fullResponseBuilder = new StringBuilder();

                @Override
                public void onNext(String chunk) {
                    fullResponseBuilder.append(chunk);
                    if (mContext instanceof TermuxActivity) {
                        ((TermuxActivity) mContext).runOnUiThread(() -> {
                            if (isFirstChunk) {
                                tvAI.setText("");
                                isFirstChunk = false;
                            }
                            tvAI.append(chunk);
                            // Optional: auto scroll
                            // scrollView.fullScroll(View.FOCUS_DOWN); 
                        });
                    }
                }

                @Override
                public void onSuccess(String response) {
                    String finalResponse = (response != null && !response.isEmpty()) ? response : fullResponseBuilder.toString();
                    
                    if (mContext instanceof TermuxActivity) {
                        ((TermuxActivity) mContext).runOnUiThread(() -> {
                             markwon.setMarkdown(tvAI, finalResponse);
                             btnSend.setEnabled(true);
                             scrollView.fullScroll(View.FOCUS_DOWN);
                        });
                    }
                    
                    // Add to history
                    try {
                        JSONObject aiMsg = new JSONObject();
                        aiMsg.put("role", "assistant");
                        aiMsg.put("content", finalResponse);
                        messages.put(aiMsg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(String error) {
                    if (mContext instanceof TermuxActivity) {
                        ((TermuxActivity) mContext).runOnUiThread(() -> {
                            tvAI.setText("错误: " + error);
                            btnSend.setEnabled(true);
                        });
                    }
                }
            });
        };

        btnSend.setOnClickListener(sendAction);

        // Trigger initial message
        sendAction.onClick(null);
    }
}
