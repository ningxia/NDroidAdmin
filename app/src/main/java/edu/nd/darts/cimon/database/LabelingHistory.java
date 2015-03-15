package edu.nd.darts.cimon.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Database to labeling history
 *
 * @author Xiao(Sean) Bo
 *
 */
public class LabelingHistory {
    public static final String TABLE_NAME = "labelling_history";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_START = "start_time";
    public static final String COLUMN_END = "end_time";
    public static final String COLUMN_STATE = "state";
    private static final String DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
            + "(" + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_STATE + " text not null, " + COLUMN_START
            + " integer not null," + COLUMN_END + " integer not null" + ");";
    private static final String DATABASE_DROP = "DROP TABLE " + TABLE_NAME + ";";
    private static final String PATH = "/data/data/edu.nd.darts.cimon/databases/labellinghistory";
    public static SQLiteDatabase db;

    public LabelingHistory() {
        this.open();
    }

    public void open() {
        db = SQLiteDatabase.openOrCreateDatabase(PATH, null);
        db.execSQL(DATABASE_CREATE);
    }

    public Cursor getData() {
        Cursor cursor = db.rawQuery("select * from " + TABLE_NAME, null);
        return cursor;
    }

    public void insertData(String state, Long startTime, Long endTime) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATE, state);
        values.put(COLUMN_START, startTime);
        values.put(COLUMN_END, endTime);
        db.insert(TABLE_NAME, null, values);
    }

    public void deleteAllData() {
        db.execSQL(this.DATABASE_DROP);
    }

    public void close() {
        db.close();
    }
}
