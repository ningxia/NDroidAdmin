package edu.nd.darts.cimon.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Database for added work state
 *
 * @author Xiao(Sean) Bo
 *
 */
public class LabelingDB {
    public static final String TABLE_NAME = "labelling_db";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_STATE = "state";
    private static final String DATABASE_CREATE = "CREATE TABLE IF NOT EXISTS "
            + TABLE_NAME + "(" + COLUMN_ID
            + " integer primary key autoincrement, " + COLUMN_STATE
            + " text not null " + ");";
    private static final String DATABASE_DROP = "DROP TABLE " + TABLE_NAME
            + ";";
    private static final String PATH = "/data/data/edu.nd.darts.cimon/databases/labellingdb";
    public static SQLiteDatabase db;

    public LabelingDB() {
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

    public void insertData(String state) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATE, state);
        db.insert(TABLE_NAME, null, values);
    }

    public void deleteRow(String state) {
        db.delete(TABLE_NAME, this.COLUMN_STATE + " = " + "'" + state + "'",
                null);
    }

    public void close() {
        db.close();
    }
}
