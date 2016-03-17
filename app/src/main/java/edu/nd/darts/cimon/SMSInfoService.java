package edu.nd.darts.cimon;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.os.Handler;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.Locale;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * SMS information service
 * @author ningxia
 */
public final class SMSInfoService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int SMS_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "SMS information activity";
    private static final String[] metrics = {"SMS Sent", "SMS Received"};
    private static final int SMS_SENT = Metrics.SMSSENT - Metrics.SMS_INFO_CATEGORY;
    private static final int SMS_RECEIVED = Metrics.SMSRECEIVED - Metrics.SMS_INFO_CATEGORY;
    private static final SMSInfoService INSTANCE = new SMSInfoService();
    private static String description;

    public static final String SMS_ADDRESS = "address";
    public static final String SMS_DATE = "date";
    public static final String SMS_TYPE = "type";
    public static final String SMS_BODY = "body";
    public static final String SMS_PROTOCOL = "protocol";

    private static final int MESSAGE_TYPE_INBOX  = 1;
    private static final int MESSAGE_TYPE_SENT   = 6;

    private static final Uri uri = Uri.parse("content://sms/");
    private static final String[] sms_projection = new String[]{BaseColumns._ID,
            SMS_ADDRESS, SMS_DATE, SMS_TYPE, SMS_BODY, SMS_PROTOCOL};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevSMSID  = -1;

    private ContentObserver smsObserver = null;
    private ContentResolver smsResolver;

    private SMSInfoService() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("SMSInfoService already instantiated");
        }
        groupId = Metrics.SMS_INFO_CATEGORY;
        metricsCount = SMS_METRICS;

        values = new String[SMS_METRICS];
        valueNodes = new SparseArray<ValueNode<String>>();
        freshnessThreshold = THIRTY_SECONDS;
        smsResolver = MyApplication.getAppContext().getContentResolver();
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<TimerNode>();
        init();
    }

    public static SMSInfoService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    /**
     * Content observer to be notified of changes to SMS database tables.
     * @author darts
     *
     */
    private class SmsContentObserver extends ContentObserver {

        public SmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService - SmsContentObserver: changed");
            getSmsData();
            super.onChange(selfChange);
        }
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getMetricInfo - updating sms activity value");

        if (prevSMSID  < 0) {
            if (smsObserver == null) {
                smsObserver = new SmsContentObserver(metricHandler);
            }
            smsResolver.registerContentObserver(uri, true, smsObserver);

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
        for (int i = 0; i < SMS_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    private void updateSmsData() {
        Cursor cur = smsResolver.query(uri, sms_projection, SMS_TYPE + "=?",
                new String[]{String.valueOf(MESSAGE_TYPE_INBOX)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - incoming sms cursor empty?");
            values[SMS_RECEIVED] = "";
        }
        else {
            prevSMSID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - received: " + values[SMS_RECEIVED]);
        cur.close();

        cur = smsResolver.query(uri, sms_projection, SMS_TYPE + "=?",
                new String[] {String.valueOf(MESSAGE_TYPE_SENT)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "SMSService.updateSmsData - sent sms cursor empty?");
            values[SMS_SENT] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            if (topID > prevSMSID) {
                prevSMSID = topID;
            }
        }
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - sent: " + values[SMS_SENT]);
        cur.close();
    }

    private void getSmsData() {
        Cursor cur = smsResolver.query(uri, sms_projection, null, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData IDs: " + prevSMSID + " - " + nextID);
        while (nextID > prevSMSID) {
            if (cur.getColumnIndex(SMS_PROTOCOL) == -1) continue;
            if (cur.getColumnIndex(SMS_ADDRESS) == -1) continue;
            String protocol = cur.getString(cur.getColumnIndexOrThrow(SMS_PROTOCOL));
            String smsAddress = cur.getString(cur.getColumnIndexOrThrow(SMS_ADDRESS));
            if (smsAddress != null) {
                smsAddress = smsAddress.replaceAll("[^0-9]", "");
            }
            long smsDate = cur.getLong(cur.getColumnIndexOrThrow(SMS_DATE));

            if (protocol != null) {
                values[SMS_RECEIVED] = smsAddress + "+" + smsDate;
                values[SMS_SENT] = null;
                if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData RECEIVED: " + smsAddress + " - " + smsDate);
            }
            else {
                values[SMS_SENT] = smsAddress + "+" + smsDate;
                values[SMS_RECEIVED] = null;
                if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData SENT: " + smsAddress + " - " + smsDate);
            }

            performUpdates();

            if (!cur.moveToNext()) {
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        cur.close();
        prevSMSID  = firstID;
    }

    @Override
    protected void updateObserver() {
        adminObserver.setValue(Metrics.SMSSENT, values[SMS_SENT] == "" ? 0 : values[SMS_SENT].split("\\|").length);
        adminObserver.setValue(Metrics.SMSRECEIVED, values[SMS_RECEIVED] == "" ? 0 : values[SMS_RECEIVED].split("\\|").length);
    }

    @Override
    protected void performUpdates() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.performUpdates - updating values");
        long nextUpdate = updateValueNodes();
        if (nextUpdate < 0) {
            smsResolver.unregisterContentObserver(smsObserver);
            prevSMSID  = -1;
        }
        updateObservable();
    }
}
