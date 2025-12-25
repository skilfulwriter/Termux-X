package com.termux.zerocore.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;

import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

public class SeekerDialog extends BaseDialogCentre {

    private Spinner spinnerTemplates;
    private TextView textViewInstructions;
    
    // Template Views
    private LinearLayout nearYouView;
    private LinearLayout googleDriveView;
    private LinearLayout customLinkPreviewView;
    private LinearLayout telegramView;
    private LinearLayout whatsAppView;
    private LinearLayout whatsAppRedirectView;
    private LinearLayout zoomView;
    private LinearLayout googleRecaptchaView;
    
    // Input Fields
    private EditText editTextGdriveUrl;
    private EditText editTextSitename;
    private EditText editTextTitle;
    private EditText editTextImageUrl;
    private EditText editTextDesc;
    private EditText editTextMembers;
    private EditText editTextOnline;
    private EditText editTextRedirect;
    private EditText editTextDisplayUrl;
    private EditText editTextTelegramTitle;
    private EditText editTextTelegramImage;
    private EditText editTextTelegramDesc;
    private EditText editTextWhatsAppTitle;
    private EditText editTextWhatsAppImage;
    private EditText editTextWhatsAppRedirectTitle;
    private EditText editTextWhatsAppRedirectImage;
    private EditText editTextReCaptchaRedirect;
    private EditText editTextLocalPort;
    
    private Button buttonStart;
    private Button buttonStop;
    private TextView stopHintText;
    private Button buttonTunnelStart;
    private Button buttonTunnelStop;

    private String currentTemplate = "";

    public SeekerDialog(Context context) {
        super(context);
    }

    public SeekerDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    void initViewDialog(View mView) {
        initViews(mView);
        setupTemplateSpinner();
        
        // Initial state
        // 默认启用停止按钮，或者可以通过检查进程状态来决定
        // 这里为了简单起见，我们默认让它是启用的，或者可以添加逻辑去检查
        updateStopButtonState(true);
    }

    @Override
    int getContentView() {
        return R.layout.dialog_seeker;
    }

    private void initViews(View rootView) {
        spinnerTemplates = rootView.findViewById(R.id.spinnerTemplates);
        textViewInstructions = rootView.findViewById(R.id.textViewInstructions);
        stopHintText = rootView.findViewById(R.id.stopHintText);
        
        nearYouView = rootView.findViewById(R.id.nearYouView);
        googleDriveView = rootView.findViewById(R.id.googleDriveView);
        customLinkPreviewView = rootView.findViewById(R.id.customLinkPreviewView);
        telegramView = rootView.findViewById(R.id.telegramView);
        whatsAppView = rootView.findViewById(R.id.whatsAppView);
        whatsAppRedirectView = rootView.findViewById(R.id.whatsAppRedirectView);
        zoomView = rootView.findViewById(R.id.zoomView);
        googleRecaptchaView = rootView.findViewById(R.id.googleRecaptchaView);
        
        editTextGdriveUrl = rootView.findViewById(R.id.editTextGdriveUrl);
        editTextSitename = rootView.findViewById(R.id.editTextSitename);
        editTextTitle = rootView.findViewById(R.id.editTextTitle);
        editTextImageUrl = rootView.findViewById(R.id.editTextImageUrl);
        editTextDesc = rootView.findViewById(R.id.editTextDesc);
        editTextMembers = rootView.findViewById(R.id.editTextMembers);
        editTextOnline = rootView.findViewById(R.id.editTextOnline);
        editTextRedirect = rootView.findViewById(R.id.editTextRedirect);
        editTextDisplayUrl = rootView.findViewById(R.id.editTextDisplayUrl);
        editTextTelegramTitle = rootView.findViewById(R.id.editTextTelegramTitle);
        editTextTelegramImage = rootView.findViewById(R.id.editTextTelegramImage);
        editTextTelegramDesc = rootView.findViewById(R.id.editTextTelegramDesc);
        editTextWhatsAppTitle = rootView.findViewById(R.id.editTextWhatsAppTitle);
        editTextWhatsAppImage = rootView.findViewById(R.id.editTextWhatsAppImage);
        editTextWhatsAppRedirectTitle = rootView.findViewById(R.id.editTextWhatsAppRedirectTitle);
        editTextWhatsAppRedirectImage = rootView.findViewById(R.id.editTextWhatsAppRedirectImage);
        editTextReCaptchaRedirect = rootView.findViewById(R.id.editTextReCaptchaRedirect);
        editTextLocalPort = rootView.findViewById(R.id.editTextLocalPort);

        buttonStart = rootView.findViewById(R.id.buttonStart);
        buttonStop = rootView.findViewById(R.id.buttonStop);
        buttonTunnelStart = rootView.findViewById(R.id.buttonTunnelStart);
        buttonTunnelStop = rootView.findViewById(R.id.buttonTunnelStop);

        buttonStart.setOnClickListener(v -> startSeeker());
        buttonStop.setOnClickListener(v -> stopSeeker());
        buttonTunnelStart.setOnClickListener(v -> openLocalTunnel());
        buttonTunnelStop.setOnClickListener(v -> stopLocalTunnel());
    }

