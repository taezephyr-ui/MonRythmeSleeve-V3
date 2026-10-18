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
}
