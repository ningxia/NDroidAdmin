package edu.nd.darts.cimon;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONObject;

import edu.nd.darts.cimon.database.DataCommunicator;

/**
 * Created by xiaobo on 9/9/15.
 */
public class PingService extends Service {

    private static final String TAG = "CimonReminderService";
    private static final int period = 1000 * 15;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        pingServer();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate(){}

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private void pingServer(){
        Log.d(TAG,"Start Ping Service");
        final Handler handler = new Handler();
        final Runnable worker = new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() {
                        JSONObject mainPackage = new JSONObject();
                        try {
                            DataCommunicator comm = new DataCommunicator();
                            mainPackage.put("table", "Ping");
                            String deviceID = UploadingService.getDeviceID();
                            mainPackage.put("device_id", deviceID);
                            String callBack = comm.postData(mainPackage.toString().getBytes());
                            //if(DebugLog.DEBUG)
                                Log.d(TAG,"Ping Callback:"+callBack);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                handler.postDelayed(this, period);
            }
        };
        handler.postDelayed(worker, period);
    }
}
