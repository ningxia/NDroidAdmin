package edu.nd.darts.cimon.database;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
//import java.security.Key;
//import java.security.NoSuchAlgorithmException;


//import javax.crypto.Cipher;
//import javax.crypto.KeyGenerator;
//import javax.crypto.NoSuchPaddingException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.SecretKeySpec;
//import javax.xml.bind.DatatypeConverter;

/**
 * Communication facility for uploading to server
 *
 * @author Xiao(Sean) Bo
 *
 */
public class DataCommunicator {
    private URL url;
    private String url_c = "http://129.74.152.106:8300/Update_Data/";
    private HttpURLConnection connection = null;
//    private Cipher cipher;
//    private final SecretKey secretKey;

    public DataCommunicator() throws MalformedURLException{
//        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
//        keyGenerator.init(128);
//        secretKey = new SecretKeySpec(new String("javax.crypto.spec.SecretKeySpec@fffffe7d").getBytes(), "AES");
//        cipher = Cipher.getInstance("AES");
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
     * Post data to server
     *
     * @param data             Data package for uplodaing
     *
     * @author Xiao(Sean Bo)
     *
     */
    public String postData(byte[] data) {
        String callBack = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("content-type",
                    "application/json; charset=utf-8");

//            cipher.init(Cipher.ENCRYPT_MODE,secretKey);
//            data = cipher.doFinal(data);
//            Log.d("encryption",secretKey.toString());
//
//            Log.d("encryption",data.toString());

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
