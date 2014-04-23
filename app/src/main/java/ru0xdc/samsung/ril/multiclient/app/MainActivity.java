package ru0xdc.samsung.ril.multiclient.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

import ru0xdc.samsung.ril.multiclient.app.rilexecutor.DetectResult;
import ru0xdc.ssp.samsumrilmulticlient.app.R;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    boolean mBound = false;
    MainService mService = null;

    private TextView mCpSwVersion, mFtaSwVersion, mFtaHwVersion, mVersionTextView,
            mBasicInfoTextView, mNeighboursTextView, mCipheringInfoTextView;
    private ProgressBar mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCpSwVersion = (TextView) findViewById(R.id.cpSwVersion);
        mFtaSwVersion = (TextView) findViewById(R.id.ftaSwVersion);
        mFtaHwVersion = (TextView) findViewById(R.id.ftaHwVersion);
        mVersionTextView = (TextView) findViewById(R.id.versionAll);
        mBasicInfoTextView = (TextView) findViewById(R.id.basic_info);
        mNeighboursTextView = (TextView) findViewById(R.id.neighbours);
        mCipheringInfoTextView = (TextView) findViewById(R.id.ciphering_info);
        mProgress = (ProgressBar) findViewById(R.id.progress);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MainService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void runTest(View v) {
        if (!mBound) return;

        TextView errorTextView = (TextView)findViewById(R.id.error);
        DetectResult rilStatus = mService.getRilExecutorStatus();
        if (!rilStatus.available) {
            errorTextView.setText(rilStatus.error);
            errorTextView.setVisibility(View.VISIBLE);
        } else {
            errorTextView.setVisibility(View.GONE);
            new RequestOemInfoTask().execute();
        }
    }

    void updateCipherInfo() {
        final List<String> info = mService.getCipheringInfo();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (info == null) {
                    mCipheringInfoTextView.setText("null");
                } else {
                    mCipheringInfoTextView.setText(TextUtils.join("\n", info));
                }
            }
        });
    }

    void updateMainVersion() {
        final List<String> cpSwVersion = mService.getCpSwVersion();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCpSwVersion.setText("CP SW version: " + cpSwVersion.get(0)
                                + "\nCompile date: " + cpSwVersion.get(1)
                                + "\nCompile time: " + cpSwVersion.get(2)
                );

                mFtaHwVersion.setText("FTA HW Version: " + mService.getFtaHwVersion());
                mFtaSwVersion.setText("FTA SW Version: " + mService.getFtaSwVersion());
            }
        });
    }

    void updateAllVersion() {
        final List<String> info = mService.getAllVersion();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (info == null) {
                    mVersionTextView.setText("null");
                } else {
                    mVersionTextView.setText(TextUtils.join("\n", info));
                }
            }
        });
    }

    void updateNeighbours() {
        final List<String> info = mService.getNeighbours();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (info == null) {
                    mNeighboursTextView.setText("null");
                } else {
                    mNeighboursTextView.setText(TextUtils.join("\n", info));
                }
            }
        });
    }

    void updateBasicInfo() {
        final List<String> info = mService.getBasicInfo();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (info == null) {
                    mBasicInfoTextView.setText("null");
                } else {
                    mBasicInfoTextView.setText(TextUtils.join("\n", info));
                }
            }
        });
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MainService.LocalBinder binder = (MainService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mBound = false;
        }
    };

    private class RequestOemInfoTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgress.setMax(5);
            mProgress.setProgress(0);
            findViewById(R.id.button_load).setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!mBound) return null;

            updateMainVersion();
            publishProgress(1);

            updateAllVersion();
            publishProgress(2);

            updateBasicInfo();
            publishProgress(3);

            updateNeighbours();
            publishProgress(4);

            updateCipherInfo();
            publishProgress(5);

            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgress.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mProgress.setProgress(0);
            findViewById(R.id.button_load).setEnabled(true);
        }
    }
}
