package com.termux.zerocore.dialog;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;

import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.terminal.TerminalSession;
import com.termux.app.TermuxService;
import java.util.List;

public class SqlmapDialog extends BaseDialogCentre {

    private EditText editTextUrl;
    private EditText editTextData;
    private EditText editTextCookie;
    
    private CheckBox checkBoxWizard;
    private LinearLayout layoutOptions;
    
    private CheckBox checkBoxBatch;
    private CheckBox checkBoxRandomAgent;
    private CheckBox checkBoxDbs;
    private CheckBox checkBoxTables;
    private CheckBox checkBoxColumns;
    private CheckBox checkBoxDump;
    
    private EditText editTextLevel;
    private EditText editTextRisk;
    
    private Button buttonStart;
    private Button buttonStop;
    private TextView stopHintText;

    public SqlmapDialog(Context context) {
        super(context);
    }

    public SqlmapDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    void initViewDialog(View mView) {
        initViews(mView);
        setupListeners();
        updateStopButtonState(false);
    }

    @Override
    int getContentView() {
        return R.layout.dialog_sqlmap;
    }

    private void initViews(View rootView) {
        editTextUrl = rootView.findViewById(R.id.editTextUrl);
        editTextData = rootView.findViewById(R.id.editTextData);
        editTextCookie = rootView.findViewById(R.id.editTextCookie);
        
        checkBoxWizard = rootView.findViewById(R.id.checkBoxWizard);
        layoutOptions = rootView.findViewById(R.id.layoutOptions);
        
        checkBoxBatch = rootView.findViewById(R.id.checkBoxBatch);
        checkBoxRandomAgent = rootView.findViewById(R.id.checkBoxRandomAgent);
        checkBoxDbs = rootView.findViewById(R.id.checkBoxDbs);
        checkBoxTables = rootView.findViewById(R.id.checkBoxTables);
        checkBoxColumns = rootView.findViewById(R.id.checkBoxColumns);
        checkBoxDump = rootView.findViewById(R.id.checkBoxDump);
        
        editTextLevel = rootView.findViewById(R.id.editTextLevel);
        editTextRisk = rootView.findViewById(R.id.editTextRisk);
        
        buttonStart = rootView.findViewById(R.id.buttonStart);
        buttonStop = rootView.findViewById(R.id.buttonStop);
        stopHintText = rootView.findViewById(R.id.stopHintText);
    }

    private void setupListeners() {
        buttonStart.setOnClickListener(v -> startSqlmap());
        buttonStop.setOnClickListener(v -> stopSqlmap());
        
        checkBoxWizard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutOptions.setVisibility(View.GONE);
            } else {
                layoutOptions.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startSqlmap() {
        String url = editTextUrl.getText().toString().trim();
        
        if (url.isEmpty()) {
            Toast.makeText(mContext, "请输入目标 URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder cmd = new StringBuilder();
        // Kali usually has sqlmap pre-installed or install via apt
        cmd.append("dpkg -s sqlmap > /dev/null 2>&1 || apt-get install sqlmap -y && ");
        
        cmd.append("sqlmap -u \"").append(url).append("\"");
        
        if (checkBoxWizard.isChecked()) {
            cmd.append(" --wizard");
        } else {
            String data = editTextData.getText().toString().trim();
            if (!data.isEmpty()) {
                cmd.append(" --data=\"").append(data).append("\"");
            }
            
            String cookie = editTextCookie.getText().toString().trim();
            if (!cookie.isEmpty()) {
                cmd.append(" --cookie=\"").append(cookie).append("\"");
            }
            
            if (checkBoxBatch.isChecked()) cmd.append(" --batch");
            if (checkBoxRandomAgent.isChecked()) cmd.append(" --random-agent");
            if (checkBoxDbs.isChecked()) cmd.append(" --dbs");
            if (checkBoxTables.isChecked()) cmd.append(" --tables");
            if (checkBoxColumns.isChecked()) cmd.append(" --columns");
            if (checkBoxDump.isChecked()) cmd.append(" --dump");
            
            String level = editTextLevel.getText().toString().trim();
            if (!level.isEmpty() && !level.equals("1")) {
                cmd.append(" --level=").append(level);
            }
            
            String risk = editTextRisk.getText().toString().trim();
            if (!risk.isEmpty() && !risk.equals("1")) {
                cmd.append(" --risk=").append(risk);
            }
        }
        
        executeInRootKali(cmd.toString());
        dismiss();
    }

    private void stopSqlmap() {
        executeInRootKali("pkill -f sqlmap");
        updateStopButtonState(false);
    }

    private void executeInRootKali(String command) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;
            TermuxService mTermuxService = activity.mTermuxService;
            TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient = activity.mTermuxTerminalSessionActivityClient;
            
            String sessionName = "Kali-Root";
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

            // Ensure we are in nethunter root environment
            if (TermuxActivity.mTerminalView != null) {
                TermuxActivity.mTerminalView.sendTextToTerminal("nethunter -r\n");
                TermuxActivity.mTerminalView.sendTextToTerminal(command + "\n");
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
