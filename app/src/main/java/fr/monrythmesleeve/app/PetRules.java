package fr.monrythmesleeve.app;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** Calendar rewards, independent of meals, drinks and app openings. */
public final class PetRules {
    private PetRules() {}
    public static int days(LocalDate adopted,LocalDate today){return (int)Math.max(0,ChronoUnit.DAYS.between(adopted,today));}
    public static int accessories(LocalDate adopted,LocalDate today){return days(adopted,today)/2;}
    public static int specials(LocalDate adopted,LocalDate today){
        LocalDate first=adopted.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        return today.isBefore(first)?0:1+(int)(ChronoUnit.DAYS.between(first,today)/7);
    }
    public static boolean blocked(long now,long start,long end,boolean active){
        return active ? now>=start : now>=start-30*60_000L&&now<end+30*60_000L;
    }
}
