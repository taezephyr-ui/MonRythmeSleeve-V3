package fr.monrythmesleeve.app;

import android.content.Context;
import com.google.android.gms.wearable.DataMap;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class WatchMealsTest {
    Context c; LocalDate day; long start,end; DataMap packet;
    @Before public void init(){
        c=RuntimeEnvironment.getApplication();c.deleteDatabase("mon_rythme_history_v2.db");
        ScheduleStore.prefs(c).edit().clear().commit();day=ScheduleStore.day(c);
        end=System.currentTimeMillis();start=Math.max(ScheduleStore.at(day,0),end-60000);
        packet=new DataMap();packet.putString("day",day.toString());packet.putInt("index",0);packet.putLong("start",start);
    }
    @Test public void queuedPairKeepsTapTimesAndReplayDoesNotAdvanceAnotherMeal(){
        packet.putLong("end",end);assertEquals("",WatchMeals.apply(c,packet));assertEquals("",WatchMeals.apply(c,packet));
        HistoryStore.Entry e=ScheduleStore.record(c,day,0);assertEquals(start,e.started);assertEquals(end,e.finished);
        assertNull(ScheduleStore.record(c,day,1));
    }
    @Test public void canFinishAPhoneStartAndCannotOverwritePhoneCorrection(){
        assertTrue(ScheduleStore.begin(c,0,start));packet.putLong("end",end);
        assertEquals("",WatchMeals.apply(c,packet));packet.putLong("start",start+1);
        assertFalse(WatchMeals.apply(c,packet).isEmpty());assertEquals(start,ScheduleStore.record(c,day,0).started);
    }
}