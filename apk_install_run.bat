adb install -r -d app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -r -e debug false -e class com.github.uiautomator.stub.Stub \com.github.uiautomator.test/androidx.test.runner.AndroidJUnitRunner