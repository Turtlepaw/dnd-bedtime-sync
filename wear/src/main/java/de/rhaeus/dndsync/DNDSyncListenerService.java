package de.rhaeus.dndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class DNDSyncListenerService extends WearableListenerService {
    private static final String TAG = "DNDSyncListenerService";
    private static final String DND_SYNC_MESSAGE_PATH = "/wear-dnd-sync";

    @Override
    public void onMessageReceived (MessageEvent messageEvent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onMessageReceived: " + messageEvent);
        }

        if (messageEvent.getPath().equalsIgnoreCase(DND_SYNC_MESSAGE_PATH)) {

            vibrate();

            byte[] data = messageEvent.getData();
            // data[0] contains if dnd or bedtime mode
            // 0 = dnd
            // 1 = bedtime mode
            // data[1] contains dnd mode of phone
            // 0 = INTERRUPTION_FILTER_UNKNOWN
            // 1 = INTERRUPTION_FILTER_ALL (all notifications pass)
            // 2 = INTERRUPTION_FILTER_PRIORITY
            // 3 = INTERRUPTION_FILTER_NONE (no notification passes)
            // 4 = INTERRUPTION_FILTER_ALARMS
            byte mode = data[0];
            Log.d(TAG, "mode: " + mode);
            byte dndStatePhone = data[1];
            Log.d(TAG, "dndStatePhone: " + dndStatePhone);

            // get dnd state
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//            if (!mNotificationManager.isNotificationPolicyAccessGranted()) {
//                Toast.makeText(getApplicationContext(), "DNDSync missing DND permission!", Toast.LENGTH_SHORT).show();
//                Log.d(TAG, "DNDSync missing DND permission!");
//            }

            int filterState = mNotificationManager.getCurrentInterruptionFilter();
            if (filterState < 0 || filterState > 4) {
                Log.d(TAG, "DNDSync weird current dnd state: " + filterState);
            }
            byte currentDndState = (byte) filterState;
            Log.d(TAG, "currentDndState: " + currentDndState);

            switch (mode) {
                case 0: //dnd
                {
                    if (dndStatePhone != currentDndState) {
                        mNotificationManager.setInterruptionFilter(dndStatePhone);
//                    Toast.makeText(getApplicationContext(), "DND set to " + dndStatePhone, Toast.LENGTH_LONG).show();
                        Log.d(TAG, "DND set to " + dndStatePhone);
                    }
                    break;
                }
                case 1: //bedtime
                {
                    if (dndStatePhone != currentDndState) {
                        toggleBedtimeMode();
                        // set DND just in case bedtime toggle does not work to have at least DND
                        mNotificationManager.setInterruptionFilter(dndStatePhone);
                        Log.d(TAG, "DND set to " + dndStatePhone);
                    }

//                    // now toggle bedtime mode
//                    Log.d(TAG, "toggle bedtime mode");
//                    if(currentDndState == NotificationManager.INTERRUPTION_FILTER_ALL && dndStatePhone != NotificationManager.INTERRUPTION_FILTER_ALL) {
//                        // watch dnd is off but phone dnd is not off -> turn bedtime mode on
//                        Log.d(TAG, "DNDSync enables bedtime mode");
//                        toggleBedtimeMode();
//                    } else if (currentDndState != NotificationManager.INTERRUPTION_FILTER_ALL && dndStatePhone == NotificationManager.INTERRUPTION_FILTER_ALL) {
//                        // watch dnd is not off but phone dnd is off -> turn bedtime mode off
//                        Log.d(TAG, "DNDSync disables bedtime mode");
//                        toggleBedtimeMode();
//                    }
                    break;
                }
                default:
                {
                    Log.d(TAG, "DNDSync mode went wrong: " + mode);
                }
            }
        } else {
            super.onMessageReceived(messageEvent);
        }
    }

    private void toggleBedtimeMode() {
        // turn on screen
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
//            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "dndsync:MyWakeLock");
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP , "dndsync:MyWakeLock");
        wakeLock.acquire(2*60*1000L /*2 minutes*/);

        DNDSyncAccessService serv = DNDSyncAccessService.getSharedInstance();
        if (serv == null) {
            Log.d(TAG, "accessibility not connected");
            // create a handler to post messages to the main thread
            Handler mHandler = new Handler(getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "DNDSync can't toggle bedtime mode since Accessibility Service is not connected!", Toast.LENGTH_LONG).show();
                }
            });
            return;
        } else {
            // create a handler to post messages to the main thread
            Handler mHandler = new Handler(getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "DNDSync toggles Bedtime Mode.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // wait a bit before touch input to make sure screen is on
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // open quick panel
        serv.swipeDown();

        // wait for quick panel to open
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // click on the middle icon in the first row
        serv.clickIcon1_2();

        // wait a bit
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // close quick panel
        serv.goBack();

        wakeLock.release();
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
    }

}