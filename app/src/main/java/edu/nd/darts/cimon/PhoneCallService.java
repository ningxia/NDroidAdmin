package edu.nd.darts.cimon;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.Locale;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;

/**
 * Monitoring service for phone call information metrics
 *
 * <li>Outgoing phone calls</li>
 * <li>Incoming phone calls</li>
 * <li>Missed phone calls</li>
 *
 * @author ningxia
 */
public final class PhoneCallService extends MetricService<String> {

    private static final String TAG = "NDroid";
    private static final int PHONE_METRICS = 3;
    private static final long THIRTY_SECONDS = 30000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Phone call activity";
    private static final String[] metrics = {"Outgoing calls", "Incoming calls", "Missed calls"};
    private static final int OUTGOING =		Metrics.PHONE_CALL_OUTGOING - Metrics.PHONE_CALL_CATEGORY;
    private static final int INCOMING =		Metrics.PHONE_CALL_INCOMING - Metrics.PHONE_CALL_CATEGORY;
    private static final int MISSED =		Metrics.PHONE_CALL_MISSED - Metrics.PHONE_CALL_CATEGORY;
    private static final PhoneCallService INSTANCE = new PhoneCallService();
    private static String description;
    private static int PHONE_STATE =	0;

    private static final Uri phone_uri = CallLog.Calls.CONTENT_URI;
    private static final String[] phone_projection = new String[]{CallLog.Calls._ID,
            CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.NUMBER};	//CallLog.Calls.NUMBER

    private FinishPerformUpdates finishUpdates = null;
    TelephonyManager telephonyManager;
    private static final String SORTORDER = CallLog.Calls._ID + " DESC";
    private long prevPhoneID  = -1;

    private PhoneStateListener phoneStateListener = null;

