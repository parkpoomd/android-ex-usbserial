package com.example.deer.exappusbserial;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.deer.exappusbserial.service.UsbSerialService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // Notification from UsbSerialService will be received here.
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbSerialService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private UsbSerialService usbSerialService;
    private MyHandler mHandler;

    TextView tvDisplayData;
    EditText etSendData;
    Button btnSendData;

    Button btnSendIdTable;

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            usbSerialService = ((UsbSerialService.UsbBinder) service).getService();
            usbSerialService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            usbSerialService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new MyHandler(this);

        tvDisplayData = (TextView) findViewById(R.id.tvDisplayData);
        etSendData = (EditText) findViewById(R.id.etSendData);
        btnSendData = (Button) findViewById(R.id.btnSendData);

        btnSendIdTable = (Button) findViewById(R.id.btnSendIdTable);

        btnSendData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String data = etSendData.getText().toString();
                try {
                    usbSerialService.write(data.getBytes(), 1000);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Example Get and List folder, file
        /*
        String path = Environment.getExternalStorageDirectory() + "/Download/LINE";
        Log.d("Path", String.valueOf(path));
        File directory = new File(String.valueOf(path));
        File[] files = directory.listFiles();
        if (files != null) {
            Log.d("Files", "Size: " + files.length);
            for (int i = 0; i < files.length; i++) {
                Log.d("Files", "FileName:" + files[i].getName());
            }
        }
        */

        // Find the directory for the SD Card using the API
        String path = Environment.getExternalStorageDirectory() + "/Download";

        // Get the text file
        File file = new File(path, "ID.IDS");

        // Read text from file
        StringBuilder text = new StringBuilder();

        final int size = (int) file.length();
        final byte[] bytes = new byte[size];
        Log.d("size", String.valueOf(size));

        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            bufferedInputStream.read(bytes, 0, bytes.length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        btnSendIdTable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    usbSerialService.write(bytes, 1000);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

//        try {
//            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
//            String line;
//
//            while ((line = bufferedReader.readLine()) != null) {
//                text.append(line);
//                Log.d("Text", text.toString());
//                text.append('\n');
//            }
//            bufferedReader.close();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        setFilters(); // Start listening notifications from UsbSerialService
        startService(UsbSerialService.class, usbConnection, null); // Start UsbSerialService(if it was not started before) and Bind it
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbSerialService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbSerialService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbSerialService.ACTION_NO_USB);
        filter.addAction(UsbSerialService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbSerialService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbSerialService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbSerialService.
     * Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UsbSerialService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    mActivity.get().tvDisplayData.setText(data);
                    break;
            }
        }
    }
}














