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
package edu.nd.darts.cimon;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.StringTokenizer;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Not currently implemented.
 * @author darts
 *
 */
public final class BrowserHistoryService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int BROWSING_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "Browser history";
    private static final String[] metrics = {"Browsing History"};
    private static final int BROWSING_HISTORY = Metrics.BROWSING_HISTORY - Metrics.BROWSER_HISTORY_CATEGORY;
    private static final BrowserHistoryService INSTANCE = new BrowserHistoryService();
    private static String description;

    private static final String BROWSING_TYPE = Browser.BookmarkColumns.BOOKMARK + " = 0"; // 0 = history, 1 = bookmark
    private static final String BROWSING_TITLE = Browser.BookmarkColumns.TITLE;
    private static final String BROWSING_DATE = Browser.BookmarkColumns.DATE;
    private static final String BROWSING_URL = Browser.BookmarkColumns.URL;

    private static final Uri uri = Browser.BOOKMARKS_URI;
    private static final String[] browsing_projection = new String[]{BaseColumns._ID,
            BROWSING_TITLE, BROWSING_DATE, BROWSING_URL};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevID  = -1;

    private ContentObserver browserObserver = null;
    private ContentResolver browserResolver;

    private BrowserHistoryService() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BrowserHistoryService already instantiated");
        }
        groupId = Metrics.BROWSER_HISTORY_CATEGORY;
        metricsCount = BROWSING_METRICS;

        values = new String[BROWSING_METRICS];
        valueNodes = new SparseArray<ValueNode<String>>();
        freshnessThreshold = THIRTY_SECONDS;
        browserResolver = MyApplication.getAppContext().getContentResolver();
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static BrowserHistoryService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    /**
     * Content observer to be notified of changes to SMS database tables.
     * @author darts
     *
     */
    private class BrowserContentObserver extends ContentObserver {

        public BrowserContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
//            if (DebugLog.DEBUG)
                Log.d(TAG, "BrowserHistoryService - BrowserContentObserver: changed");
            Log.d(TAG, "Time: " + System.currentTimeMillis());
            getBrowserData();
            performUpdates();
            super.onChange(selfChange);
        }
    };

    @Override
    void getMetricInfo() {
//        if (DebugLog.DEBUG)
            Log.d(TAG, "BrowserHistoryService.getMetricInfo - updating browser activity value");

        if (prevID  < 0) {
            updateBrowserData();
            if (browserObserver == null) {
                browserObserver = new BrowserContentObserver(metricHandler);
            }
            browserResolver.registerContentObserver(Uri.parse("content://com.android.chrome.browser/history"), true, browserObserver);

            performUpdates();
        }
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "Browser History service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < BROWSING_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    private void updateBrowserData() {
        Cursor cur = browserResolver.query(uri, browsing_projection, BROWSING_TYPE, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
//            if (DebugLog.DEBUG)
                Log.d(TAG, "BrowserHistoryService.updateBrowserData - browser history cursor empty?");
            values[BROWSING_HISTORY] = "";
        }
        else {
            prevID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
//        if (DebugLog.DEBUG)
            Log.d(TAG, "BrowserHistoryService.updateBrowserData - prevSMSID: " + prevID);
        cur.close();
    }

    private void getBrowserData() {
        Cursor cur = browserResolver.query(uri, browsing_projection, BROWSING_TYPE, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getBrowserData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        StringBuilder sb = new StringBuilder();
        while (nextID != prevID) {
            String title = cur.getString(cur.getColumnIndexOrThrow(BROWSING_TITLE));
            String date = getDate(cur.getLong(cur.getColumnIndexOrThrow(BROWSING_DATE)), "hh:ss MM/dd/yyyy");
            String url = cur.getString(cur.getColumnIndexOrThrow(BROWSING_URL));
            appendInfo(sb, title, date, url);

            if (!cur.moveToNext()) {
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        values[BROWSING_HISTORY] = sb.toString();
//        if (DebugLog.DEBUG)
            Log.d(TAG, "BrowserHistoryService.getBrowserData - browsing history: " + values[BROWSING_HISTORY]);

        cur.close();
        prevID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String title, String date, String url) {
        title = title.replace("\\|", "");
        sb.append(title)
                .append("+")
                .append(date)
                .append("+")
                .append(url)
                .append("|");
    }

    private String getDate(long milliSeconds, String dateFormat) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.US);
        return formatter.format(milliSeconds);
    }

    @Override
    protected void updateObserver() {
        if (values[BROWSING_HISTORY] == null) {
            adminObserver.setValue(Metrics.BROWSING_HISTORY, 0);
        }
        else {
            StringTokenizer st = new StringTokenizer(values[BROWSING_HISTORY], "\\|");
            adminObserver.setValue(Metrics.BROWSING_HISTORY, values[BROWSING_HISTORY].equals("") ? 0 : st.countTokens());
        }
    }

    @Override
    protected void performUpdates() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.performUpdates - updating values");
        long nextUpdate = updateValueNodes();
        if (nextUpdate < 0) {
            browserResolver.unregisterContentObserver(browserObserver);
            prevID  = -1;
        }
        updateObservable();
    }

}
