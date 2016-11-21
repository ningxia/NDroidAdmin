package edu.nd.darts.cimon;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.util.Log;
import android.util.SparseArray;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
//    public static final String MMS_TYPE = "msg_box";
    public static final String MMS_TYPE = "m_type";

    private static final int MESSAGE_TYPE_INBOX  = 1;
    private static final int MESSAGE_TYPE_SENT   = 2;

    private static final Uri uri = Uri.parse("content://mms/");
    private static final String[] mms_projection = new String[]{BaseColumns._ID,
            MMS_ADDRESS, MMS_DATE, MMS_TYPE};

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
            super.onChange(selfChange);
        }
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMetricInfo - updating mms activity value");

        if (prevMMSID  < 0) {
            if (mmsObserver == null) {
                mmsObserver = new MmsContentObserver(metricHandler);
            }
            mmsResolver.registerContentObserver(Uri.parse("content://mms-sms"), true, mmsObserver);

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
        Cursor cur = mmsResolver.query(uri, null, "msg_box = 1 or msg_box = 4", null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        final int TYPE_COLUMN = cur.getColumnIndex(MMS_TYPE);
        if (TYPE_COLUMN == -1) return;
        while (nextID > prevMMSID) {
            if (DebugLog.DEBUG) Log.d(TAG, "getMmsData prevMMSID: " + prevMMSID + " - nextID: " + nextID);
            int type = cur.getInt(TYPE_COLUMN);
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData - type: " + type);
            if (cur.getColumnIndex(MMS_DATE) == -1) continue;
            long date = cur.getLong(cur.getColumnIndexOrThrow(MMS_DATE)) * 1000L;
            String mmsDate = String.valueOf(date);
            handleMessage(nextID, type, mmsDate);
            if (!cur.moveToNext()) {
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        cur.close();
        prevMMSID  = firstID;
    }

    private void handleMessage(final long nextID, final int type, final String mmsDate) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                String mmsAddress = getAddress(nextID);
                switch (type) {
                    case 132:
                        values[MMS_RECEIVED] = mmsAddress + "+" + mmsDate;
                        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData RECEIVED: " + mmsAddress + " - " + mmsDate);
                        values[MMS_SENT] = null;
                        performUpdates();
                        break;
                    case 128:
                        values[MMS_SENT] = mmsAddress + "+" + mmsDate;
                        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMmsData SENT: " + mmsAddress + " - " + mmsDate);
                        values[MMS_RECEIVED] = null;
                        performUpdates();
                        break;
                    default:
                        break;
                }
            }
        }, 100);
    }

    private String getAddress(long id) {
        Uri uriAddress = Uri.parse("content://mms/" + id + "/addr");
        String[] selectAddr = {"address"};
        Cursor curAddress = mmsResolver.query(uriAddress, selectAddr, "msg_id=" + id, null, null);
        String address = "";
        String val;
        if (curAddress == null){
            return "Null";
        }
        if (curAddress.moveToFirst()) {
            do {
                val = curAddress.getString(curAddress.getColumnIndex("address"));
                if (val != null) {
                    val = val.replaceAll("[^0-9]", "");
                    if (!val.equals("")) {
                        address = val;
                        break;
                    }
                }
            } while (curAddress.moveToNext());
        }
        if (curAddress != null) {
            curAddress.close();
        }
        return address;
    }

    @Override
    protected void updateObserver() {
        adminObserver.setValue(Metrics.MMSSENT, values[MMS_SENT] == "" ? 0 : values[MMS_SENT].split("\\|").length);
        adminObserver.setValue(Metrics.MMSRECEIVED, values[MMS_RECEIVED] == "" ? 0 : values[MMS_RECEIVED].split("\\|").length);
    }
}
