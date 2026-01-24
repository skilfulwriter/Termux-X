package com.termux.zerocore.socket.config;

import static com.termux.zerocore.socket.config.ZTKeyConstants.ZT_ID_ASK_AI;

import android.content.Context;
import android.content.SharedPreferences;

import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;
import com.termux.zerocore.ai.AIClient;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AskAIConfig extends SimpleConfig {
    private static final String PREFS_NAME = "com.termux.ai_prefs";
    private static final String KEY_AI_API_KEY = "ai_api_key";
    private static final String KEY_AI_API_URL = "ai_api_url";
    private static final String KEY_AI_MODEL_NAME = "ai_model_name";

    @Override
    public String getCommand(Context context, String command) {
        // This method is kept for compatibility but should not be used for streaming
        // We can implement a dummy call to execute with a fake writer if needed,
        // but ZTSocketService now calls execute() directly.
        return "Error: Internal error (getCommand called instead of execute)";
    }

    @Override
    public void execute(Context context, String command, PrintWriter out) {
        // command format: "ask <query>" or "ai <query>"
        // potential pid injection: "ask --pid=123 <query>"

        String pidStr = null;
        String query = command;

        // Extract PID if present
        Pattern pidPattern = Pattern.compile("^\\w+\\s+--pid=(\\d+)\\s+(.*)$");
        Matcher matcher = pidPattern.matcher(command);
        if (matcher.find()) {
            pidStr = matcher.group(1);
            query = matcher.group(2);
        } else {
            // Normal split if no pid flag
            String[] parts = command.split(" ", 2);
            if (parts.length < 2) {
                out.println("Usage: ask <query>");
                return;
            }
            query = parts[1];
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(KEY_AI_API_KEY, "");
        String apiUrl = prefs.getString(KEY_AI_API_URL, "https://api.openai.com/v1");
        String modelName = prefs.getString(KEY_AI_MODEL_NAME, "gpt-3.5-turbo");

        if (apiKey.isEmpty()) {
            out.println("Error: API Key not configured. Please configure it in the app settings (AI Assistant).");
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        String systemPrompt = "你是一个专业的 Termux-x 和 Linux 专家。请用中文回答。\n" +
                "如果用户要求创建文件、执行命令或进行具体操作（如“帮我写个Python文件”），请提供具体的 Shell 命令。\n" +
                "将建议执行的 Shell 命令（仅限命令本身）包裹在 @@@EXEC@@@ 标记中，例如：\n" +
                "这是一个示例文件。\n" +
                "@@@EXEC@@@\n" +
                "cat > hello.py << 'EOF'\n" +
                "print('Hello')\n" +
                "EOF\n" +
                "@@@EXEC@@@\n" +
                "运行它：\n" +
                "@@@EXEC@@@ python3 hello.py @@@EXEC@@@\n" +
                "注意：\n" +
                "1. 你可以提供多个 @@@EXEC@@@ 块，它们将按顺序自动执行。\n" +
                "2. 涉及 Python 时，请务必使用 `python3` 命令，不要使用 `python`（除非你确定它存在）。\n" +
                "3. 避免单独使用 `cd` 命令，因为它只在当前执行块有效。若需在特定目录执行，请使用 `cd dir && command`。\n" +
                "4. 确保命令可以在 Termux-x 环境下直接运行。";
        
        // Try to get terminal context if PID is available
        if (pidStr != null) {
            try {
                int pid = Integer.parseInt(pidStr);
                TermuxService service = TermuxService.getInstance();
                if (service != null) {
                    List<TermuxSession> sessions = service.getTermuxSessions();
                    for (TermuxSession session : sessions) {
                        TerminalSession terminalSession = session.getTerminalSession();
                        if (terminalSession.getPid() == pid) {
                            String transcript = terminalSession.getEmulator().getScreen().getTranscriptTextWithFullLinesJoined();
                            // Get last 2000 chars or reasonable amount to avoid token limit
                            if (transcript.length() > 2000) {
                                transcript = transcript.substring(transcript.length() - 2000);
                            }
                            systemPrompt += "\n\nTerminal Context (Last output):\n```\n" + transcript + "\n```\n" +
                                    "User is asking about this context. Analyze the error or output above if relevant.";
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore context errors
            }
        }

        AIClient.sendMessage(systemPrompt, query, apiKey, apiUrl, modelName, new AIClient.AIResponseListener() {
            @Override
            public void onNext(String chunk) {
                out.print(chunk);
                out.flush();
            }

            @Override
            public void onSuccess(String fullResponse) {
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                out.println("\nError: " + error);
                latch.countDown();
            }
        });

        try {
            // Wait for up to 120 seconds (streaming might take longer)
            if (!latch.await(120, TimeUnit.SECONDS)) {
                out.println("\nError: Timeout waiting for AI response.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.println("\nError: Interrupted.");
        }
    }

    @Override
    public int getId() {
        return ZT_ID_ASK_AI;
    }
}
