package com.termux.zerocore.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdateChecker {
    private static final String TAG = "AppUpdateChecker";
    // TODO: 请在服务器上创建此 JSON 文件，内容格式参考 kali-nethunter-term-main 的 latest_version_nhterm.json
    private static final String UPDATE_CHECK_URL = "https://xheishou.com/update/latest_version_zerotermux.json";
    
    public static boolean hasNetworkPermission(Context context) {
        return context.checkCallingOrSelfPermission(android.Manifest.permission.INTERNET) == 
               PackageManager.PERMISSION_GRANTED;
    }

    public interface UpdateCheckListener {
        void onUpdateAvailable(String latestVersion, String downloadUrl, String releaseNotes, String detailsUrl);
        void onNoUpdateAvailable();
        void onError(String errorMessage);
    }

    public static void checkForUpdate(Context context, UpdateCheckListener listener) {
        new UpdateCheckTask(context, listener).execute();
    }

    private static class UpdateCheckTask extends AsyncTask<Void, Void, UpdateResult> {
        private final Context context;
        private final UpdateCheckListener listener;

        UpdateCheckTask(Context context, UpdateCheckListener listener) {
            this.context = context;
            this.listener = listener;
        }

        @Override
        protected UpdateResult doInBackground(Void... voids) {
            try {
                if (!hasNetworkPermission(context)) {
                    return new UpdateResult(false, null, null, null, null, "No network permission");
                }

                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                String currentVersion = pInfo.versionName;

                URL url = new URL(UPDATE_CHECK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    String latestVersion = json.getString("latestVersion");
                    String downloadUrl = json.getString("downloadUrl");
                    String releaseNotes = json.getString("releaseNotes");
                    String detailsUrl = json.optString("detailsUrl", null);

                    if (isNewVersionAvailable(currentVersion, latestVersion)) {
                        return new UpdateResult(true, latestVersion, downloadUrl, releaseNotes, detailsUrl, null);
                    } else {
                        return new UpdateResult(false, null, null, null, null, null);
                    }
                } else {
                    return new UpdateResult(false, null, null, null, null, "Server error: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                Log.e(TAG, "Check update failed: " + e.getMessage());
                return new UpdateResult(false, null, null, null, null, e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(UpdateResult result) {
            if (result.hasError) {
                listener.onError(result.errorMessage);
            } else if (result.updateAvailable) {
                listener.onUpdateAvailable(result.latestVersion, result.downloadUrl, result.releaseNotes, result.detailsUrl);
            } else {
                listener.onNoUpdateAvailable();
            }
        }
    }

    private static boolean isNewVersionAvailable(String currentVersion, String latestVersion) {
        try {
            String[] currentParts = currentVersion.split("\\.");
            String[] latestParts = latestVersion.split("\\.");

            int maxLength = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < maxLength; i++) {
                int current = (i < currentParts.length) ? Integer.parseInt(currentParts[i]) : 0;
                int latest = (i < latestParts.length) ? Integer.parseInt(latestParts[i]) : 0;

                if (latest > current) {
                    return true;
                } else if (latest < current) {
                    return false;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return !currentVersion.equals(latestVersion);
        }
    }

    private static class UpdateResult {
        boolean updateAvailable;
        String latestVersion;
        String downloadUrl;
        String releaseNotes;
        String detailsUrl;
        String errorMessage;
        boolean hasError;

        UpdateResult(boolean updateAvailable, String latestVersion, String downloadUrl, String releaseNotes, String detailsUrl, String errorMessage) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.releaseNotes = releaseNotes;
            this.detailsUrl = detailsUrl;
            this.errorMessage = errorMessage;
            this.hasError = errorMessage != null;
        }
    }
}
