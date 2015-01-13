package com.pedropombeiro.sparkwol;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
* Created by Pedro on 13.01.2015.
*/
abstract class InvokeSparkGetMethodTaskBase extends AsyncTask<String, Void, String> {
    protected final String authenticationToken;

    protected InvokeSparkGetMethodTaskBase(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    private String GET(String url){
        InputStream inputStream = null;
        String result = "";
        try {
            // create HttpClient
            HttpClient httpclient = new DefaultHttpClient();

            // make GET request to the given URL
            HttpGet request = new HttpGet(url);
            request.addHeader("Authorization", String.format("Bearer %s", this.authenticationToken));

            HttpResponse httpResponse = httpclient.execute(request);

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

            // convert inputstream to string
            if(inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
        }

        return result;
    }

    // convert inputstream to String
    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    @Override
    protected String doInBackground(String... params) {
        return GET(this.GetUrl());
    }

    protected abstract String GetUrl();

    protected abstract void onPostExecute(String result);
}
