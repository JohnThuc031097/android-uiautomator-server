/*
 * The MIT License (MIT)
 * Copyright (c) 2015 xiaocong@gmail.com, 2018 codeskyblue@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.github.uiautomator.stub;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Use JUnit test to start the uiautomator jsonrpc server.
 *
 * @author xiaocong@gmail.com
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 23)
public class Stub {
//    private final String TAG = "UIAUTOMATOR";
//    private static final int LAUNCH_TIMEOUT = 5000;
//    // http://www.jsonrpc.org/specification#error_object
//    private static final int CUSTOM_ERROR_CODE = -32001;
//
//    int PORT = 9008;
//    AutomatorHttpServer server = new AutomatorHttpServer(PORT);
//
////    @Before
//    private void setUp() throws Exception {
////        launchService();
////        JsonRpcServer jrs = new JsonRpcServer(new ObjectMapper(), new AutomatorServiceImpl(), AutomatorService.class);
////        jrs.setShouldLogInvocationErrors(true);
////        jrs.setErrorResolver((throwable, method, list) -> {
////            String data = throwable.getMessage();
////            if (!throwable.getClass().equals(UiObjectNotFoundException.class)) {
////                throwable.printStackTrace();
////                StringWriter sw = new StringWriter();
////                throwable.printStackTrace(new PrintWriter(sw));
////                data = sw.toString();
////            }
////            return new ErrorResolver.JsonError(CUSTOM_ERROR_CODE, throwable.getClass().getName(), data);
////        });
////        server.route("/jsonrpc/0", jrs);
////        server.start();
//    }
//
//    private String launchPackage(String packageName) {
//        try {
//            Log.i(TAG, "Launch " + packageName);
//            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
//            Context context = InstrumentationRegistry.getInstrumentation().getContext();
//            final Intent intent = context.getPackageManager()
//                    .getLaunchIntentForPackage(packageName);
//            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//            context.startActivity(intent);
//
//            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), LAUNCH_TIMEOUT);
//            device.pressHome();
//        }catch (Exception e){
//            e.printStackTrace();
//            return e.toString();
//        }
//        return "OK";
//    }
//
//    private void launchService() throws RemoteException {
//        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
//        Context context = InstrumentationRegistry.getInstrumentation().getContext();
//        device.wakeUp();
//
//        // Wait for launcher
//        String launcherPackage = device.getLauncherPackageName();
//        Boolean ready = device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
//        if (!ready) {
//            Log.i(TAG, "Wait for launcher timeout");
//            return;
//        }
//
//        Log.d("Launch service");
//        startMonitorService(context);
//    }
//
//    private void startMonitorService(Context context) {
//        Intent intent = new Intent("com.github.uiautomator.ACTION_START");
//        intent.setPackage("com.github.uiautomator"); // fix error: Service Intent must be explicit
//        context.startService(intent);
//    }
//
////    @After
//    private void tearDown() {
////        server.stop();
//        Context context = InstrumentationRegistry.getInstrumentation().getContext();
//        stopMonitorService(context);
//    }
//
//    private void stopMonitorService(Context context) {
//        Intent intent = new Intent("com.github.uiautomator.ACTION_STOP");
//        intent.setPackage("com.github.uiautomator");
//        context.startService(intent);
//    }

    @Test
    @LargeTest
    public void testUIAutomatorStub() throws InterruptedException {
//        while (server.isAlive()) {
        while (true) {
            Thread.sleep(100);
        }
    }
}