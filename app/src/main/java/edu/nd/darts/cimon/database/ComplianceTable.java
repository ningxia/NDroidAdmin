/*
 * Copyright (C) 2013 Chris Miller
 *
 * This file is part of CIMON.
 * 
 * CIMON is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CIMON is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with CIMON.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package edu.nd.darts.cimon.database;

import edu.nd.darts.cimon.DebugLog;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Defines the layout of the Compliance table of the database.
 * This table stores data for all readings of cimon metrics.
 *
 * @author ningxia
 *
 * @see MetricsTable
 *
 */
public final class ComplianceTable {

    private static final String TAG = "NDroid";

    // Database table
    public static final String TABLE_COMPLIANCE = "compliance";
    // Table columns
    /** Unique id (Long) */
    public static final String COLUMN_ID = "_id";
    /** Time of reading, from system uptime in milliseconds (Long). */
    public static final String COLUMN_TIMESTAMP = "timestamp";
    /** Data collected counter (Long). */
    public static final String COLUMN_TYPE = "type";
    public static final int TYPE_COLLECTED = 0;
    public static final int TYPE_UPLOADED = 1;
    /** Data remained counter in DataTable */
    public static final String COLUMN_VALUE = "value";

    // Database creation SQL statement
    private static final String DATABASE_CREATE = "create table if not exists "
            + TABLE_COMPLIANCE
            + "("
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_TIMESTAMP + " long not null,"
            + COLUMN_TYPE + " integer not null,"
            + COLUMN_VALUE + " long not null"
            + ");";

    public static void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    public static void onUpgrade(SQLiteDatabase database, int oldVersion,
                                 int newVersion) {
        if (DebugLog.INFO) Log.i(TAG, TABLE_COMPLIANCE + ": Upgrading database from version "
                + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_COMPLIANCE);
        onCreate(database);
    }

}
