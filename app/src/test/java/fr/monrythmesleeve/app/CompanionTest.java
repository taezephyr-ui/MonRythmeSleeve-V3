package fr.monrythmesleeve.app;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.content.Context;
import java.time.LocalDate;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CompanionTest {
    private Context c;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();ScheduleStore.prefs(c).edit().clear().commit();PetStore.prefs(c).edit().clear().commit();c.deleteDatabase("mon_rythme_history_v2.db");ScheduleStore.prepare(c);}
    @Test public void rewardsAccumulateAndStackOnSunday(){
        LocalDate adopted=LocalDate.of(2026,10,16),sun=LocalDate.of(2026,10,18);
        assertEquals(1,PetRules.accessories(adopted,sun));assertEquals(1,PetRules.specials(adopted,sun));
        assertEquals(7,PetRules.accessories(adopted,adopted.plusDays(14)));assertEquals(2,PetRules.specials(adopted,adopted.plusDays(14)));
        assertEquals(0,PetRules.specials(sun,sun));assertEquals(1,PetRules.specials(sun,sun.plusDays(7)));
        assertEquals(0,PetRules.accessories(adopted,adopted.minusDays(1)));
    }
    @Test public void rewardsSurviveAbsenceClaimAndSpeciesChange(){
        PetStore.adopt(c,0,"Lili");PetStore.prefs(c).edit().putString("adopted",LocalDate.now().minusDays(14).toString()).commit();
        int due=PetStore.pending(c);assertTrue(due>=9);PetStore.claim(c);assertEquals(0,PetStore.pending(c));
        PetStore.adopt(c,7,"Pico");assertEquals(14,PetRules.days(PetStore.adoption(c),LocalDate.now()));assertEquals(0,PetStore.pending(c));
        PetStore.claim(c);assertEquals(0,PetStore.pending(c));
    }
    @Test public void drinkBoundariesAndUnfinishedMeal(){
        long start=10_000_000,end=start+30*60_000L;
        assertFalse(PetRules.blocked(start-30*60_000L-1,start,end,false));
        assertTrue(PetRules.blocked(start-30*60_000L,start,end,false));
        assertTrue(PetRules.blocked(end+30*60_000L-1,start,end,false));
        assertFalse(PetRules.blocked(end+30*60_000L,start,end,false));
        assertTrue(PetRules.blocked(end+8*60*60_000L,start,end,true));
    }
    @Test public void correctionPreservesFinishAndLaterMeals(){
        LocalDate day=ScheduleStore.day(c);long now=System.currentTimeMillis(),start=ScheduleStore.at(day,0);
        try(HistoryStore db=new HistoryStore(c)){
            db.start(day.toString(),0,"Breakfast",start,start+1000);db.finish(day.toString(),0,"Breakfast",start,start+2000);
            db.start(day.toString(),1,"Snack",start+3000,start+3000);db.finish(day.toString(),1,"Snack",start+3000,start+4000);
        }
        assertNull(ScheduleStore.correctStart(c,day,0,start+500));
        assertEquals(start+2000,ScheduleStore.record(c,day,0).finished);assertEquals(start+3000,ScheduleStore.record(c,day,1).started);
        assertNotNull(ScheduleStore.correctStart(c,day,0,now+60_000));
        assertNotNull(ScheduleStore.correctStart(c,day,0,start+2500));
    }
    @Test public void forgottenStartRecalculatesThirtyMinutesAndGap(){
        LocalDate day=ScheduleStore.day(c);long actual=ScheduleStore.at(day,0)+1000;
        assertNull(ScheduleStore.correctStart(c,day,0,actual));
        assertEquals(actual+30*60_000L,ScheduleStore.expectedEnd(c,day,0));
        assertEquals(181,ScheduleStore.times(c)[1]);
        assertEquals(actual,ScheduleStore.record(c,day,0).started);
    }
    @Test public void nocturnalSnackUsesPlanningDay(){
        LocalDate d=LocalDate.of(2026,10,24);
        assertEquals(1470,ScheduleStore.minuteOf(d,ScheduleStore.at(d,1470)));
        assertEquals(1,PetRules.accessories(d,d.plusDays(2)));
    }
    @Test public void finishCorrectionRecalculatesPendingMealsAndKeepsStart(){
        LocalDate d=ScheduleStore.day(c);long start=ScheduleStore.at(d,0)+1000;
        try(HistoryStore db=new HistoryStore(c)){
            db.start(d.toString(),0,"Breakfast",start,start);
            db.finish(d.toString(),0,"Breakfast",start,start+1000);
        }
        long end=start+60_000;
        assertNull(ScheduleStore.correctFinish(c,d,0,end));
        assertEquals(start,ScheduleStore.record(c,d,0).started);
        assertEquals(end,ScheduleStore.record(c,d,0).finished);
        assertEquals(end,ScheduleStore.expectedEnd(c,d,0));
        assertEquals(152,ScheduleStore.times(c)[1]);
        assertEquals(332,ScheduleStore.times(c)[2]);
        assertNotNull(ScheduleStore.correctFinish(c,d,0,start-1));
        assertNotNull(ScheduleStore.correctFinish(c,d,0,System.currentTimeMillis()+60_000));
        assertEquals(end,ScheduleStore.record(c,d,0).finished);
    }
    @Test public void finishCorrectionPreservesLaterRealMealAndItsPlanning(){
        LocalDate d=ScheduleStore.day(c);long start=ScheduleStore.at(d,0)+1000;
        try(HistoryStore db=new HistoryStore(c)){
            db.start(d.toString(),0,"Breakfast",start,start);
            db.finish(d.toString(),0,"Breakfast",start,start+1000);
            db.start(d.toString(),2,"Lunch",start+6000,start+6000);
            db.finish(d.toString(),2,"Lunch",start+6000,start+7000);
        }
        int original=ScheduleStore.times(c)[2],last=ScheduleStore.times(c)[3];
        assertNull(ScheduleStore.correctFinish(c,d,0,start+2000));
        assertEquals(start+6000,ScheduleStore.record(c,d,2).started);
        assertEquals(start+7000,ScheduleStore.record(c,d,2).finished);
        assertEquals(original,ScheduleStore.times(c)[2]);assertEquals(last,ScheduleStore.times(c)[3]);
        assertNotNull(ScheduleStore.correctFinish(c,d,0,start+6500));
        assertEquals(start+2000,ScheduleStore.record(c,d,0).finished);
    }
    @Test public void historicalFinishCorrectionNeverChangesToday(){
        LocalDate today=ScheduleStore.day(c),d=today.minusDays(1);long end=ScheduleStore.at(d,1470);
        try(HistoryStore db=new HistoryStore(c)){db.finish(d.toString(),5,"Old snack",end-1800000,end);}
        int[] original=ScheduleStore.times(c);
        assertNull(ScheduleStore.correctFinish(c,d,5,end+60_000));
        assertEquals(1471,ScheduleStore.minuteOf(d,ScheduleStore.record(c,d,5).finished));
        assertArrayEquals(original,ScheduleStore.times(c));
        assertEquals(0,ScheduleStore.record(c,d,5).started);
    }
    @Test public void rewardPreviewsMatchCalendarAndProgress(){
        LocalDate friday=LocalDate.of(2026,10,16),sunday=friday.plusDays(2);
        assertEquals(sunday,PetRules.nextAccessory(friday,0));
        assertEquals(sunday,PetRules.nextSpecial(friday,0));
        assertEquals(sunday.plusDays(7),PetRules.nextSpecial(friday,1));
        assertEquals(50,PetRules.progress(friday,sunday,friday.plusDays(1)));
        assertEquals(100,PetRules.progress(friday,sunday,sunday.plusDays(2)));
        assertEquals(0,PetRules.progress(friday,sunday,friday.minusDays(2)));
        assertEquals(sunday.plusDays(7),PetRules.nextSpecial(sunday,0));
    }
    @Test public void launcherSwitchKeepsOneIconAndActivityEnabled(){
        android.content.pm.PackageManager pm=c.getPackageManager();
        for(int chosen=0;chosen<8;chosen++){
            CompanionIcon.apply(c,chosen);
            for(int i=-1;i<8;i++){
                android.content.ComponentName component=new android.content.ComponentName(c.getPackageName(),"fr.monrythmesleeve.app.Launcher"+(i<0?"Default":i));
                assertEquals(i==chosen?android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED:android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,pm.getComponentEnabledSetting(component));
            }
            assertNotEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,pm.getComponentEnabledSetting(new android.content.ComponentName(c,MainActivity.class)));
        }
    }
    @Test @Config(sdk=33) public void launcherSwitchIsAtomicOnNewerAndroid(){launcherSwitchKeepsOneIconAndActivityEnabled();}
}
