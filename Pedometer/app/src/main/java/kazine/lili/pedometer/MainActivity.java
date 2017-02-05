package kazine.lili.pedometer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import butterknife.BindView;
import kazine.lili.devutils.LogUtils;
import kazine.lili.pedometer.global.Constants;
import kazine.lili.pedometer.services.CountService;

public class MainActivity extends AppCompatActivity implements Handler.Callback {

//    @BindView(R.id.steps)
    private TextView steps;

    private long TIME_INTERVAL = 500;
    private Messenger messenger;
    private Messenger receiveMessenger = new Messenger(new Handler(this));
    private Handler delayHandler;

    @Override
    protected void onStart() {
        super.onStart();
        initService();
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            try {
                messenger = new Messenger(iBinder);
                Message msg = Message.obtain(null, Constants.MSG_FROM_CLIENT);
                msg.replyTo = receiveMessenger;
                messenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            LogUtils.d("Connection broken.");
        }
    };

    private void initService() {
        Intent intent = new Intent(this, CountService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        startService(intent);
        LogUtils.d("start service.");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        delayHandler = new Handler(this);
        steps = (TextView) findViewById(R.id.steps);
//        steps.setText("3");
    }


    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case Constants.MSG_FROM_SERVER:
                steps.setText(message.getData().getInt("step") + "");
                delayHandler.sendEmptyMessageDelayed(Constants.REQUEST_SERVER, TIME_INTERVAL);
                break;
            case Constants.REQUEST_SERVER:
                try {
                    Message msg = Message.obtain(null, Constants.MSG_FROM_CLIENT);
                    msg.replyTo = receiveMessenger;
                    messenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                };
                break;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
    }
}
