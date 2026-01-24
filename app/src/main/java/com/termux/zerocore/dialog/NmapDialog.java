package com.termux.zerocore.dialog;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class NmapDialog extends BaseDialogCentre {

    private EditText editTextTarget;
    private Button buttonScan;
    private Button buttonStop;
    private TextView stopHintText;
    
    private Switch switchAdvanced;
    private LinearLayout layoutAdvancedOptions;
    
    private CheckBox checkAllAdvanced;
    private CheckBox checkPingScan;
    private CheckBox checkServiceVersion;
    private CheckBox checkSkipDiscovery;
    private CheckBox checkNoDNS;
    private CheckBox checkIPv6;
    
    private CheckBox checkTopPorts;
    private CheckBox checkFastMode;
    private CheckBox checkNoRandomize;
    
    private Spinner spinnerTiming;

    public NmapDialog(Context context) {
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
        return R.layout.dialog_nmap;
    }

    private void initViews(View rootView) {
        editTextTarget = rootView.findViewById(R.id.editTextTarget);
        buttonScan = rootView.findViewById(R.id.buttonScan);
        buttonStop = rootView.findViewById(R.id.buttonStop);
        stopHintText = rootView.findViewById(R.id.stopHintText);
        
        switchAdvanced = rootView.findViewById(R.id.switchAdvanced);
        layoutAdvancedOptions = rootView.findViewById(R.id.layoutAdvancedOptions);
        
        checkAllAdvanced = rootView.findViewById(R.id.checkAllAdvanced);
        checkPingScan = rootView.findViewById(R.id.checkPingScan);
        checkServiceVersion = rootView.findViewById(R.id.checkServiceVersion);
        checkSkipDiscovery = rootView.findViewById(R.id.checkSkipDiscovery);
        checkNoDNS = rootView.findViewById(R.id.checkNoDNS);
        checkIPv6 = rootView.findViewById(R.id.checkIPv6);
        
        checkTopPorts = rootView.findViewById(R.id.checkTopPorts);
        checkFastMode = rootView.findViewById(R.id.checkFastMode);
        checkNoRandomize = rootView.findViewById(R.id.checkNoRandomize);
        
        spinnerTiming = rootView.findViewById(R.id.spinnerTiming);
        
        setupSpinners();
    }
    
    private void setupSpinners() {
        // Timing Templates
        String[] timings = new String[] {
            "默认 (T3)", "偏执的 (T0)", "偷偷摸摸的 (T1)", "有礼貌的 (T2)", "野蛮的 (T4)", "疯狂的 (T5)"
        };
        ArrayAdapter<String> timingAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, timings);
        timingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTiming.setAdapter(timingAdapter);
    }

    private void setupListeners() {
        buttonScan.setOnClickListener(v -> startNmap());
        buttonStop.setOnClickListener(v -> stopNmap());
        
        switchAdvanced.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutAdvancedOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void startNmap() {
        String target = editTextTarget.getText().toString().trim();
        
        if (target.isEmpty()) {
            Toast.makeText(mContext, "请输入目标 IP 或域名", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder cmd = new StringBuilder();
        // Check for nmap installation in Termux
        cmd.append("if ! command -v nmap > /dev/null 2>&1; then pkg install nmap -y; fi && ");
        
        cmd.append("nmap");
        
        if (switchAdvanced.isChecked()) {
            // Default to -sT for non-root users if no other scan type is implied
            // But actually, we don't need to specify -sT, it's default for unprivileged.
            
            if (checkAllAdvanced.isChecked()) cmd.append(" -sV -sC"); // Changed from -A to safe version
            if (checkPingScan.isChecked()) cmd.append(" -sn");
            if (checkServiceVersion.isChecked()) cmd.append(" -sV");
            if (checkSkipDiscovery.isChecked()) cmd.append(" -Pn");
            if (checkNoDNS.isChecked()) cmd.append(" -n");
            if (checkIPv6.isChecked()) cmd.append(" -6");
            
            if (checkTopPorts.isChecked()) cmd.append(" --top-ports 20");
            if (checkFastMode.isChecked()) cmd.append(" -F");
            if (checkNoRandomize.isChecked()) cmd.append(" -r");
            
            int timingIndex = spinnerTiming.getSelectedItemPosition();
            if (timingIndex > 0) {
                // Map index to T value
                // 0: Default (T3), 1: T0, 2: T1, 3: T2, 4: T4, 5: T5
                int[] timingMap = {3, 0, 1, 2, 4, 5};
                cmd.append(" -T").append(timingMap[timingIndex]);
            }
        }
        
        cmd.append(" ").append(target);
        
        executeInTermux(cmd.toString());
        dismiss();
    }

    private void stopNmap() {
        executeInTermux("pkill -f nmap");
        updateStopButtonState(false);
    }

    private void executeInTermux(String command) {
        if (mContext instanceof TermuxActivity) {
            TermuxActivity activity = (TermuxActivity) mContext;
            TermuxService mTermuxService = activity.mTermuxService;
            TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient = activity.mTermuxTerminalSessionActivityClient;
            
            // Use current session or create a new Termux session
            // We want standard Termux environment, not Kali
            
            if (TermuxActivity.mTerminalView != null) {
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