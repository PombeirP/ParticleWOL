package com.pedropombeiro.sparkwol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends ActionBarActivity {

    private InvokeSparkRestMethod invokeSparkRestMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent myIntent = new Intent(this, SettingsActivity.class);
            //myIntent.putExtra("key", value); //Optional parameters
            this.startActivity(myIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onWakeComputerButtonClick(View view) {
        invokeSparkRestMethod = new InvokeSparkRestMethod();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        invokeSparkRestMethod.execute("wakeHome", sharedPreferences.getString("device_id", ""), sharedPreferences.getString("authentication_token", ""));
    }

    private class InvokeSparkRestMethod extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {

            String methodName = params[0];
            String deviceId = params[1];
            String authenticationToken = params[2];
            String urlString = String.format("https://api.spark.io/v1/devices/%s/%s", deviceId, methodName); // URL to call
            String result = "";

            // HTTP Post
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Authorization", String.format("Bearer %s", authenticationToken));

                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i("FromOnPostExecute", result);
        }
    }
}
