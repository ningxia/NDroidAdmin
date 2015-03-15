package edu.nd.darts.cimon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;

import edu.nd.darts.cimon.database.CimonDatabaseAdapter;
import edu.nd.darts.cimon.database.DataCommunicator;
import edu.nd.darts.cimon.database.DataTable;
import edu.nd.darts.cimon.database.LabelingHistory;
import edu.nd.darts.cimon.database.MetricInfoTable;

/**
 * Upadloing Service For Data
 *
 * @author Xiao(Sean) Bo
 *
 */
public class UploadingService  extends Service {
    private static final String[] uploadTables = {DataTable.TABLE_DATA,
            MetricInfoTable.TABLE_METRICINFO, LabelingHistory.TABLE_NAME};
    private static final int period = 10000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduleUploading();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private void scheduleUploading() {
        final Handler handler = new Handler();

        final Runnable worker = new Runnable() {
            public void run() {
                runUpload();
                handler.postDelayed(this, period);
            }
        };
        handler.postDelayed(worker, period);
    }

    /**
     * One iteration of upload.
     *
     * @author Xiao(Sean) Bo
     *
     */
    private void runUpload() {
        Calendar timeConverter = Calendar.getInstance();
        timeConverter.set(Calendar.HOUR_OF_DAY, 0);
        long startTime = timeConverter.getTimeInMillis();
        timeConverter.set(Calendar.HOUR_OF_DAY, 24);
        long endTime = timeConverter.getTimeInMillis();
        long currentTime = System.currentTimeMillis();
        if (currentTime >= startTime && currentTime <= endTime
                && CimonDatabaseAdapter.database != null) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        for (String table : uploadTables) {
                            uploadFromTable(table);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }


    /**
     * Update from certain table.
     *
     * @author Xiao(Sean) Bo
     *
     * @param tableName table to update
     *
     */
    private void uploadFromTable(String tableName) throws JSONException,
            MalformedURLException {
        Cursor cursor = this.getCursor(tableName);
        cursor.moveToFirst();
        JSONArray records = new JSONArray();
        String[] columnNames = cursor.getColumnNames();
        ArrayList<Integer> rowIDs = new ArrayList<Integer>();
        while (!cursor.isAfterLast()) {
            JSONObject record = new JSONObject();
            rowIDs.add(new Integer(cursor.getInt(0)));
            for (String columnName : columnNames) {
                if (columnName.equals("_id"))
                    continue;
                int columnIndex = cursor.getColumnIndex(columnName);
                switch (cursor.getType(columnIndex)) {
                    case Cursor.FIELD_TYPE_STRING:
                        record.put(columnName, cursor.getString(columnIndex));
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        record.put(columnName,
                                Integer.toString(cursor.getInt(columnIndex)));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        record.put(columnName,
                                Float.toString(cursor.getFloat(columnIndex)));
                        break;
                    default:
                        record.put(columnName, "");
                        break;
                }
            }
            records.put(record);
            if (records.length() >= 40)
                batchUpload(records, tableName, rowIDs);
            cursor.moveToNext();
        }
        batchUpload(records, tableName, rowIDs);
    }

    /**
     * Upload batch data to server.
     *
     * @author Xiao(Sean) Bo
     *
     * @param records Array of JSON
     * @param tableName table to update
     * @param rowIDs Corresponding row IDs of JSON
     *
     */

    private void batchUpload(JSONArray records, String tableName,
                             ArrayList<Integer> rowIDs) throws MalformedURLException,
            JSONException {
        DataCommunicator comm = new DataCommunicator();
        JSONObject mainPackage = new JSONObject();
        mainPackage.put("type", "Test");
        mainPackage.put("records", records);
        mainPackage.put("table", tableName);
        String deviceID = getDeviceID();
        mainPackage.put("device_id", deviceID);
        String callBack = comm.postData(mainPackage.toString().getBytes());
        if (callBack.equals("Success")
                && (tableName.equals("data") || tableName
                .equals("labelling_history")))
            garbageCollection(rowIDs, tableName);
    }

    /**
     * Upload batch data to server.
     *
     * @author Xiao(Sean) Bo
     *
     * @param tableName table to update
     * @param rowIDs Corresponding row IDs to delete
     *
     */

    private static void garbageCollection(ArrayList<Integer> rowIDs,
                                          String tableName) {
        SQLiteDatabase curDB = tableName.equals(LabelingHistory.TABLE_NAME) ? LabelingHistory.db
                : CimonDatabaseAdapter.database;
        for (Integer rowID : rowIDs) {
            curDB.delete(tableName, "_id = " + rowID.toString(), null);
        }
        rowIDs.clear();
    }

    /**
     * Get device ID.
     *
     * @author Xiao(Sean) Bo
     *
     *
     */

    private String getDeviceID() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getDeviceId();
    }

    private void sendDebuginfo(String msg) throws MalformedURLException,
            JSONException {
        DataCommunicator comm = new DataCommunicator();
        JSONObject debugInfo = new JSONObject();
        debugInfo.put("type", "Test");
        debugInfo.put("info", msg);
        comm.postData(debugInfo.toString().getBytes());
    }

    private Cursor getCursor(String tableName) {
        if (tableName.equals(LabelingHistory.TABLE_NAME))
            return LabelingHistory.db.rawQuery("SELECT * FROM " + tableName,
                    null);
        else
            return CimonDatabaseAdapter.database.rawQuery("SELECT * FROM "
                    + tableName, null);
    }
}
