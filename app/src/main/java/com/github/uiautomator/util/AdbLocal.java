package com.github.uiautomator.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.RequiresApi;

import com.github.uiautomator.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdbLocal {
    private final long MAX_OUTPUT_BUFFER_SIZE = 1024 * 16;
    private final long OUTPUT_BUFFER_DELAY_MS = 100L;

    private Context _context = null;
    private String _adbPath = "";
    private String _scriptPath = "";

    private boolean _ready = false;
    private boolean _closed = false;

    private Process shellProcess = null;

    private File _outputBufferFile = null;

    public AdbLocal(Context context){
        this._context = context;
        this._adbPath = context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
        this._scriptPath = context.getExternalFilesDir(null) + "/script.sh";
        this._outputBufferFile = new File(Environment.getExternalStorageDirectory().getPath() + "/debug-adb-local.txt");
    }

    public boolean ready(){
        return _ready;
    }

    public boolean closed(){
        return _closed;
    }

    public long getOutputBufferSize() {
        return MAX_OUTPUT_BUFFER_SIZE;
    }

    public File outputBufferFile(){
        return _outputBufferFile;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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
        if (shellProcess == null || shellProcess.getOutputStream() == null) return;
        PrintStream printStream = new PrintStream(shellProcess.getOutputStream());
        printStream.println(msg);
        printStream.flush();
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
        List<String> cloneCommand = new ArrayList<>(command);
        cloneCommand.add(0, this._adbPath);
        return shell(redirect, cloneCommand);
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
    public void stop() throws IOException, InterruptedException {
        this._ready = false;
        debug("Destroying shell process");
        shellProcess.destroyForcibly();
        debug("Disconnecting all clients");
        adb(false, Collections.singletonList("disconnect")).waitFor();
        debug("Killing ADB server");
        adb(false, Collections.singletonList("kill-server")).waitFor();
        debug("Erasing all ADB server files");
        this._context.getFilesDir().deleteOnExit();
        this._context.getFilesDir().deleteOnExit();
        this._closed = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void initializeClient() throws IOException, InterruptedException {
        if (this._ready) return;
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

        sendToShellProcess("alias adb=\"" + this._adbPath + "\"");

        if (autoWireless && !secureSettingsGranted) {
            sendToShellProcess("echo 'NOTE: Granting secure settings permission for next time'");
            sendToShellProcess("pm grant " + BuildConfig.APPLICATION_ID + " android.permission.WRITE_SECURE_SETTINGS");
        }

        if (autoShell && autoPair)
            sendToShellProcess("echo 'NOTE: Dropped into ADB shell automatically'");
        else
            sendToShellProcess("echo 'NOTE: In unprivileged shell, not ADB shell'");

        if (!startupCommand.isEmpty()){
            sendToShellProcess(startupCommand);
        }

        _ready = true;
    }
}
