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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Monitoring service for browser history
 * @author ningxia
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
    private static final Uri BOOKMARKS_URI_DEFAULT = Uri.parse("content://com.android.chrome.browser/history");
    private static final Uri BOOKMARKS_URI_CHROME = Uri.parse("content://com.android.chrome.browser/bookmarks");
    private static final Uri BOOKMARKS_URI_SAMSUNG_S = Uri.parse("content://com.sec.android.app.sbrowser.browser/history");
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
        displayProviders();
        init();
    }

    public static BrowserHistoryService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    private void displayProviders() {
        List<String> contentProviders = new ArrayList<>();
        PackageManager pm = MyApplication.getAppContext().getPackageManager();
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PROVIDERS)) {
            ProviderInfo[] pvis = pi.providers;
            if (pvis != null) {
                for (ProviderInfo pvi : pvis) {
                    if (pvi.authority.toString().toLowerCase().contains("browser")) {
                        contentProviders.add(pvi.authority);
                    }
                }
            }
        }
        for (String contentProvider : contentProviders) {
            Log.d(TAG, "BrowserHistoryService.displayProviders: " + contentProvider);
        }
    }

    /**
     * Content observer to be notified of changes to Browser database tables.
     * @author ningxia
     */
    private class BrowserContentObserver extends ContentObserver {

        public BrowserContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {

            Log.d(TAG, "BrowserHistoryService.BrowserContentObserver.onChange: " + selfChange + "\t " + uri.toString());
            if (DebugLog.DEBUG)
                Log.d(TAG, "BrowserHistoryService - BrowserContentObserver: changed");
            Log.d(TAG, "BrowserHistoryService Time: " + System.currentTimeMillis());
            getBrowserData();
            performUpdates();
            super.onChange(selfChange);
        }
    };

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG)
            Log.d(TAG, "BrowserHistoryService.getMetricInfo - updating browser activity value");

        if (prevID < 0) {
            if (browserObserver == null) {
                browserObserver = new BrowserContentObserver(metricHandler);
            }
//            browserResolver.registerContentObserver(Uri.parse("content://com.android.chrome.browser/history"), true, browserObserver);
//            browserResolver.registerContentObserver(Browser.BOOKMARKS_URI, true, browserObserver);
            browserResolver.registerContentObserver(BOOKMARKS_URI_DEFAULT, true, browserObserver);
        }
        getBrowserData();
        performUpdates();
//        updateBrowserData();
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
        if (cur == null || !cur.moveToFirst()) {
            if (cur != null) {
                cur.close();
            }
            if (DebugLog.DEBUG)
                Log.d(TAG, "BrowserHistoryService.updateBrowserData - browser history cursor empty?");
            values[BROWSING_HISTORY] = "";
        }
        else {
            prevID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        if (DebugLog.DEBUG)
            Log.d(TAG, "BrowserHistoryService.updateBrowserData - prevID: " + prevID);
        cur.close();
    }

    private void getBrowserData() {
        Cursor cur = browserResolver.query(uri, browsing_projection, BROWSING_TYPE, null, SORT_ORDER);
        if (cur == null) {
            return;
        }
        else {
            if (!cur.moveToFirst()) {
                cur.close();
                if (DebugLog.DEBUG)
                    Log.d(TAG, "BrowserHistoryService.getBrowserData - cursor empty?");
                return;
            }

            long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            long nextID = firstID;
            Log.d(TAG, "BrowserHistoryService.getBrowserData - nextID: " + nextID);
            StringBuilder sb = new StringBuilder();
            while (nextID != prevID) {
                String title = cur.getString(cur.getColumnIndexOrThrow(BROWSING_TITLE));
                long date = cur.getLong(cur.getColumnIndexOrThrow(BROWSING_DATE));
                String url = cur.getString(cur.getColumnIndexOrThrow(BROWSING_URL));
                appendInfo(sb, title, date, url);

                if (!cur.moveToNext()) {
                    break;
                }

                nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            }

            values[BROWSING_HISTORY] = sb.toString();
            if (DebugLog.DEBUG)
                Log.d(TAG, "BrowserHistoryService.getBrowserData - browsing history: " + sb.toString());

            cur.close();
            prevID = firstID;
        }
    }

    private void appendInfo(StringBuilder sb, String title, long date, String url) {
        title = title.replaceAll("\\|", "");
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
    Object getMetricValue(int metric) {
        final long curTime = SystemClock.uptimeMillis();
        if ((curTime - lastUpdate) > freshnessThreshold) {
            getBrowserData();
            lastUpdate = curTime;
        }

        if ((metric < groupId) || (metric >= (groupId + values.length))) {
            if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getMetricValue - metric value" + metric + ", not valid for group" + groupId);
            return null;
        }
        return values[metric - groupId];
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
        scheduleNextUpdate(nextUpdate);
        updateObservable();
    }

}
