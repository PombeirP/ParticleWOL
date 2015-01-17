package com.pedropombeiro.sparkwol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;


public class MainActivity extends ActionBarActivity {
    SparkService sparkService;

    Button wakeComputerButton;
    Button refreshButton;
    Button flashSparkButton;
    TextView messageTextView;
    ProgressBar progress;
    private SparkDevice selectedSparkDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.createSparkService();

        this.wakeComputerButton = (Button) findViewById(R.id.wakeComputerButton);
        this.refreshButton = (Button) findViewById(R.id.refreshButton);
        this.flashSparkButton = (Button) findViewById(R.id.flashSparkButton);
        this.messageTextView = (TextView) findViewById(R.id.messageTextView);
        this.progress = (ProgressBar) findViewById(R.id.progress);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key == PreferenceKeys.DEVICE_ID || key == PreferenceKeys.AUTHENTICATION_TOKEN) {
                    String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
                    String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");
                    retrieveSparkDevice(deviceId, authenticationToken);
                }
            }
        });

        this.wakeComputerButton.setVisibility(View.INVISIBLE);
        UpdateUI();
    }

    private void createSparkService() {
        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");

                if (authenticationToken != "")
                    request.addHeader("Authorization", String.format("Bearer %s", authenticationToken));
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint("https://api.spark.io")
                .setRequestInterceptor(requestInterceptor)
                .build();
        this.sparkService = restAdapter.create(SparkService.class);
    }

    String getSparkDeviceName() {
        if (this.selectedSparkDevice != null)
            return this.selectedSparkDevice.name;

        return "Spark";
    }

    private void retrieveSparkDevice(String deviceId, String authenticationToken) {
        if (authenticationToken != "" && deviceId != "") {
            sparkService.getDevice(deviceId, new SetActiveDeviceCallback(deviceId));
        }
    }

    private void UpdateUI() {
        this.refreshButton.setVisibility(View.GONE);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
        String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");
        if (authenticationToken != "" && deviceId != "")
        {
            this.messageTextView.setText(String.format("Trying to connect to %s...", getSparkDeviceName()));
            this.progress.setVisibility(View.VISIBLE);

            this.retrieveSparkDevice(deviceId, authenticationToken);
        }
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
        final String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
        String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");
        String ipAddress = sharedPreferences.getString(PreferenceKeys.IP_ADDRESS, "");
        String macAddress = sharedPreferences.getString(PreferenceKeys.MAC_ADDRESS, "");
        if (macAddress == "")
            macAddress = NetworkHelpers.GetMacFromArpCache(ipAddress);

        if (deviceId.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Target Spark device not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (authenticationToken.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Authentication token not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (ipAddress.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Target IP address not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (macAddress == null || macAddress.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Could not retrieve target MAC address", Toast.LENGTH_LONG).show();
            return;
        }

        this.sparkService.invokeFunction(deviceId, "wakeHost", String.format("%s;%s", ipAddress, macAddress), new WakeUpMachineCallback(deviceId));
    }

    public void onRefreshButtonClick(View view) {
        this.UpdateUI();
    }

    public void onFlashSparkButtonClick(View view) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
        String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");

        if (deviceId.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Target Spark device not defined", Toast.LENGTH_LONG).show();
            return;
        }
        if (authenticationToken.length() == 0) {
            Toast.makeText(this.getBaseContext(), "Authentication token not defined", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            String firmware = convertStreamToString(getResources().openRawResource(R.raw.sparkscript));
            File firmwareFile = getBaseContext().getCacheDir().createTempFile("spark", "firmware");
            firmwareFile.deleteOnExit();
            BufferedWriter bw = new BufferedWriter(new FileWriter(firmwareFile));
            bw.write(firmware);
            bw.close();

            messageTextView.setText(String.format("Trying to connect to %s...", getSparkDeviceName()));
            progress.setVisibility(View.VISIBLE);
            flashSparkButton.setEnabled(false);

            this.sparkService.flashFirmware(new TypedFile("text/plain", firmwareFile), deviceId, new Callback<UploadSparkFirmwareResponse>() {
                @Override
                public void success(UploadSparkFirmwareResponse sparkResponse, Response response) {
                    try {
                        messageTextView.setText(sparkResponse.status);
                        //UpdateUI();
                    }
                    finally {
                        progress.setVisibility(View.GONE);
                    }
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    Toast.makeText(getBaseContext(), String.format("Could not flash %s", getSparkDeviceName()), Toast.LENGTH_LONG).show();
                    messageTextView.setText(String.format("Could not flash the %s: %s", getSparkDeviceName(), retrofitError.getResponse().getReason()));
                    flashSparkButton.setVisibility(View.GONE);
                    refreshButton.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                    Log.w("FromOnPostExecute", retrofitError.getMessage());
                }
            });
        }
        catch (Exception e) {
            Log.e("onFlashSparkButtonClick", e.getMessage());
        }
    }

    private String convertStreamToString(InputStream is) throws IOException {
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }

    private class DetermineSparkStatusCallback implements Callback<SparkVariable> {
        public DetermineSparkStatusCallback() {
        }

        @Override
        public void success(SparkVariable sparkVariable, Response response) {
            messageTextView.setText(String.format("%s is online.", getSparkDeviceName()));
            wakeComputerButton.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            progress.setVisibility(View.GONE);

            if (retrofitError.getResponse().getStatus() == 404) {
                messageTextView.setText(String.format("%s needs to be flashed with the WOL firmware.", getSparkDeviceName()));
                flashSparkButton.setEnabled(true);
                flashSparkButton.setVisibility(View.VISIBLE);
                refreshButton.setVisibility(View.GONE);
                return;
            }

            Toast.makeText(getBaseContext(), String.format("Could not retrieve status from %s", getSparkDeviceName()), Toast.LENGTH_LONG).show();
            messageTextView.setText(String.format("Could not retrieve status from %s: %s", getSparkDeviceName(), retrofitError.getResponse().getReason()));
            refreshButton.setVisibility(View.VISIBLE);
            Log.w("FromOnPostExecute", retrofitError.getMessage());
        }
    }

    private class WakeUpMachineCallback implements Callback<Response> {
        private final String deviceId;

        public WakeUpMachineCallback(String deviceId) {
            this.deviceId = deviceId;

            messageTextView.setText("Waking up machine...");
            wakeComputerButton.setVisibility(View.INVISIBLE);
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void success(Response sparkVariable, Response response) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    sparkService.getVariable("state", deviceId, new WaitForMachineToWakeUpCallback(deviceId));
                }
            };

            Handler handler = new Handler();
            handler.postDelayed(runnable, 2000);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Toast.makeText(getBaseContext(), String.format("Could not retrieve status from %s", getSparkDeviceName()), Toast.LENGTH_LONG).show();
            Log.w("FromOnPostExecute", retrofitError.getMessage());

            messageTextView.setText("");
            wakeComputerButton.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
        }
    }

    private class WaitForMachineToWakeUpCallback implements Callback<SparkVariable> {
        private final String deviceId;

        public WaitForMachineToWakeUpCallback(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public void success(SparkVariable sparkVariable, Response response) {
            switch (sparkVariable.result) {
                case "Sent WOL":
                case "Pinging": {
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            sparkService.getVariable("state", deviceId, new WaitForMachineToWakeUpCallback(deviceId));
                        }
                    };

                    Handler handler = new Handler();
                    handler.postDelayed(runnable, 2000);
                    break;
                }
                case "Unreachable":
                    Toast.makeText(getBaseContext(), "Target machine is unreachable", Toast.LENGTH_LONG).show();
                    messageTextView.setText(String.format("%s could not contact the target machine :-(", getSparkDeviceName()));
                    wakeComputerButton.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                    break;
                case "Reachable":
                    Toast.makeText(getBaseContext(), "Machine is awake!", Toast.LENGTH_LONG).show();
                    messageTextView.setText("Machine is awake!");
                    wakeComputerButton.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Toast.makeText(getBaseContext(), String.format("Could not retrieve status from %s", getSparkDeviceName()), Toast.LENGTH_LONG).show();
            Log.w("FromOnPostExecute", retrofitError.getMessage());

            messageTextView.setText(String.format("Could not retrieve status from %s:\n%s", getSparkDeviceName(), retrofitError.getResponse().getReason()));
            wakeComputerButton.setEnabled(true);
            progress.setVisibility(View.GONE);
        }
    }

    private class SetActiveDeviceCallback implements Callback<SparkDevice> {
        private final String deviceId;

        public SetActiveDeviceCallback(String deviceId) {

            this.deviceId = deviceId;
        }

        @Override
        public void success(SparkDevice sparkDevice, Response response) {
            selectedSparkDevice = sparkDevice;
            messageTextView.setText(String.format("Connected to %s", getSparkDeviceName()));
            sparkService.getVariable("state", deviceId, new DetermineSparkStatusCallback());
            flashSparkButton.setText(String.format("Flash %s", getSparkDeviceName()));
        }

        @Override
        public void failure(RetrofitError retrofitError) {

        }
    }
}
