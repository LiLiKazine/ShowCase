package kazine.lili.pedometer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import kazine.lili.pedometer.services.CountService;

/**
 * Created by LiLi
 * lilikazine@gmail.com
 */

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, CountService.class);
        context.startService(i);
    }
}
