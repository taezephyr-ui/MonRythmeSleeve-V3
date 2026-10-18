package fr.monrythmesleeve.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Alertes identiques a la V1, sur un canal haute priorite avec vibration. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx,Intent intent){
        int meal=intent.getIntExtra("meal",-1),kind=intent.getIntExtra("kind",-1);
        if(meal<0||meal>=ScheduleStore.count(ctx)||kind<0||kind>2)return;
        if(ScheduleStore.alertsEnabled(ctx)&&ScheduleStore.eventEnabled(ctx,kind))notify(ctx,meal,kind);
        AlarmScheduler.scheduleOne(ctx,meal,kind);
    }
    public static void ensureChannel(Context ctx){
        NotificationManager mgr=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(mgr==null)return;
        NotificationChannel channel=new NotificationChannel(AlarmScheduler.CHANNEL,"Repas et boissons",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifications et vibrations pour les repas et les boissons");
        channel.enableVibration(true);channel.setVibrationPattern(new long[]{0,280,150,280});mgr.createNotificationChannel(channel);
    }
    private static void notify(Context c,int meal,int kind){
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        NotificationManager mgr=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(mgr==null)return;
        ensureChannel(c);
        String title,body;
        if(kind==0){title="Pause boisson";body="Arrête de boire : "+ScheduleStore.name(meal)+" dans 30 minutes.";}
        else if(kind==1){title="C'est l'heure du repas";body=ScheduleStore.name(meal)+" : prends ton temps pour manger.";}
        else{title="Tu peux boire à nouveau";body="La pause après "+ScheduleStore.name(meal)+" est terminée.";}
        Intent open=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action=PendingIntent.getActivity(c,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,AlarmScheduler.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(action)
            .setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build();
        mgr.notify(2000+meal*3+kind,n);
    }
}

