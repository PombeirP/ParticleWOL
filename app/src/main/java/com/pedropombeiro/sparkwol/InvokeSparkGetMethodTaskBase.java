package com.pedropombeiro.sparkwol;

import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.InputStream;

/**
* Created by Pedro on 13.01.2015.
*/
abstract class InvokeSparkGetMethodTaskBase extends InvokeHttpMethodTaskBase {
    protected InvokeSparkGetMethodTaskBase(String authenticationToken) {
        super(authenticationToken);
    }

    private String GET(String url){
        InputStream inputStream = null;
        String result = "";
        try {
            // create HttpClient
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams httpParameters = httpclient.getParams();

            HttpConnectionParams.setConnectionTimeout(httpParameters, 5000);
            HttpConnectionParams.setSoTimeout(httpParameters, 10000);

            // make GET request to the given URL
            HttpGet request = new HttpGet(url);
            request.addHeader("Authorization", String.format("Bearer %s", this.authenticationToken));

            HttpResponse httpResponse = httpclient.execute(request);

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

            // convert inputStream to string
            if(inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";
        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
        }

        return result;
    }

    @Override
    protected String doInBackground(String... params) {
        return GET(this.GetUrl());
    }

    protected abstract String GetUrl();

    protected abstract void onPostExecute(String result);
}

