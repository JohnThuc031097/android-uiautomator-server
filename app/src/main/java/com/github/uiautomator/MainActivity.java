package com.github.uiautomator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.permission.FloatWindowManager;
import com.github.uiautomator.util.MemoryManager;
import com.github.uiautomator.util.OkhttpManager;
import com.github.uiautomator.util.Permissons4App;
import com.jaredrummler.ktsh.Shell;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends Activity {
    private final String TAG = "ATXMainActivity";
    private final int PORT = 7912;
    private final String ATX_AGENT_URL = "http://127.0.0.1:" + PORT;
    private final Shell shell = new Shell("sh");


    private TextView tvInStorage;
    private TextView textViewIP;
    private TextView tvAgentStatus;
    private TextView tvAutomatorStatus;
    private TextView tvAutomatorMode;
    private TextView tvServiceMessage;

    private final WindowManager windowManager = null;
    private final boolean isWindowShown = false;
    private FloatView floatView;

    private final OkhttpManager okhttpManager = OkhttpManager.getSingleton();

    private static final class TextViewSetter implements Runnable {
        private final TextView v;
        private final String what;
        private final int color;

        TextViewSetter(TextView v, String what, int color) {
            this.v = v;
            this.what = what;
            this.color = color;
        }

        TextViewSetter(TextView v, String what) {
            this(v, what, Color.BLACK);
        }

        @Override
        public void run() {
            v.setText(what);
            v.setTextColor(color);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAgentStatus = findViewById(R.id.atx_agent_status);
        tvAutomatorStatus = findViewById(R.id.uiautomator_status);
        tvAutomatorMode = findViewById(R.id.uiautomator_mode);
        tvServiceMessage = findViewById(R.id.serviceMessage);

        Button btnFinish = findViewById(R.id.btn_finish);
        btnFinish.setOnClickListener(view -> {
            stopService(new Intent(MainActivity.this, Service.class));
            finish();
        });

        Button btnIdentify = findViewById(R.id.btn_identify);
        btnIdentify.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, IdentifyActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("theme", "RED");
            intent.putExtras(bundle);
            startActivity(intent);
        });

        findViewById(R.id.accessibility).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        findViewById(R.id.development_settings).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));

        Intent intent = getIntent();
        boolean isHide = intent.getBooleanExtra("hide", false);
        if (isHide) {
            Log.i(TAG, "launch args hide:true, move to background");
            moveTaskToBack(true);
        }
        textViewIP = findViewById(R.id.ip_address);
        tvInStorage = findViewById(R.id.in_storage);

        String[] permissions = new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        Permissons4App.initPermissions(this, permissions);
    }
    @Override
    protected void onStart() {
        super.onStart();
    }
    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();
        checkAtxAgentStatus(null);
        checkUiautomatorStatus(null);

        tvInStorage.setText(Formatter.formatFileSize(this, MemoryManager.getAvailableInternalMemorySize()) + "/" + Formatter.formatFileSize(this, MemoryManager.getTotalExternalMemorySize()));
        checkNetworkAddress(null);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // must unbind service, otherwise it will leak memory
        // connection no need to set it to null
        Log.i(TAG, "unbind service");
    }
    //    =========================
