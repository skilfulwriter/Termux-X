package com.termux.zerocore.dialog;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class DirbDialog extends BaseDialogCentre {

    private EditText editTextTarget;
    private EditText editTextWordlist;
    private EditText editTextExtensions;
    private EditText editTextIgnoreStatus;
    private EditText editTextShowStatus;
    private EditText editTextDelay;
    private EditText editTextProxy;
    private EditText editTextCookie;
    
    private CheckBox checkNotRecursive;
    private CheckBox checkIgnoreRedirects;
    private CheckBox checkCaseInsensitive;
    
    private Button buttonScan;
    private Button buttonStop;
    private TextView stopHintText;

    public DirbDialog(Context context) {
        super(context);
    }

    @Override
    void initViewDialog(View mView) {
        initViews(mView);
        setupListeners();
        updateStopButtonState(false);
    }

    @Override
    int getContentView() {
        return R.layout.dialog_dirb;
    }

    private void initViews(View rootView) {
        editTextTarget = rootView.findViewById(R.id.editTextTarget);
        editTextWordlist = rootView.findViewById(R.id.editTextWordlist);
        editTextExtensions = rootView.findViewById(R.id.editTextExtensions);
        editTextIgnoreStatus = rootView.findViewById(R.id.editTextIgnoreStatus);
        editTextShowStatus = rootView.findViewById(R.id.editTextShowStatus);
        editTextDelay = rootView.findViewById(R.id.editTextDelay);
        editTextProxy = rootView.findViewById(R.id.editTextProxy);
        editTextCookie = rootView.findViewById(R.id.editTextCookie);
        
        checkNotRecursive = rootView.findViewById(R.id.checkNotRecursive);
        checkIgnoreRedirects = rootView.findViewById(R.id.checkIgnoreRedirects);
        checkCaseInsensitive = rootView.findViewById(R.id.checkCaseInsensitive);
        
        buttonScan = rootView.findViewById(R.id.buttonScan);
        buttonStop = rootView.findViewById(R.id.buttonStop);
        stopHintText = rootView.findViewById(R.id.stopHintText);
    }

    private void setupListeners() {
        buttonScan.setOnClickListener(v -> startDirb());
        buttonStop.setOnClickListener(v -> stopDirb());
    }

    private void startDirb() {
        String target = editTextTarget.getText().toString().trim();
        
        if (target.isEmpty()) {
            Toast.makeText(mContext, "请输入目标 URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // 我们假设用户在 Kali 环境中，或者此命令将在 Kali 中执行
        // dirb <url_base> [<wordlist_file(s)>] [options]
        
        cmd.append("dirb ").append(target);
        
        String wordlist = editTextWordlist.getText().toString().trim();
        if (!wordlist.isEmpty()) {
            cmd.append(" ").append(wordlist);
        }
        
        if (checkNotRecursive.isChecked()) cmd.append(" -r");
        if (checkIgnoreRedirects.isChecked()) cmd.append(" -w");
        if (checkCaseInsensitive.isChecked()) cmd.append(" -i");
        
        String extensions = editTextExtensions.getText().toString().trim();
        if (!extensions.isEmpty()) {
            cmd.append(" -X ").append(extensions);
        }
        
        String ignoreStatus = editTextIgnoreStatus.getText().toString().trim();
        if (!ignoreStatus.isEmpty()) {
            cmd.append(" -N ").append(ignoreStatus);
        }

        String showStatus = editTextShowStatus.getText().toString().trim();
        if (!showStatus.isEmpty()) {
            // Dirb doesn't strictly have a "show only" flag in standard help, but -S is sometimes used for silence or specific codes in modified versions?
            // Wait, user asked for: # 仅显示指定状态码 dirb http://目标.com/ -S 200,301,302
            // Checking standard dirb man page: 
            // -N <nf> : Ignore responses with this HTTP code.
            // There is no standard -S for "Show Only". 
            // However, user specifically requested "dirb http://目标.com/ -S 200,301,302"
            // I will implement as requested.
            cmd.append(" -S ").append(showStatus);
        }

        String delay = editTextDelay.getText().toString().trim();
        if (!delay.isEmpty()) {
            cmd.append(" -z ").append(delay);
        }

        String proxy = editTextProxy.getText().toString().trim();
        if (!proxy.isEmpty()) {
            cmd.append(" -p ").append(proxy);
        }

        String cookie = editTextCookie.getText().toString().trim();
        if (!cookie.isEmpty()) {
            // Cookies often need quoting
            cmd.append(" -c \"").append(cookie).append("\"");
        }
        
        executeCommand(cmd.toString());
        dismiss();
    }

    private void stopDirb() {
        executeCommand("pkill -f dirb");
        updateStopButtonState(false);
    }

    private void executeCommand(String command) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;
            
            // 参考 SeekerDialog 的实现：
            // 1. 检查是否已存在 "Kali-Root" 会话
            // 2. 如果存在，复用之；如果不存在，创建新的
            // 3. 发送 nethunter -r 命令进入 Root 环境
            // 4. 发送具体的工具命令
            
            String sessionName = "Kali-Root";
            TerminalSession targetSession = null;
            
            if (activity.mTermuxService != null) {
                List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> sessions = activity.mTermuxService.getTermuxSessions();
                for (int i = 0; i < sessions.size(); i++) {
                    TerminalSession session = sessions.get(i).getTerminalSession();
                    if (sessionName.equals(session.mSessionName)) {
                        targetSession = session;
                        break;
                    }
                }
            }
            
            if (targetSession != null) {
                activity.mTermuxTerminalSessionActivityClient.setCurrentSession(targetSession);
            } else {
                activity.mTermuxTerminalSessionActivityClient.addNewSession(false, sessionName);
                // 获取新创建的会话（通常是当前活动的会话）
                targetSession = activity.getCurrentSession();
            }
            
            if (targetSession != null) {
                // 先发送 nethunter -r 确保进入 Root 环境
                // 注意：如果已经是 Root 环境，再次发送也没关系，或者可以加判断
                // 为了简单可靠，我们假设用户可能在普通 Shell 中
                targetSession.write("nethunter -r\n");
                
                // 检查 dirb 是否安装，如果未安装则尝试安装
                // 使用 command -v dirb 检查命令是否存在
                String installAndRunCmd = "if ! command -v dirb >/dev/null 2>&1; then " +
                                          "echo 'Dirb 未安装，正在尝试安装...'; " +
                                          "apt update && apt install dirb -y; " +
                                          "fi; " + 
                                          command;
                
                // 稍微延迟一下？不，Termux 的输入队列应该能处理
                // 发送实际命令
                targetSession.write(installAndRunCmd + "\n");
            }
        }
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
