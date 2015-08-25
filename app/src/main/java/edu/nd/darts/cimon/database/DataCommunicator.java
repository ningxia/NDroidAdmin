package edu.nd.darts.cimon.database;

//import android.util.Log;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import edu.nd.darts.cimon.MyApplication;

/**
 * Communication facility for uploading to server
 *
 * @author Xiao(Sean) Bo
 *
 */
public class DataCommunicator {
    private URL url;
    private String url_c = "http://129.74.246.85:8100/Update_Data/";
    //private String url_c = "http://10.11.132.136:8300/Update_Data/";
    private HttpURLConnection connection = null;

    public DataCommunicator() throws MalformedURLException{
        this.url = new URL(url_c);
    }

    /**
     * Convert inputStream to regular string
     *
     * @param is             Input stream acquired from server
     *
     * @author Xiao(Sean Bo)
     *
     */
    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    /**
     * Check if WIFI is connected.
     *
     * @author Xiao(Sean) Bo
     *
     */

    private boolean isWifiConnected(){
        Context context = MyApplication.getAppContext();
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }

    /**
     * Post data to server
     *
     * @param data             Data package for uplodaing
     *
     * @author Xiao(Sean Bo)
     *
     */
    public String postData(byte[] data) {
        String callBack = null;
        if(!isWifiConnected()){
            return "Wifi Failed";
        }
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("content-type",
                    "application/json; charset=utf-8");

            // Send data
            OutputStream out = new BufferedOutputStream(
                    connection.getOutputStream());
            out.write(data);
            out.flush();

            // Get call back
            InputStream in = new BufferedInputStream(
                    connection.getInputStream());
            if (in != null) {
                callBack = this.convertStreamToString(in);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Fail";
        } finally {
            connection.disconnect();
            return callBack;
        }
    }
}
