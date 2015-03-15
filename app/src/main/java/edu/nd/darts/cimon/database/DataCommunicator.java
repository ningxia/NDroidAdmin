package edu.nd.darts.cimon.database;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Communication facility for uploading to server
 *
 * @author Xiao(Sean) Bo
 *
 */
public class DataCommunicator {
    private URL url;
    private String url_c = "http://10.11.133.3:8100/Update_Data/";
    private HttpURLConnection connection = null;

    public DataCommunicator() throws MalformedURLException {
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
        } finally {
            connection.disconnect();
            return callBack;
        }
    }
}
