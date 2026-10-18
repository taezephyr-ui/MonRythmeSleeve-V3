package fr.monrythmesleeve.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class RescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx,Intent intent){AlarmReceiver.ensureChannel(ctx);AlarmScheduler.rescheduleAll(ctx);}
}

