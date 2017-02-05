package kazine.lili.pedometer.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import java.io.FileDescriptor;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import kazine.lili.devutils.LogUtils;
import kazine.lili.pedometer.CountDownTimer;
import kazine.lili.pedometer.MainActivity;
import kazine.lili.pedometer.R;
import kazine.lili.pedometer.entity.StepData;
import kazine.lili.pedometer.global.Constants;
import kazine.lili.pedometer.tools.DBUtils;
import kazine.lili.pedometer.tools.StepDetector;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class CountService extends Service implements SensorEventListener {
    private final String TAG = getClass().getSimpleName();
    private static int gap = 30000;
    private static String CURRENT_DATE="";
    private SensorManager sensorManager;
    private StepDetector detector;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;

    private BroadcastReceiver mBatInfoReceiver;
    private PowerManager.WakeLock wakeLock;
    private TimeCount time;

    private static int i = 0;
    private String DB_NAME = "pedometer";

    private Messenger messenger = new Messenger(new MessengerHandler());


    private static class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MSG_FROM_CLIENT:
                    try {
                        Messenger messenger = msg.replyTo;
                        Message replyMsg = Message.obtain(null, Constants.MSG_FROM_SERVER);
                        Bundle bundle = new Bundle();
                        bundle.putInt("step", StepDetector.CURRENT_STEP);
                        replyMsg.setData(bundle);
                        messenger.send(replyMsg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.d(TAG, "service on.");
        initBroadcastReceiver();
        new Thread(new Runnable() {
            @Override
            public void run() {
                startStepDetector();
            }
        }).start();
        startTimeCount();
    }

    private void startStepDetector() {
        if (sensorManager != null && detector != null) {
            sensorManager.unregisterListener(detector);
            sensorManager = null;
            detector = null;
        }
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        getLock(this);
        addPedometerListener();
        addCountStepListener();
    }

    private void addCountStepListener() {
        Sensor detectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (detectorSensor != null) {
            sensorManager.registerListener(CountService.this, detectorSensor, SensorManager.SENSOR_DELAY_UI);
        } else if (countSensor != null) {
            sensorManager.registerListener(CountService.this, countSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            LogUtils.d(TAG, "Count sensor not available!");
        }
    }


    private void addPedometerListener() {
        detector = new StepDetector(this);
        // 获得传感器的类型，这里获得的类型是加速度传感器
        // 此方法用来注册，只有注册过才会生效，参数：SensorEventListener的实例，Sensor的实例，更新速率
        Sensor sensor = sensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(detector, sensor,
                SensorManager.SENSOR_DELAY_UI);
        detector
                .setOnSensorChangeListener(new StepDetector.OnSensorChangeListener() {

                    @Override
                    public void onChange() {
                        updateNotification("Today's Step: " + StepDetector.CURRENT_STEP);
                    }
                });
    }

    private void startTimeCount() {
        time = new TimeCount(gap, 1000);
        time.start();
    }

    private void initBroadcastReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_SHUTDOWN);

        mBatInfoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                String action = intent.getAction();

                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    LogUtils.d(TAG, "screen on");
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    LogUtils.d(TAG, "screen off");
                    gap = 60000;
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    LogUtils.d(TAG, "screen unlock");
                    save();
                    gap = 30000;
                } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                    LogUtils.d(TAG, " receive Intent.ACTION_CLOSE_SYSTEM_DIALOGS");
                    save();
                } else if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                    LogUtils.d(TAG, " receive ACTION_SHUTDOWN");
                    save();
                } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
                    LogUtils.d(TAG, " receive ACTION_TIME_CHANGED");
                    initData();
                    clearStepData();
                }
            }
        };
        registerReceiver(mBatInfoReceiver, filter);
    }

    private void clearStepData() {
        i = 0;
        CountService.CURRENT_DATE = "0";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initData();
        updateNotification("Today's Step: " + StepDetector.CURRENT_STEP);
        return START_STICKY;
    }

    private String getDate() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyy-MM-dd");
        return format.format(date);
    }

    private void initData() {
        CURRENT_DATE = getDate();
        DBUtils.createDb(this, DB_NAME);

        List<StepData> list = DBUtils.getQueryByWhere(StepData.class, "today", new String[]{CURRENT_DATE});
        if (list.size() == 0 || list.isEmpty()) {
            StepDetector.CURRENT_STEP = 0;
        } else if (list.size() == 1) {
            StepDetector.CURRENT_STEP = Integer.parseInt(list.get(0).getStep());
        } else {
            LogUtils.d(TAG, "Something went Wrong!");
        }
    }
    /**
     * 更新通知
     */
    private void updateNotification(String content) {
        builder = new NotificationCompat.Builder(this);
        builder.setPriority(Notification.PRIORITY_MIN);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        builder.setContentIntent(contentIntent);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setTicker("Pedometer");
        builder.setContentTitle("Pedometer");
        //设置不可清除
        builder.setOngoing(true);
        builder.setContentText(content);
        Notification notification = builder.build();

        startForeground(0, notification);
        notificationManager= (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(R.string.app_name, notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return messenger.getBinder();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        i++;
//        StepDetector.CURRENT_STEP++;

        updateNotification("Today's Step: " + StepDetector.CURRENT_STEP);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    class TimeCount extends CountDownTimer {
        public TimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long l) {

        }

        @Override
        public void onFinish() {
            time.cancel();
            save();
            startTimeCount();
        }
    }

    private void save() {
        int tempStep = StepDetector.CURRENT_STEP;
        List<StepData> list = DBUtils.getQueryByWhere(StepData.class, "today", new String[]{CURRENT_DATE});
        if (list.size() == 0 || list.isEmpty()) {
            StepData data = new StepData();
            data.setToday(CURRENT_DATE);
            data.setStep(tempStep + "");
            DBUtils.insert(data);
        } else if (list.size() == 1) {
            StepData data = list.get(0);
            data.setStep(tempStep + "");
            DBUtils.update(data);
        } else {
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        DBUtils.closeDb();
        unregisterReceiver(mBatInfoReceiver);
        Intent intent = new Intent(this, StepDetector.class);
        startService(intent);
        super.onDestroy();
    }
    synchronized private PowerManager.WakeLock getLock(Context context) {
        if (wakeLock != null) {
            if (wakeLock.isHeld())
                wakeLock.release();
            wakeLock = null;
        }

        if (wakeLock == null) {
            PowerManager mgr = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    CountService.class.getName());
            wakeLock.setReferenceCounted(true);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(System.currentTimeMillis());
            int hour = c.get(Calendar.HOUR_OF_DAY);
            if (hour >= 23 || hour <= 6) {
                wakeLock.acquire(5000);
            } else {
                wakeLock.acquire(300000);
            }
        }
        return (wakeLock);
    }
}
