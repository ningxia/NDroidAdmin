package edu.nd.darts.cimon;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.Locale;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * MMS information service
 * @author ningxia
 */
public final class MMSInfoService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int MMS_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "MMS information activity";
    private static final String[] metrics = {"MMS Sent", "MMS Received"};
    private static final int MMS_SENT = Metrics.MMSSENT - Metrics.MMS_INFO_CATEGORY;
    private static final int MMS_RECEIVED = Metrics.MMSRECEIVED - Metrics.MMS_INFO_CATEGORY;
    private static final MMSInfoService INSTANCE = new MMSInfoService();
    private static String description;

    public static final String MMS_ADDRESS = "address";
    public static final String MMS_DATE = "date";
    public static final String MMS_TYPE = "msg_box";

    private static final int MESSAGE_TYPE_INBOX  = 1;
    private static final int MESSAGE_TYPE_SENT   = 2;

    private static final Uri uri = Uri.parse("content://mms/");
    private static final String[] mms_projection = new String[]{BaseColumns._ID,
            MMS_DATE, MMS_TYPE};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevMMSID  = -1;

    private ContentObserver mmsObserver = null;
    private ContentResolver mmsResolver;

    private MMSInfoService() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("MMSInfoService already instantiated");
        }
        groupId = Metrics.MMS_INFO_CATEGORY;
        metricsCount = MMS_METRICS;

        values = new String[MMS_METRICS];
        valueNodes = new SparseArray<ValueNode<String>>();
        freshnessThreshold = THIRTY_SECONDS;
        mmsResolver = MyApplication.getAppContext().getContentResolver();
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static MMSInfoService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    /**
     * Content observer to be notified of changes to MMS database tables.
     * @author darts
     *
     */
    private class MmsContentObserver extends ContentObserver {

        public MmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService - MmsContentObserver: changed");
            getMmsData();
            performUpdates();
            super.onChange(selfChange);
        }
    };

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMetricInfo - updating mms activity value");

        if (prevMMSID  < 0) {
            updateMmsData();
            if (mmsObserver == null) {
                mmsObserver = new MmsContentObserver(metricHandler);
            }
            mmsResolver.registerContentObserver(uri, true, mmsObserver);

            performUpdates();
        }
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "Short message information service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < MMS_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    private void updateMmsData() {
        Cursor cur = mmsResolver.query(uri, mms_projection, MMS_TYPE + "=?",
                new String[]{String.valueOf(MESSAGE_TYPE_INBOX)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMmsData - incoming mms cursor empty?");
            values[MMS_RECEIVED] = "";
        }
        else {
            prevMMSID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMmsData - received: " + values[MMS_RECEIVED]);
        cur.close();

        cur = mmsResolver.query(uri, mms_projection, MMS_TYPE + "=?",
                new String[] {String.valueOf(MESSAGE_TYPE_SENT)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "MMSService.updateMmsData - sent mms cursor empty?");
            values[MMS_SENT] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            if (topID > prevMMSID) {
                prevMMSID = topID;
            }
        }
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMmsData - sent: " + values[MMS_SENT]);
        cur.close();
    }

    private void getMmsData() {
        Cursor cur = mmsResolver.query(uri, mms_projection, null, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        final int TYPE_COLUMN = cur.getColumnIndex(MMS_TYPE);
        StringBuilder sbReceived = new StringBuilder();
        StringBuilder sbSent = new StringBuilder();
        while (nextID != prevMMSID) {
            int type = cur.getInt(TYPE_COLUMN);
            String mmsAddress = cur.getString(cur.getColumnIndexOrThrow(MMS_ADDRESS));
            String mmsDate = getDate(cur.getLong(cur.getColumnIndexOrThrow(MMS_DATE)), "hh:ss MM/dd/yyyy");
            switch (type) {
                case MESSAGE_TYPE_INBOX:
                    appendInfo(sbReceived, mmsAddress, mmsDate);
                    break;
                case MESSAGE_TYPE_SENT:
                    appendInfo(sbSent, mmsAddress, mmsDate);
                    break;
                default:
                    break;
            }

            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData - type: " + type);

            if (!cur.moveToNext()) {
                values[MMS_RECEIVED] = sbReceived.substring(0, sbReceived.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMmsData - received: " + values[MMS_RECEIVED]);
                values[MMS_SENT] = sbSent.substring(0, sbSent.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMmsData - sent: " + values[MMS_SENT]);
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        cur.close();
        prevMMSID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String mmsAddress, String mmsDate) {
        sb.append(mmsAddress)
                .append("+")
                .append(mmsDate)
                .append("|");
    }

    private String getDate(long milliSeconds, String dateFormat) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.US);
        return formatter.format(milliSeconds);
    }

    @Override
    protected void updateObserver() {
        adminObserver.setValue(Metrics.MMSSENT, values[MMS_SENT] == "" ? 0 : values[MMS_SENT].split("\\|").length);
        adminObserver.setValue(Metrics.MMSRECEIVED, values[MMS_RECEIVED] == "" ? 0 : values[MMS_RECEIVED].split("\\|").length);
    }
}
