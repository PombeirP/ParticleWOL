package com.pedropombeiro.sparkwol;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Pedro on 14.01.2015.
 */
abstract class InvokeHttpMethodTaskBase extends AsyncTask<String, Void, String> {
    protected final String authenticationToken;

    protected InvokeHttpMethodTaskBase(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

    // convert inputStream to String
    protected String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    protected abstract String GetUrl();

    protected abstract void onPostExecute(String result);
}
