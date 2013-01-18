package com.orbotix.att.command;

import android.os.AsyncTask;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

/**
 * Created by Orbotix Inc.
 * User: brandon
 * Date: 10/3/12
 * Time: 3:55 PM
 */
public class SpeechWebServiceCall {
    public static final int SPHEROVERSE_BAD_URL = 1001;
    public static final int SPHEROVERSE_GOOD_CALL = 1042;
    public static final int SPHEROVERSE_ERROR = 1013;

    public enum SenderIdentifier {ball_stats, login, profile, user_id, diagnostics}

    private SenderIdentifier mSenderIdentifier;
    private String mUrl;
    private String mPayload;
    private SpeechCallCompletedListener mListener;
    private String mData;
    private String mAuthorization;
    private boolean hasData;
    private HttpURLConnection mConnection;
    private static String sAppId, sAppSecret;
    private String mRequestMethod;

    public SpeechWebServiceCall() {
        new SpeechWebServiceCall(null);
    }

    public SpeechWebServiceCall(String url) {
        mUrl = url;
        hasData = false;
        mAuthorization = null;
        mRequestMethod = "GET";
    }

    public int makeCallSynchronously() {
        return call();
    }

    public void makeCallAsynchronously() {
        WebCallAsyncTask callTask = new WebCallAsyncTask();
        callTask.execute();
    }

    private Integer call() {
        URL dataUrl = null;
        Authenticator.setDefault(new SpheroVerseAuthenticator());
        try {
            dataUrl = new URL(mUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            //SpheroVerseConfig.d("Something wrong with the web service url");
            return SPHEROVERSE_BAD_URL;
        }

        Integer errorCode = SPHEROVERSE_ERROR;
        hasData = false;
        try {
            mConnection = (HttpURLConnection)dataUrl.openConnection();
            mConnection.setUseCaches(false);
            if (mAuthorization != null) {
                mConnection.setRequestProperty("Authorization", mAuthorization);
            }
            if (mPayload != null) {
                mConnection.setRequestMethod(mRequestMethod);
                mConnection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");

                mConnection.setRequestProperty("Content-Length", "" +
                        Integer.toString(mPayload.getBytes().length));
                mConnection.setRequestProperty("Content-Language", "en-US");

                mConnection.setDoInput(true);
                mConnection.setDoOutput(true);

                //Send request
                DataOutputStream wr = new DataOutputStream(
                        mConnection.getOutputStream());
                wr.writeBytes(mPayload);
                wr.flush();
                wr.close();
            }

            int responseCode = mConnection.getResponseCode();

            switch (responseCode) {
                case HttpsURLConnection.HTTP_OK:

                    BufferedInputStream jsonInputStream = new BufferedInputStream(mConnection.getInputStream());
                    ByteArrayOutputStream jsonOutputStream = new ByteArrayOutputStream();
                    byte[] byteChunk = new byte[8192];
                    int n;
                    while ( (n = jsonInputStream.read(byteChunk)) > 0 ) {
                        jsonOutputStream.write(byteChunk, 0, n);
                    }

                    mData = new String(jsonOutputStream.toByteArray());
                    hasData = !mData.equalsIgnoreCase("");
                    errorCode = SPHEROVERSE_GOOD_CALL;
                    break;

                case HttpsURLConnection.HTTP_UNAUTHORIZED:
                    //SpheroVerseConfig.d("401 Not Authorized");
                    break;
                case HttpsURLConnection.HTTP_NOT_FOUND:
                    //SpheroVerseConfig.d("404 Not Found");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
            //SpheroVerseConfig.d(String.format("Cannot open the connection to: %s", dataUrl.toString()));
        } finally {
            if (mConnection != null) {
                mConnection.disconnect();
            }
        }

        return errorCode;
    }

    public SenderIdentifier getSenderIdentifier() {
        return mSenderIdentifier;
    }

    public void setSenderIdentifier(SenderIdentifier senderIdentifier) {
        mSenderIdentifier = senderIdentifier;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public String getPayload() {
        return mPayload;
    }

    public void setPayload(String payload) {
        mPayload = payload;
    }

    public SpeechCallCompletedListener getListener() {
        return mListener;
    }

    public void setListener(SpeechCallCompletedListener listener) {
        mListener = listener;
    }

    public String getData() {
        return mData;
    }

    public boolean hasData() {
        return hasData;
    }

    private void deliverData(int resultCode) {
        if (mListener != null) {
            mListener.onCallCompleted(this, resultCode);
        }
    }

    public HttpURLConnection getConnection() {
        return mConnection;
    }

    public static String getAppId() {
        return sAppId;
    }

    public static void setAppId(String appId) {
        sAppId = appId;
    }

    public static String getAppSecret() {
        return sAppSecret;
    }

    public static void setAppSecret(String sAppSecret) {
        SpeechWebServiceCall.sAppSecret = sAppSecret;
    }

    public void setAuthorization(String authorization) {
        mAuthorization = authorization;
    }

    public void setRequestMethod(String requestMethod) {
        mRequestMethod = requestMethod;
    }

    private class WebCallAsyncTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... voids) {
            return call();
        }

        @Override
        protected void onPostExecute(Integer errorCode) {
            deliverData(errorCode);
            super.onPostExecute(errorCode);
        }
    }

    public interface SpeechCallCompletedListener {
        public void onCallCompleted(SpeechWebServiceCall call, int speechResponseCode);
    }

    private class SpheroVerseAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            PasswordAuthentication auth = null;
            if (sAppId != null && sAppSecret != null) {
                auth = new PasswordAuthentication(sAppId, sAppSecret.toCharArray());
            } else {
                auth = super.getPasswordAuthentication();
            }
            return auth;
        }
    }
}
