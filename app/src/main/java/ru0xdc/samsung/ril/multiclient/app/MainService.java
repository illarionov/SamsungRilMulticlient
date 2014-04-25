/*
 * Copyright (C) 2014 Alexey Illarionov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru0xdc.samsung.ril.multiclient.app;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.cyanogenmod.samsungservicemode.OemCommands;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru0xdc.samsung.ril.multiclient.app.rilexecutor.DetectResult;
import ru0xdc.samsung.ril.multiclient.app.rilexecutor.OemRilExecutor;
import ru0xdc.samsung.ril.multiclient.app.rilexecutor.RawResult;
import ru0xdc.samsung.ril.multiclient.app.rilexecutor.SamsungMulticlientRilExecutor;


public class MainService extends Service {
    private static final String TAG = "SamsungMulticlientService";

    private static final int ID_REQUEST_START_SERVICE_MODE_COMMAND = 1;
    private static final int ID_REQUEST_FINISH_SERVICE_MODE_COMMAND = 2;
    private static final int ID_REQUEST_PRESS_A_KEY = 3;
    private static final int ID_REQUEST_REFRESH = 4;

    private static final int ID_RESPONSE = 101;
    private static final int ID_RESPONSE_FINISH_SERVICE_MODE_COMMAND = 102;
    private static final int ID_RESPONSE_PRESS_A_KEY = 103;

    private static final int REQUEST_TIMEOUT = 10000; // ms
    private static final int REQUEST_VERSION_TIMEOUT = 300; // ms

    private final IBinder mBinder = new LocalBinder();

    private final ConditionVariable mRequestCondvar = new ConditionVariable();

    private final Object mLastResponseLock = new Object();

    private volatile List<String> mLastResponse;

    private DetectResult mRilExecutorDetectResult;
    private OemRilExecutor mRequestExecutor;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandlerThread = new HandlerThread("ServiceModeSeqHandler");
        mHandlerThread.start();

        Looper l = mHandlerThread.getLooper();
        mHandler = new Handler(l, new MyHandler());

        mRequestExecutor = new SamsungMulticlientRilExecutor();
        mRilExecutorDetectResult = mRequestExecutor.detect();
        if (!mRilExecutorDetectResult.available) {
            Log.e(TAG, "Samsung multiclient ril not available: " + mRilExecutorDetectResult.error);
            mRequestExecutor = null;
        } else {
            mRequestExecutor.start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRequestExecutor != null) {
            mRequestExecutor.stop();
            mRequestExecutor = null;
        }
        mHandler = null;
        mHandlerThread.quit();
        mHandlerThread = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public DetectResult getRilExecutorStatus() {
        return mRilExecutorDetectResult;
    }

    public List<String> getCpSwVersion() {
        String version = "";
        String compileDate = "";
        String compileTime = "";
        List<String> strings = executeServiceModeCommand(OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_SW_VERSION_ENTER, null, REQUEST_VERSION_TIMEOUT);
        Pattern versionPattern = Pattern.compile("CP\\s+SW\\s+VERSION\\s*[:;]\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern compileDatePattern = Pattern.compile("CP\\s+SW\\s+COMPILE\\s+DATE\\s*[:;]\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern compileTimePattern = Pattern.compile("CP\\s+SW\\s+COMPILE\\s+TIME\\s*[:;]\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);

        for (String str : strings) {
            if (str == null) continue;
            Matcher matcher = versionPattern.matcher(str);
            if (matcher.matches()) version = matcher.group(1);
            matcher = compileDatePattern.matcher(str);
            if (matcher.matches()) compileDate = matcher.group(1);
            matcher = compileTimePattern.matcher(str);
            if (matcher.matches()) compileTime = matcher.group(1);
        }
        return new ArrayList<>(Arrays.asList(version, compileDate, compileTime));

    }

    public String getFtaSwVersion() {
        List<String> strings = executeServiceModeCommand(OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_FTA_SW_VERSION_ENTER, null, REQUEST_VERSION_TIMEOUT);
        Pattern ftaSwVersionPattern = Pattern.compile("FTA\\s+SW\\s+VERSION\\s*[:;]\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
        for (String str : strings) {
            if (str == null) continue;
            Matcher matcher = ftaSwVersionPattern.matcher(str);
            if (matcher.matches()) return matcher.group(1);
        }
        return null;
    }

    public String getFtaHwVersion() {
        List<String> strings = executeServiceModeCommand(OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_FTA_HW_VERSION_ENTER, null, REQUEST_VERSION_TIMEOUT);
        Pattern ftaHwVersionPattern = Pattern.compile("FTA\\s+HW\\s+VERSION\\s*[:;]\\s*(.+)\\s*$", Pattern.CASE_INSENSITIVE);
        for (String str : strings) {
            if (str == null) continue;
            Matcher matcher = ftaHwVersionPattern.matcher(str);
            if (matcher.matches()) return matcher.group(1);
        }
        return null;
    }

    public List<String> getCipheringInfo() {
        return executeServiceModeCommand(
                OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_CIPHERING_PROTECTION_ENTER,
                null
        );
    }

    public List<String> getAllVersion() {
        return executeServiceModeCommand(
                OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_ALL_VERSION_ENTER,
                null
        );
    }

    public List<String> getBasicInfo() {
        KeyStep getBasicInfoKeySeq[] = new KeyStep[]{
                new KeyStep('\0', false),
                new KeyStep('1', false), // [1] DEBUG SCREEN
                new KeyStep('1', true), // [1] BASIC INFORMATION
        };

        return executeServiceModeCommand(
                OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_ENTER,
                Arrays.asList(getBasicInfoKeySeq)
        );
    }

    public List<String> getNeighbours() {
        KeyStep getNeighboursKeySeq[] = new KeyStep[]{
                new KeyStep('\0', false),
                new KeyStep('1', false), // [1] DEBUG SCREEN
                new KeyStep('4', true), // [4] NEIGHBOUR CELL
        };

        return executeServiceModeCommand(
                OemCommands.OEM_SM_TYPE_TEST_MANUAL,
                OemCommands.OEM_SM_TYPE_SUB_ENTER,
                Arrays.asList(getNeighboursKeySeq)
        );

    }

    private List<String> executeServiceModeCommand(int type, int subtype,
                                                   java.util.Collection<KeyStep> keySeqence) {
        return executeServiceModeCommand(type, subtype, keySeqence, REQUEST_TIMEOUT);
    }

    private synchronized List<String> executeServiceModeCommand(int type, int subtype,
                                                                java.util.Collection<KeyStep> keySeqence, int timeout) {
        if (mRequestExecutor == null) return Collections.emptyList();

        mRequestCondvar.close();
        mHandler.obtainMessage(ID_REQUEST_START_SERVICE_MODE_COMMAND,
                type,
                subtype,
                keySeqence).sendToTarget();
        if (!mRequestCondvar.block(timeout)) {
            Log.e(TAG, "request timeout");
            return Collections.emptyList();
        } else {
            synchronized (mLastResponseLock) {
                return mLastResponse;
            }
        }
    }

    public class LocalBinder extends Binder {
        MainService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MainService.this;
        }
    }

    private static class KeyStep {
        public final char keychar;
        public boolean captureResponse;

        public KeyStep(char keychar, boolean captureResponse) {
            this.keychar = keychar;
            this.captureResponse = captureResponse;
        }

        public static KeyStep KEY_START_SERVICE_MODE = new KeyStep('\0', true);
    }

    private class MyHandler implements Handler.Callback {

        private int mCurrentType;
        private int mCurrentSubtype;

        private Queue<KeyStep> mKeySequence;

        @Override
        public boolean handleMessage(Message msg) {
            byte[] requestData;
            Message responseMsg;
            KeyStep lastKeyStep;

            switch (msg.what) {
                case ID_REQUEST_START_SERVICE_MODE_COMMAND:
                    mCurrentType = msg.arg1;
                    mCurrentSubtype = msg.arg2;
                    mKeySequence = new ArrayDeque<KeyStep>(3);
                    if (msg.obj != null) {
                        mKeySequence.addAll((java.util.Collection<KeyStep>) msg.obj);
                    } else {
                        mKeySequence.add(KeyStep.KEY_START_SERVICE_MODE);
                    }
                    synchronized (mLastResponseLock) {
                        mLastResponse = new ArrayList<>();
                    }
                    requestData = OemCommands.getEnterServiceModeData(
                            mCurrentType, mCurrentSubtype, OemCommands.OEM_SM_ACTION);
                    responseMsg = mHandler.obtainMessage(ID_RESPONSE);
                    mRequestExecutor.invokeOemRilRequestRaw(requestData, responseMsg);
                    break;
                case ID_REQUEST_FINISH_SERVICE_MODE_COMMAND:
                    requestData = OemCommands.getEndServiceModeData(mCurrentType);
                    responseMsg = mHandler.obtainMessage(ID_RESPONSE_FINISH_SERVICE_MODE_COMMAND);
                    mRequestExecutor.invokeOemRilRequestRaw(requestData, responseMsg);
                    break;
                case ID_REQUEST_PRESS_A_KEY:
                    requestData = OemCommands.getPressKeyData(msg.arg1, OemCommands.OEM_SM_ACTION);
                    responseMsg = mHandler.obtainMessage(ID_RESPONSE_PRESS_A_KEY);
                    mRequestExecutor.invokeOemRilRequestRaw(requestData, responseMsg);
                    break;
                case ID_REQUEST_REFRESH:
                    requestData = OemCommands.getPressKeyData('\0', OemCommands.OEM_SM_QUERY);
                    responseMsg = mHandler.obtainMessage(ID_RESPONSE);
                    mRequestExecutor.invokeOemRilRequestRaw(requestData, responseMsg);
                    break;
                case ID_RESPONSE:
                    lastKeyStep = mKeySequence.poll();
                    try {
                        RawResult result = (RawResult) msg.obj;
                        if (result == null) {
                            Log.e(TAG, "result is null");
                            break;
                        }
                        if (result.exception != null) {
                            Log.e(TAG, "", result.exception);
                            break;
                        }
                        if (result.result == null) {
                            Log.v(TAG, "No need to refresh.");
                            break;
                        }
                        if (lastKeyStep.captureResponse) {
                            synchronized (mLastResponseLock) {
                                mLastResponse.addAll(Utils.unpackListOfStrings(result.result));
                            }
                        }
                    } finally {
                        if (mKeySequence.isEmpty()) {
                            mHandler.obtainMessage(ID_REQUEST_FINISH_SERVICE_MODE_COMMAND).sendToTarget();
                        } else {
                            mHandler.obtainMessage(ID_REQUEST_PRESS_A_KEY, mKeySequence.element().keychar, 0).sendToTarget();
                        }
                    }
                    break;
                case ID_RESPONSE_PRESS_A_KEY:
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(ID_REQUEST_REFRESH), 10);
                    break;
                case ID_RESPONSE_FINISH_SERVICE_MODE_COMMAND:
                    mRequestCondvar.open();
                    break;

            }
            return true;
        }
    }
}
