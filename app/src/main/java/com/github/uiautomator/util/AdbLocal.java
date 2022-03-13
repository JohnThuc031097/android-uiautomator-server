package com.github.uiautomator.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdbLocal {
    private final long MAX_OUTPUT_BUFFER_SIZE = 1024 * 16;
    private final long OUTPUT_BUFFER_DELAY_MS = 100L;

    private Context _context;
    private String _adbPath = "";
    private String _scriptPath = "";

    private MutableLiveData<Boolean> _ready;
    private MutableLiveData<Boolean> _closed;

    private Process shellProcess = null;

   public AdbLocal(Context context){
        this._context = context;
        this._adbPath = context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
        this._scriptPath = context.getExternalFilesDir(null) + "/script.sh";
   }

   public LiveData<Boolean> ready(){
       return _ready;
   }

   public LiveData<Boolean> closed(){
        return _closed;
   }

   public File outputBufferFile() throws IOException {
       File mFile = new File(Environment.getExternalStorageDirectory().getPath() + "/debug-adb-local.txt");
       if (mFile.createNewFile())
           mFile.deleteOnExit();
       return mFile;
   }

   public long getOutputBufferSize() {
        return MAX_OUTPUT_BUFFER_SIZE;
   }

    public void debug(String msg) throws IOException {
        synchronized(this.outputBufferFile()) {
            if (this.outputBufferFile().exists())
            {
                FileWriter fr = new FileWriter(this.outputBufferFile(), true);
                fr.write(">>> " + msg + System.lineSeparator());
                fr.close();
            }
        }
    }

    public void sendToShellProcess(String msg) {
        if (shellProcess == null || shellProcess.getOutputStream() == null){
            PrintStream printStream = new PrintStream(shellProcess.getOutputStream());
            printStream.println(msg);
            printStream.flush();
        }
    }

    public void sendScript(String code) throws IOException {
        File internalScript = new File(this._scriptPath);
        FileWriter fr = new FileWriter(internalScript, false);
        fr.write(code);
        fr.close();
        sendToShellProcess("sh " + internalScript.getAbsolutePath());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Process shell(Boolean redirect, List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(this._context.getFilesDir());
        if(redirect){
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(this.outputBufferFile());
        }
        processBuilder.environment().put("HOME", this._context.getFilesDir().getPath());
        processBuilder.environment().put("TMPDIR", this._context.getCacheDir().getPath());

        return processBuilder.start();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private Process adb(Boolean redirect, List<String> command) throws IOException {
       command.add(0, this._adbPath);
        return shell(redirect, command);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public void pair(String port, String pairingCode) throws IOException, InterruptedException {
        Process pairShell = adb(true, Arrays.asList("pair", "localhost:" + port));

        Thread.sleep(5000);

        PrintStream printStream = new PrintStream(pairShell.getOutputStream());
        printStream.println(pairingCode);
        printStream.flush();

        pairShell.waitFor(10, TimeUnit.SECONDS);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public void reset() throws IOException, InterruptedException {
        this._ready.postValue(false);
        FileWriter fr = new FileWriter(this.outputBufferFile(), false);
        fr.write("");
        fr.close();
        debug("Destroying shell process");
        shellProcess.destroyForcibly();
        debug("Disconnecting all clients");
        adb(false, Collections.singletonList("disconnect")).waitFor();
        debug("Killing ADB server");
        adb(false, Collections.singletonList("kill-server")).waitFor();
        debug("Erasing all ADB server files");
        this._context.getFilesDir().deleteOnExit();
        this._context.getFilesDir().deleteOnExit();
        this._closed.postValue(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void startShellDeathThread() {
        Handler mHandler = new Handler();
        mHandler.postDelayed(
                () -> {
                    try {
                        shellProcess.waitFor();
                        _ready.postValue(false);
                        debug("Shell is dead, resetting");
                        adb(false, Collections.singletonList("kill-server")).waitFor();
                        initializeClient();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                1000
        );
    }

   @RequiresApi(api = Build.VERSION_CODES.O)
   public void initializeClient() throws IOException, InterruptedException {
        if (_ready.getValue()) return;
        initializeADBShell(true, true, true, "echo 'Init adb local success'");
   }

   @RequiresApi(api = Build.VERSION_CODES.O)
   private void initializeADBShell(Boolean autoShell, Boolean autoPair, Boolean autoWireless, String startupCommand) throws IOException, InterruptedException {
       boolean secureSettingsGranted = this._context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;

       if (autoWireless) {
           debug("Enabling wireless debugging");
           if (secureSettingsGranted) {
               Settings.Global.putInt(
                       this._context.getContentResolver(),
                       "adb_wifi_enabled",
                       1
               );
               debug("Waiting a few moments...");
               Thread.sleep(3000);
           } else {
               debug("NOTE: Secure settings permission not granted yet");
               debug("NOTE: After first pair, it will auto-grant");
           }
       }
       if (autoPair) {
           debug("Starting ADB client");
           adb(false, Collections.singletonList("start-server")).waitFor();
           debug("Waiting for device respond (max 5m)");
           adb(false, Collections.singletonList("wait-for-device")).waitFor();
       }

       debug("Shelling into device");
       Process process = null;
       if (autoShell && autoPair){
           if (Build.SUPPORTED_ABIS[0].equals("arm64-v8a")){
               process = adb(true, Arrays.asList("-t", "1", "shell"));
           }else{
               process = adb(true, Collections.singletonList("shell"));
           }
       }else{
           process = shell(true, Arrays.asList("sh", "-l"));
       }

       if (process == null) {
           debug("Failed to open shell connection");
           return;
       }
       shellProcess = process;

       sendToShellProcess("alias adb=\"$this._adbPath\"");

       if (autoWireless && !secureSettingsGranted) {
           sendToShellProcess("echo 'NOTE: Granting secure settings permission for next time'");
           sendToShellProcess("pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS");
       }

       if (autoShell && autoPair)
           sendToShellProcess("echo 'NOTE: Dropped into ADB shell automatically'");
       else
           sendToShellProcess("echo 'NOTE: In unprivileged shell, not ADB shell'");

       if (!startupCommand.isEmpty()){
           sendToShellProcess(startupCommand);
       }

       _ready.postValue(true);

       startShellDeathThread();
   }
}