    private void setupTemplateSpinner() {
        List<String> templates = new ArrayList<>();
        templates.add("NearYou");
        templates.add("淘宝分享");
        templates.add("WhatsApp");
        templates.add("WhatsApp Redirect");
        templates.add("Telegram");
        templates.add("腾讯会议");
        templates.add("Google ReCaptcha");
        templates.add("Custom Link Preview");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, templates);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTemplates.setAdapter(adapter);

        spinnerTemplates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentTemplate = templates.get(position);
                updateTemplateViewVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void updateTemplateViewVisibility() {
        nearYouView.setVisibility(View.GONE);
        googleDriveView.setVisibility(View.GONE);
        customLinkPreviewView.setVisibility(View.GONE);
        telegramView.setVisibility(View.GONE);
        whatsAppView.setVisibility(View.GONE);
        whatsAppRedirectView.setVisibility(View.GONE);
        zoomView.setVisibility(View.GONE);
        googleRecaptchaView.setVisibility(View.GONE);
        
        switch (currentTemplate) {
            case "NearYou":
                nearYouView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("定位模板：请求位置权限");
                break;
            case "淘宝分享":
                googleDriveView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("淘宝分享 模板：模拟 淘宝商品分享 页面");
                break;
            case "Custom Link Preview":
                customLinkPreviewView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("自定义链接预览：自定义显示的元数据");
                break;
            case "Telegram":
                telegramView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("Telegram 模板：模拟 Telegram 邀请链接");
                break;
            case "WhatsApp":
                whatsAppView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("WhatsApp 模板：模拟 WhatsApp 邀请链接");
                break;
            case "WhatsApp Redirect":
                whatsAppRedirectView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("WhatsApp 重定向：跳转到真实的 WhatsApp 群组");
                break;
            case "腾讯会议":
                zoomView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("腾讯会议 模板：模拟 腾讯会议链接");
                break;
            case "Google ReCaptcha":
                googleRecaptchaView.setVisibility(View.VISIBLE);
                textViewInstructions.setText("ReCaptcha 模板：模拟验证码页面");
                break;
        }
    }

    private void executeInRootKali(String command, boolean newSession) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;
            TermuxService mTermuxService = activity.mTermuxService;
            TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient = activity.mTermuxTerminalSessionActivityClient;
            
            String sessionName = "Kali-Root";
            // If newSession is requested, we append a timestamp or unique ID to make it distinct
            // Or we just force create a NEW session even if one exists?
            // "Kali-Root" is usually a singleton session for tools.
            // If user wants parallel tools, we should use different session names or append IDs.
            // For this specific request: "Start seeker" AND "Start tunnel" -> separate terminals.
            
            // Let's use "Kali-Seeker" and "Kali-Tunnel" for clarity if we want separation.
            // Or just generic "Kali-Root-1", "Kali-Root-2".
            
            // To keep it simple and consistent with previous logic:
            // If newSession is true, we always create a new session.
            
            TerminalSession targetSession = null;

            if (!newSession && mTermuxService != null) {
                 // Try to find existing "Kali-Root" only if we don't force new
                List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions = mTermuxService.getTermuxSessions();
                for (int i = 0; i < sessions.size(); i++) {
                    TerminalSession session = sessions.get(i).getTerminalSession();
                    if (sessionName.equals(session.mSessionName)) {
                        targetSession = session;
                        break;
                    }
                }
            }