//    ====== UIAutomator ======
//    =========================
    public void checkUiautomatorStatus(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .get()
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvAutomatorStatus, "UIAutomator Stopped"));
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        this.onFailure(call, new IOException("UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    JSONObject obj = new JSONObject(responseData);
                    boolean running = obj.getBoolean("running");
                    String status = running ? "UIAutomator Running" : "UIAutomator Stopped";
                    runOnUiThread(new TextViewSetter(tvAutomatorStatus, status));
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
                    try {
                        Class.forName("com.github.uiautomator.stub.Stub");
                        runOnUiThread(new TextViewSetter(tvAutomatorMode, "Normal service mode"));
                    } catch (ClassNotFoundException e) {
                        // TODO The pop-up box should be forced to exit after onResume check
                        runOnUiThread(new TextViewSetter(tvAutomatorMode, "Unable to serve non-am instrument startup", Color.RED));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    public void stopUiautomator(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .delete()
                .build();
        okhttpManager.newCall(request, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                uiToaster("UIAutomator already stopped ");
                checkUiautomatorStatus(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                checkUiautomatorStatus(null);
            }
        });
    }
    public void startUiautomator(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/uiautomator")
                .post(RequestBody.create(new byte[0]))
                .build();
        okhttpManager.newCall(request, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                uiToaster("UIAutomator not starting");
                checkUiautomatorStatus(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                uiToaster("UIAutomator started");
                checkUiautomatorStatus(null);
            }
        });
    }
    public void testUiautomator(View view) {
        String json = "{" +
                "            \"jsonrpc\": \"2.0\",\n" +
                "            \"id\": \"14d3bbb25360373624ea5b343c5abb1f\", \n" +
                "            \"method\": \"dumpWindowHierarchy\",\n" +
                "            \"params\": [false]\n" +
                "        }";
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/jsonrpc/0")
                .post(RequestBody.create(json,MediaType.parse("application/json")))
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        runOnUiThread(new TextViewSetter(tvServiceMessage, "UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
//                    JSONObject obj = new JSONObject(responseData);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    public void clipboardUiautomator(View view){
        String json = "{" +
                "            \"jsonrpc\": \"2.0\",\n" +
                "            \"id\": \"14d3bbb25360373624ea5b343c5abb1f\", \n" +
                "            \"method\": \"getClipboard\" \n" +
                "        }";
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/jsonrpc/0")
                .post(RequestBody.create(json,MediaType.parse("application/json")))
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        runOnUiThread(new TextViewSetter(tvServiceMessage, "UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    public void deviceUiautomator(View view){
        String json = "{" +
                "            \"jsonrpc\": \"2.0\",\n" +
                "            \"id\": \"14d3bbb25360373624ea5b343c5abb1f\", \n" +
                "            \"method\": \"deviceInfo\" \n" +
                "        }";
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/jsonrpc/0")
                .post(RequestBody.create(json,MediaType.parse("application/json")))
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        runOnUiThread(new TextViewSetter(tvServiceMessage, "UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    public void getObjectUiautomator(View view){
        String json = "{" +
                "            \"jsonrpc\": \"2.0\",\n" +
                "            \"id\": \"1\", \n" +
                "            \"method\": \"objInfo\",\n" +
                "            \"params\": [" +
                "                           {" +
                "                               \"text\":\"ENABLE\"," +
                "                               \"packageName\":\"com.github.uiautomator\"" +
                "                           }" +
                "                       ]" +
                "        }";
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/jsonrpc/0")
                .post(RequestBody.create(json,MediaType.parse("application/json")))
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        runOnUiThread(new TextViewSetter(tvServiceMessage, "UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    public void testFuncUiautomator(View view) {
        String json = "{" +
                "            \"jsonrpc\": \"2.0\",\n" +
                "            \"id\": \"1\", \n" +
                "            \"method\": \"dumpWindowHierarchy\",\n" +
                "            \"params\": [false]\n" +
                "        }";
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/jsonrpc/0")
                .post(RequestBody.create(json,MediaType.parse("application/json")))
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.body() == null || !response.isSuccessful()) {
                        runOnUiThread(new TextViewSetter(tvServiceMessage, "UIAutomator not responding!"));
                        return;
                    }
                    String responseData = response.body().string();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, responseData));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
//    ======================
//    ===== ATX-Agent ======
//    ======================
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void startAtxAgentStatus(View view) {
        runOnUiThread(() ->{
            try {
                Shell.Command.Result result = shell.run("am instrument -w -r -e debug false -e class com.github.uiautomator.stub.Stub \\com.github.uiautomator.test/androidx.test.runner.AndroidJUnitRunner");
                if (result.isSuccess()){
                    runOnUiThread(new TextViewSetter(tvServiceMessage, result.stdout()));
                }else{
                    runOnUiThread(new TextViewSetter(tvServiceMessage, result.stderr()));
                }
            } catch (Shell.ClosedException e) {
                e.printStackTrace();
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }
//            try {
//                Class<?> clsSelector = Class.forName("com.github.uiautomator.stub.Selector");
////                Class<?> clsObjInfo = Class.forName("com.github.uiautomator.stub.ObjInfo");
//                Class<?> clsAutomatorServiceImpl = Class.forName("com.github.uiautomator.stub.AutomatorServiceImpl");
//                Object obj = clsAutomatorServiceImpl.newInstance();
//                Object selectorValue = new ObjectMapper().readValue("{\"text\":\"CHECK\"}", clsSelector);
//                Method objInfo = obj.getClass().getDeclaredMethod("objInfo", clsSelector);
//                Object resultObjInfo = objInfo.invoke(obj, selectorValue);
//                String result = new ObjectMapper().writeValueAsString(resultObjInfo);
//                runOnUiThread(new TextViewSetter(tvServiceMessage,result));
//            } catch (Exception e){
//                e.printStackTrace();
//                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
//            }
        });
    }

    public void checkAtxAgentStatus(View view) {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/ping")
                .get()
                .build();
        okhttpManager.newCall(request, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(new TextViewSetter(tvAgentStatus, "AtxAgent Stopped"));
                runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(new TextViewSetter(tvAgentStatus, "AtxAgent Running"));
                try {
                    runOnUiThread(new TextViewSetter(tvServiceMessage, response.body().string()));
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(new TextViewSetter(tvServiceMessage, e.toString()));
                }
            }
        });
    }
    private void stopAtxAgent() {
        Request request = new Request.Builder()
                .url(ATX_AGENT_URL + "/stop")
                .get()
                .build();
        okhttpManager.newCall(request, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                uiToaster("AtxAgent already stopped");
                checkAtxAgentStatus(null);
            }

            @Override
            public void onResponse(Call call, Response response) {
                uiToaster("AtxAgent stopped");
                checkAtxAgentStatus(null);
            }
        });
    }
    public void atxAgentStopConfirm(View view) {
        AlertDialog.Builder localBuilder = new AlertDialog.Builder(this);
        localBuilder.setTitle("Stopping AtxAgent");
        localBuilder.setMessage("ATX-Agent must be started via adb next time");
        localBuilder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopAtxAgent();
            }
        });
        localBuilder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        localBuilder.show();
    }

//    =====================
//    ==== Float Window ===
//    =====================

    public void showFloatWindow(View view) {

        boolean floatEnabled = FloatWindowManager.getInstance().checkFloatPermission(MainActivity.this);
        if (!floatEnabled) {
            Log.i(TAG, "float permission not checked");
            return;
        }
        if (floatView == null) {
            floatView = new FloatView(MainActivity.this);
        }
        floatView.show();
    }
    public void dismissFloatWindow(View view) {
        if (floatView != null) {
            Log.d(TAG, "remove floatView immediate");
            floatView.hide();
        }
    }

//    =====================
//    =====================
//    =====================

    public void checkNetworkAddress(View v) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        String ipStr = (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        textViewIP.setText(ipStr);
        textViewIP.setTextColor(Color.BLUE);
    }
    private void uiToaster(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissons4App.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
