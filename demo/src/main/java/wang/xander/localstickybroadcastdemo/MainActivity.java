package wang.xander.localstickybroadcastdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import wang.xander.localstickybroadcast.LocalStickyBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    int data = 0;

    private BroadcastReceiver msgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: " + intent.getStringExtra("data"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                send();
            }
        }, 0, 5000);

    }

    private void send() {
//        sendBroadcast();
        sendStickerBroadcast();
        data++;
    }

    private void sendBroadcast() {
        Log.d(TAG, "sendBroadcast: " + data);
        Intent intent = new Intent("action.wang.msg.DEMO");
        intent.putExtra("data", "intent " + data);
        LocalStickyBroadcastManager lm =
                LocalStickyBroadcastManager.getInstance(getApplicationContext());
        lm.sendBroadcast(intent);
    }

    private void sendStickerBroadcast() {
        Log.d(TAG, "sendStickerBroadcast: " + data);
        Intent intent = new Intent("action.wang.msg.DEMO");
        intent.putExtra("data", "sticker intent " + data);
        LocalStickyBroadcastManager lm =
                LocalStickyBroadcastManager.getInstance(getApplicationContext());
        lm.sendStickerBroadcast(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
        LocalStickyBroadcastManager lm = LocalStickyBroadcastManager.getInstance(this);
        IntentFilter msgFilter = new IntentFilter();
        msgFilter.addAction("action.wang.msg.DEMO");
        lm.registerReceiver(msgReceiver, msgFilter);

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: ");
        LocalStickyBroadcastManager lm = LocalStickyBroadcastManager.getInstance(this);
        lm.unregisterReceiver(msgReceiver);
    }

}