            if (targetSession != null) {
                mTermuxTerminalSessionActivityClient.setCurrentSession(targetSession);
            } else {
                // Create new session. If newSession is true, we might want a unique name?
                // But the user might just want *another* Kali terminal.
                // Let's stick to "Kali-Root" but if we are creating a new one (because we forced it or it didn't exist),
                // Termux usually handles duplicate names by allowing them or we can append ID.
                // Actually Termux sessions don't enforce unique names strictly in the core, but UI might confuse them.
                
                // Better approach: Use distinct names for the tools.
                // But `executeInRootKali` was generic.
                
                // Let's modify executeInRootKali to accept a session suffix or name.
                // But for now, let's just make it create a NEW session if requested.
                
                mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName);
            }

            if (TermuxActivity.mTerminalView != null) {
                TermuxActivity.mTerminalView.sendTextToTerminal("nethunter -r\n");
                TermuxActivity.mTerminalView.sendTextToTerminal(command + "\n");
            }
        }
    }
    
    // Overload for backward compatibility if needed, or just update calls.
    private void executeInRootKali(String command) {
        // Default behavior: reuse or create "Kali-Root"
        // But wait, the user specifically asked for separate terminals for Seeker and Tunnel.
        // So we should probably change the logic to ALWAYS create new session or use distinct names.
        // However, `executeInRootKali` logic I wrote before tries to REUSE "Kali-Root".
        
        // Let's change the implementation to support specific session names.
        executeInRootKali(command, "Kali-Root");
    }

    private void executeInRootKali(String command, String sessionName) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;
            TermuxService mTermuxService = activity.mTermuxService;
            TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient = activity.mTermuxTerminalSessionActivityClient;
            
            TerminalSession targetSession = null;

            if (mTermuxService != null) {
                List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions = mTermuxService.getTermuxSessions();
                for (int i = 0; i < sessions.size(); i++) {
                    TerminalSession session = sessions.get(i).getTerminalSession();
                    if (sessionName.equals(session.mSessionName)) {
                        targetSession = session;
                        break;
                    }
                }
            }

            if (targetSession != null) {
                mTermuxTerminalSessionActivityClient.setCurrentSession(targetSession);
            } else {
                mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName);
            }

            if (TermuxActivity.mTerminalView != null) {
                TermuxActivity.mTerminalView.sendTextToTerminal("nethunter -r\n");
                TermuxActivity.mTerminalView.sendTextToTerminal(command + "\n");
            }
        }
    }

    private void startSeeker() {
        String port = editTextLocalPort.getText().toString().trim();
        if (port.isEmpty()) port = "8080";

        // Determine template index
        int templateIndex = 0;
        switch (currentTemplate) {
            case "NearYou": templateIndex = 0; break;
            case "淘宝分享": templateIndex = 1; break;
            case "WhatsApp": templateIndex = 2; break;
            case "WhatsApp Redirect": templateIndex = 3; break;
            case "Telegram": templateIndex = 4; break;
            case "腾讯会议": templateIndex = 5; break;
            case "Google ReCaptcha": templateIndex = 6; break;
            case "Custom Link Preview": templateIndex = 7; break;
            default: templateIndex = 0;
        }

        // Check installation and run command based on kali-nethunter-app-main logic
        // It uses /usr/share/seeker and https://github.com/skilfulwriter/seeker.git
        
        String installCmd = "if [ ! -d /usr/share/seeker ]; then " +
                            "echo 'Installing Seeker...'; " +
                            "mkdir -p /usr/share; " +
                            "cd /usr/share; " +
                            "git clone https://github.com/skilfulwriter/seeker.git; " +
                            "cd seeker; " +
                            "chmod +x install.sh; " +
                            "./install.sh; " +
                            "fi";

        String startCmd = "cd /usr/share/seeker && " +
                          "TEMPLATE=" + templateIndex + " python3 /usr/share/seeker/seeker.py -t " + templateIndex + " -p " + port;
        
        executeInRootKali(installCmd + " && " + startCmd, "Kali-Seeker");
        dismiss();
    }

    private void updateSeeker() {
        String updateCmd = "echo 'Updating Seeker...'; " +
                           "rm -rf /usr/share/seeker; " +
                           "mkdir -p /usr/share; " +
                           "cd /usr/share; " +
                           "git clone https://github.com/skilfulwriter/seeker.git; " +
                           "cd seeker; " +
                           "chmod +x install.sh; " +
                           "./install.sh;";
        executeInRootKali(updateCmd, "Kali-Seeker");
    }

    private void stopSeeker() {
        // Since we run in "Kali-Seeker" session, we can kill the process there or just pkill globally in nethunter
        // Using generic pkill in a new/existing session is safer to ensure it reaches the process
        // But better: execute pkill in "Kali-Seeker" session if it exists.
        
        // 尝试多种方式停止 Seeker 进程，优先使用 SIGINT (模拟 Ctrl+C)
        String stopCmd = "pkill -SIGINT -f seeker.py || kill -2 $(pgrep -f seeker.py) || pkill -f seeker.py || pkill -9 -f seeker.py";
        executeInRootKali(stopCmd, "Kali-Seeker");
        updateStopButtonState(false);
    }

    private void openLocalTunnel() {
        String port = editTextLocalPort.getText().toString().trim();
        if (port.isEmpty()) port = "8080";
        
        String cmd = "ssh -R 80:localhost:" + port + " nokey@localhost.run";
        executeInRootKali(cmd, "Kali-Tunnel");
    }

    private void stopLocalTunnel() {
        // 尝试多种方式停止隧道进程，优先使用 SIGINT (模拟 Ctrl+C)
        String stopCmd = "pkill -SIGINT -f 'ssh -R 80:localhost' || kill -2 $(pgrep -f 'ssh -R 80:localhost') || pkill -f 'ssh -R 80:localhost'";
        executeInRootKali(stopCmd, "Kali-Tunnel");
    }
    
    private void updateStopButtonState(boolean running) {
        if (running) {
            buttonStop.setEnabled(true);
            buttonStop.setAlpha(1.0f);
            stopHintText.setVisibility(View.GONE);
        } else {
            buttonStop.setEnabled(false);
            buttonStop.setAlpha(0.5f);
            stopHintText.setVisibility(View.VISIBLE);
        }
    }
}
