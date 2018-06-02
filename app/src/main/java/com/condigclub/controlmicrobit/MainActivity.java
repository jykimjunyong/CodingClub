package com.condigclub.controlmicrobit;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BBC";

    NRF52BLESensor nrf52BLESensor;
    Handler ble_handler;
    Handler ui_handler;
    boolean isServiceConntected = false;
    DataReceiver mDataReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //BLE Information
        Thread t = new Thread() {
            public void run() {
                Log.d("thread", "Creating handler ...");
                Looper.prepare();
                ble_handler = new Handler();
                Looper.loop();
                Log.d("thread", "Looper thread ends");
            }
        };
        t.start();
        while (ble_handler == null) {
            // let it get setup
        }
        ui_handler = new Handler();

        Intent intent = getIntent();

        final String deviceAddress = intent
                .getStringExtra(NRF52BLESensor.EXTRAS_DEVICE_ADDRESS);
        final String deviceName = intent
                .getStringExtra(NRF52BLESensor.EXTRAS_DEVICE_NAME);

        ble_handler.post(new Runnable() {
            public void run() {
                //Log.d(TAG, "Creating nrf52BLESensor");
                nrf52BLESensor = new NRF52BLESensor(getApplicationContext(), deviceAddress, deviceName);
            }
        });

    }

    @Override
    public void onResume()
    {
        super.onResume();

        if ( Build.VERSION.SDK_INT >= 23 ) {
            try {

                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            101);
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.BLUETOOTH}, 101);
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 101);
                }


            } catch (Exception e) {

            }
        }

        mDataReceiver = new DataReceiver();
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(
                        mDataReceiver,
                        new IntentFilter(
                                "com.codingclub.microbit.BLE_CONN"));

        ble_handler.postDelayed(new Runnable() {
            public void run() {
                nrf52BLESensor.resume();
            }
        }, 200);

    }

    @Override
    public void onPause()
    {

        nrf52BLESensor.pause();
        if (mDataReceiver != null) {
            LocalBroadcastManager.getInstance(this)
                    .unregisterReceiver(mDataReceiver);
            mDataReceiver = null;
        }

        super.onPause();
    }

    public void clickForward(View view) {
        nrf52BLESensor.writeConfig("F\n".getBytes());
    }
    public void clickBackward(View view) {
        nrf52BLESensor.writeConfig("B\n".getBytes());
    }
    public void clickLeft(View view) {
        nrf52BLESensor.writeConfig("L\n".getBytes());
    }
    public void clickRight(View view) {
        nrf52BLESensor.writeConfig("R\n".getBytes());
    }
    public void clickStop(View view) {
        nrf52BLESensor.writeConfig("S\n".getBytes());
    }

    private class DataReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            try {
                if ("com.codingclub.microbit.BLE_CONN".equals(intent
                        .getAction())) {
                    isServiceConntected = intent.getBooleanExtra("STATE", false);
                    Log.d(TAG, "MainActivity : Service Connected :" + isServiceConntected);

                    if ( isServiceConntected ) {
                        //String str = "TestStr\n";
                        //nrf52BLESensor.writeConfig(str.getBytes());
                    }
                    else {
                            nrf52BLESensor.pause();
                            //Try to Reconnect
                            nrf52BLESensor.resume();

                    }
                }
            } catch (NullPointerException e) {
                Log.d(TAG, "Intent:" + intent);
            }

        }
    }
}