    /**
     * Listener to handle notifications of phone state changes.
     * @author darts
     *
     */
    private class MyPhoneStateListener extends PhoneStateListener {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            PHONE_STATE = state;
            if (finishUpdates == null) {
                finishUpdates = new FinishPerformUpdates();
                metricHandler.post(finishUpdates);
            }
            super.onCallStateChanged(state, incomingNumber);
        }
    }

    private PhoneCallService() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("PhoneStateService already instantiated");
        }
        groupId = Metrics.PHONE_CALL_CATEGORY;
        metricsCount = PHONE_METRICS;

        telephonyManager = (TelephonyManager) MyApplication.getAppContext(
        ).getSystemService(Context.TELEPHONY_SERVICE);
        values = new String[PHONE_METRICS];
        valueNodes = new SparseArray<>();
        freshnessThreshold = THIRTY_SECONDS;
        adminObserver = UserObserver.getInstance();
        adminObserver.registerObservable(this, groupId);
        schedules = new SparseArray<>();
        init();
    }

    public static PhoneCallService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        switch (telephonyManager.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_NONE:
                description = "No cellular radio ";
                break;
            case TelephonyManager.PHONE_TYPE_GSM:
                description = "GSM ";
                break;
            case TelephonyManager.PHONE_TYPE_CDMA:
                description = "CDMA ";
                break;
            default:
                description = "SIP ";
                break;
        }
        String operator = telephonyManager.getNetworkOperatorName();
        if ((operator != null) && (operator.length() > 0)) {
            description = description + " (" + operator + ")";
        }
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(TelephonyManager.CALL_STATE_OFFHOOK),
                "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < PHONE_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 200);
        }
    }

    @Override
    void getMetricInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getMetricInfo - updating phone state value");

        if (prevPhoneID  < 0) {
            updateTelephonyData();
        }
        if (phoneStateListener == null) {
            phoneStateListener = new MyPhoneStateListener();
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Update the values for telephony metrics from telephony database tables.
     */
    private void updateTelephonyData() {
        ContentResolver resolver = MyApplication.getAppContext().getContentResolver();
        Cursor cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[] {String.valueOf(CallLog.Calls.INCOMING_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - incoming call cursor empty?");
            values[INCOMING] = "";
        }
        else {
            prevPhoneID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        }
        cur.close();

        cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[] {String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - outgoing call cursor empty?");
            values[OUTGOING] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
            if (topID > prevPhoneID) {
                prevPhoneID = topID;
            }
        }
        cur.close();

        cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[] {String.valueOf(CallLog.Calls.MISSED_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - missed call cursor empty?");
            values[MISSED] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
            if (topID > prevPhoneID) {
                prevPhoneID = topID;
            }
        }
        cur.close();
    }

    /**
     * Update the values for telephony metrics from telephony database tables.
     */
    private void getTelephonyData() {
        ContentResolver resolver = MyApplication.getAppContext().getContentResolver();
        Cursor cur = resolver.query(phone_uri, phone_projection, null, null, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getTelephonyData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        long nextID = firstID;
        StringBuilder sbIncoming = new StringBuilder();
        StringBuilder sbOutgoing = new StringBuilder();
        StringBuilder sbMissed = new StringBuilder();
        while (nextID != prevPhoneID) {
			final int NUMBER_COLUMN = cur.getColumnIndex(CallLog.Calls.NUMBER);
			final int DATE_COLUMN = cur.getColumnIndex(CallLog.Calls.DATE);
            final int DURATION_COLUMN = cur.getColumnIndex(CallLog.Calls.DURATION);
            final int TYPE_COLUMN = cur.getColumnIndex(CallLog.Calls.TYPE);

            String phoneNumber = cur.getString(cur.getColumnIndex(CallLog.Calls.CACHED_NAME)) == null ?
                    "Unknown Number" : cur.getString(NUMBER_COLUMN);
            String startTime = getDate(cur.getLong(DATE_COLUMN), "hh:ss MM/dd/yyyy");
            String endTime = getDate(cur.getLong(DATE_COLUMN) + cur.getLong(DURATION_COLUMN), "hh:ss MM/dd/yyyy");

            int type = cur.getInt(TYPE_COLUMN);

            switch (type) {
                case CallLog.Calls.OUTGOING_TYPE:
                    appendCalls(sbOutgoing, phoneNumber, startTime, endTime);
                    break;
                case CallLog.Calls.INCOMING_TYPE:
                    appendCalls(sbIncoming, phoneNumber, startTime, endTime);
                    break;
                case CallLog.Calls.MISSED_TYPE:
                    appendCalls(sbMissed, phoneNumber, startTime, endTime);
                    break;
                default:
                    break;
            }

            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getTelephonyData - type: " + type);

            if (!cur.moveToNext()) {
                values[INCOMING] = sbIncoming.substring(0, sbIncoming.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - incoming: " + values[INCOMING]);
                values[OUTGOING] = sbOutgoing.substring(0, sbOutgoing.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - outgoing: " + values[OUTGOING]);
                values[MISSED] = sbMissed.substring(0, sbMissed.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - missed: " + values[MISSED]);
                break;
            }
            nextID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        }

        cur.close();
        prevPhoneID = firstID;

    }

    private String getDate(long milliSeconds, String dateFormat) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.US);
        return formatter.format(milliSeconds);
    }

    private void appendCalls(StringBuilder sb, String phoneNumber, String startTime, String endTime) {
        sb.append(phoneNumber)
                .append("+")
                .append(startTime)
                .append("+")
                .append(endTime)
                .append("|");
    }

    /**
     * Runnable to schedule updates following phone state changes.
     * @author darts
     *
     */
    private class FinishPerformUpdates implements Runnable{
        public void run() {
            if (PHONE_STATE == TelephonyManager.CALL_STATE_IDLE) {
                getTelephonyData();
            }
            finishUpdates = null;
            performUpdates();
        }}

    @Override
    protected void performUpdates() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.performUpdates - updating values");
        long nextUpdate = updateValueNodes();
        if (nextUpdate < 0) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            prevPhoneID  = -1;
        }
        updateObservable();
    }

    @Override
    String getMetricValue(int metric) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getMetricValue - getting metric: "+ metric);
        return values[metric - groupId];
    }

    @Override
    protected void updateObserver() {
        adminObserver.setValue(Metrics.PHONE_CALL_OUTGOING, values[OUTGOING].split("\\|").length);
        adminObserver.setValue(Metrics.PHONE_CALL_INCOMING, values[INCOMING].split("\\|").length);
        adminObserver.setValue(Metrics.PHONE_CALL_MISSED, values[MISSED].split("\\|").length);
    }
}
