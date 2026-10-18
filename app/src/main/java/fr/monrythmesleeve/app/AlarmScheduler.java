package fr.monrythmesleeve.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.LocalDate;

/** Un seul prochain rappel par repas/type, puis reprogrammation. */
public final class AlarmScheduler {
    private AlarmScheduler() { }
    public static final int STOP_DRINKING=0,MEAL_START=1,DRINK_AGAIN=2;
    public static final String CHANNEL="sleeve_alertes_v2";
    private static PendingIntent pending(Context c,int meal,int type){
        Intent i=new Intent(c,AlarmReceiver.class).putExtra("meal",meal).putExtra("kind",type);
        return PendingIntent.getBroadcast(c,1000+meal*3+type,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static long eventTime(Context c,LocalDate day,int meal,int type){
        int start=ScheduleStore.timesFor(c,day)[meal];
        if(type==STOP_DRINKING)return ScheduleStore.at(day,start-ScheduleStore.DRINKS_BEFORE);
        if(type==MEAL_START)return ScheduleStore.at(day,start);
        HistoryStore.Entry entry=ScheduleStore.record(c,day,meal);
        // Ne jamais inviter a reboire tant que le repas a commence et n'est pas termine.
        if(entry!=null&&entry.started>0&&entry.finished==0)return -1;
        return ScheduleStore.expectedEnd(c,day,meal)+30*60_000L;
    }
    public static void rescheduleAll(Context c){
        AlarmManager mgr=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(mgr==null)return;
        for(int meal=0;meal<6;meal++)for(int kind=0;kind<3;kind++){
            mgr.cancel(pending(c,meal,kind));
            if(meal<ScheduleStore.count(c)&&ScheduleStore.alertsEnabled(c)&&ScheduleStore.eventEnabled(c,kind))scheduleOne(c,meal,kind);
        }
    }
    public static void scheduleOne(Context c,int meal,int kind){
        if(meal<0||meal>=ScheduleStore.count(c)||!ScheduleStore.alertsEnabled(c)||!ScheduleStore.eventEnabled(c,kind))return;
        AlarmManager mgr=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(mgr==null)return;
        LocalDate current=ScheduleStore.day(c);long now=System.currentTimeMillis(),next=Long.MAX_VALUE;
        for(int offset=0;offset<4;offset++){
            LocalDate d=current.plusDays(offset);long when=eventTime(c,d,meal,kind);
            if(d.equals(current)){
                HistoryStore.Entry r=ScheduleStore.record(c,d,meal);
                if(r!=null&&(r.finished>0&&kind!=DRINK_AGAIN || r.started>0&&kind==MEAL_START))continue;
            }
            if(when>now&&when<next)next=when;
        }
        if(next==Long.MAX_VALUE)return;
        PendingIntent action=pending(c,meal,kind);
        try{
            if(Build.VERSION.SDK_INT>=31&&!mgr.canScheduleExactAlarms())mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,action);
            else mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,action);
        }catch(SecurityException denied){mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,action);}
    }
}

