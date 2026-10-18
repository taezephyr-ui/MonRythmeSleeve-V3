package fr.monrythmesleeve.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Historique reel, persistant, y compris lorsqu'une collation traverse minuit. */
public final class HistoryStore extends SQLiteOpenHelper {
    private static final String TABLE = "meals";
    public HistoryStore(Context context) { super(context, "mon_rythme_history_v2.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meals (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, meal INTEGER NOT NULL, title TEXT NOT NULL, planned INTEGER NOT NULL, started INTEGER, finished INTEGER, UNIQUE(day,meal))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public static final class Entry {
        public String day, title;
        public int meal;
        public long planned, started, finished;
    }
    private Entry read(Cursor c) {
        Entry r = new Entry();
        r.day = c.getString(0); r.meal = c.getInt(1); r.title = c.getString(2);
        r.planned = c.getLong(3);
        r.started = c.isNull(4) ? 0 : c.getLong(4);
        r.finished = c.isNull(5) ? 0 : c.getLong(5);
        return r;
    }
    public Entry get(String day, int meal) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT day,meal,title,planned,started,finished FROM meals WHERE day=? AND meal=?",
                new String[]{day, String.valueOf(meal)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    public List<Entry> all() {
        List<Entry> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT day,meal,title,planned,started,finished FROM meals ORDER BY day DESC,meal ASC",null)) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }
    public void start(String day, int meal, String title, long planned, long started) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("day",day); cv.put("meal",meal); cv.put("title",title);
            cv.put("planned",planned); cv.put("started",started);
            db.insertWithOnConflict(TABLE,null,cv,SQLiteDatabase.CONFLICT_IGNORE);
            cv.clear(); cv.put("started",started);
            db.update(TABLE,cv,"day=? AND meal=?",new String[]{day,String.valueOf(meal)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void finish(String day, int meal, String title, long planned, long finished) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("day",day); cv.put("meal",meal); cv.put("title",title);
            cv.put("planned",planned); cv.put("finished",finished);
            db.insertWithOnConflict(TABLE,null,cv,SQLiteDatabase.CONFLICT_IGNORE);
            cv.clear(); cv.put("finished",finished);
            db.update(TABLE,cv,"day=? AND meal=?",new String[]{day,String.valueOf(meal)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}

