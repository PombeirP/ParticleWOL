package com.pedropombeiro.sparkwol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends ActionBarActivity {

    private InvokeSparkPostMethodTask invokeSparkPostMethodTask;

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
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.invokeSparkPostMethodTask = new InvokeSparkPostMethodTask("wakeHome", sharedPreferences.getString("device_id", ""), sharedPreferences.getString("authentication_token", ""));
        this.invokeSparkPostMethodTask.execute();
    }

    private class InvokeSparkPostMethodTask extends AsyncTask<String, String, String> {

        private final String method;
        private final String deviceId;
        private final String authenticationToken;

        public InvokeSparkPostMethodTask(String method, String deviceId, String authenticationToken) {
            this.method = method;
            this.deviceId = deviceId;
            this.authenticationToken = authenticationToken;
        }

        @Override
        protected String doInBackground(String... params) {
            String urlString = String.format("https://api.spark.io/v1/devices/%s/%s", this.deviceId, this.method); // URL to call
            String result = "";

            // HTTP Post
            try {
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Authorization", String.format("Bearer %s", this.authenticationToken));

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
            Runnable getStateVariableRunnable = new Runnable() {
                @Override
                public void run() {
                    new InvokeSparkGetStateVariableMethodTask(deviceId, authenticationToken).execute();
                }
            };

            Handler handler = new Handler();
            handler.postDelayed(getStateVariableRunnable, 2000);

            Toast.makeText(getBaseContext(), "Sent wake request", Toast.LENGTH_LONG).show();
            Log.i("FromOnPostExecute", result);
        }
    }

    private class InvokeSparkGetStateVariableMethodTask extends InvokeSparkGetMethodTaskBase {
        private String deviceId;

        public InvokeSparkGetStateVariableMethodTask(String deviceId, String authToken) {
            super(authToken);
            this.deviceId = deviceId;
        }

        @Override
        protected String GetUrl() {
            return String.format("https://api.spark.io/v1/devices/%s/state", this.deviceId);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject response = new JSONObject(result);

                String state = response.getString("result");
                switch (state)
                {
                    case "Pinging":
                    {
                        Runnable runnable = new Runnable() {
                            @Override
                            public void run() {
                                new InvokeSparkGetStateVariableMethodTask(deviceId, authenticationToken).execute();
                            }
                        };

                        Handler handler = new Handler();
                        handler.postDelayed(runnable, 2000);
                        break;
                    }
                    case "Unreachable":
                        Toast.makeText(getBaseContext(), "Could not contact the target machine :-(", Toast.LENGTH_LONG).show();
                        break;
                    case "Reachable":
                        Toast.makeText(getBaseContext(), "Machine is awake!", Toast.LENGTH_LONG).show();
                        break;
                }
            } catch (JSONException e) {
                Toast.makeText(getBaseContext(), "Could not retrieve status from Spark", Toast.LENGTH_LONG).show();
                Log.w("FromOnPostExecute", e.getMessage());
            }
        }
    }
}