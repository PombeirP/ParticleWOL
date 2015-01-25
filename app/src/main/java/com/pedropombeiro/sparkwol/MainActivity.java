package com.pedropombeiro.sparkwol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RetrofitError;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.client.UrlConnectionClient;
import retrofit.mime.TypedFile;


public class MainActivity extends ActionBarActivity {
    SparkService sparkService;

    Button wakeComputerButton;
    Button flashSparkButton;
    TextView messageTextView;
    ProgressBar progress;
    SparkDevice selectedSparkDevice;
    boolean isRefreshActionVisible = true;
    boolean isInForeground;

    enum State {
        Invalid,
        SparkNotConfigured,
        TargetHostNotConfigured,
        NoConnectionToSpark,
        TestingConnectionToSpark,
        SparkNotFlashed,
        ConnectedToSpark,
        FlashingSpark,
        SendingWakeOnLan,
    }

    State currentState = State.Invalid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.sparkService = SparkServiceProvider.createSparkService(new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");

                if (authenticationToken != "")
                    request.addHeader("Authorization", String.format("Bearer %s", authenticationToken));
            }
        });

        this.wakeComputerButton = (Button) findViewById(R.id.wakeComputerButton);
        this.flashSparkButton = (Button) findViewById(R.id.flashSparkButton);
        this.messageTextView = (TextView) findViewById(R.id.messageTextView);
        this.progress = (ProgressBar) findViewById(R.id.progress);

        this.testConnectionToSparkDevice();
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.isInForeground = true;

        this.testConnectionToSparkDevice();
    }

    @Override
    protected void onPause() {
        super.onPause();

        this.isInForeground = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.isRefreshActionVisible = this.isRefreshAvailable();
        menu.findItem(R.id.action_refresh).setVisible(this.isRefreshActionVisible);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_settings:
                Intent myIntent = new Intent(this, SettingsActivity.class);
                //myIntent.putExtra("key", value); //Optional parameters
                this.startActivity(myIntent);
                return true;
            case R.id.action_refresh:
                this.testConnectionToSparkDevice();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void displayToast(String toastText) {
        this.displayToast(toastText, true);
    }

    private void displayToast(String toastText, boolean onlyWhenInactive) {
        if(!onlyWhenInactive || !this.isInForeground) {
            Toast.makeText(this.getBaseContext(), toastText, Toast.LENGTH_LONG).show();
        }
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
            this.displayToast("Target Spark device not defined", false);
            return;
        }
        if (authenticationToken.length() == 0) {
            this.displayToast("Authentication token not defined", false);
            return;
        }
        if (ipAddress.length() == 0) {
            this.displayToast("Target computer IP address not defined", false);
            return;
        }
        if (macAddress == null || macAddress.length() == 0) {
            this.displayToast("Could not retrieve target computer MAC address", false);
            return;
        }

        this.sparkService.invokeFunction(deviceId, "wakeHost", String.format("%s;%s", ipAddress, macAddress), new WakeUpHostCallback(deviceId));
    }

    public void onFlashSparkButtonClick(View view) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
        String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");

        if (deviceId.length() == 0) {
            this.displayToast("Spark device not defined", false);
            return;
        }
        if (authenticationToken.length() == 0) {
            this.displayToast("Authentication token not defined", false);
            return;
        }

        try {
            File firmwareFile = getBaseContext().getCacheDir().createTempFile("spark", "firmware");
            Integer[] resourceIds = {R.raw.firmware};
            createFile(firmwareFile, getBaseContext(), resourceIds);
            firmwareFile.deleteOnExit();

            this.setCurrentState(State.FlashingSpark, String.format("Trying to connect to %s...", getSparkDeviceName()));

            this.sparkService.flashFirmware(new TypedFile("application/octet-stream", firmwareFile), deviceId, new Callback<UploadSparkFirmwareResponse>() {
                @Override
                public void success(UploadSparkFirmwareResponse sparkResponse, Response response) {
                    messageTextView.setText(sparkResponse.status);
                    testConnectionToSparkDevice();
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    displayToast(String.format("Could not flash %s", getSparkDeviceName()));
                    setCurrentState(State.ConnectedToSpark, String.format("Could not flash the %s: %s", getSparkDeviceName(), retrofitError.getResponse().getReason()));
                    Log.w("FromOnPostExecute", retrofitError.getMessage());
                }
            });
        }
        catch (Exception e) {
            Log.e("onFlashSparkButtonClick", e.getMessage());
        }
    }

    public final class UrlConnectionClientWithShorterTimeout extends UrlConnectionClient {
        @Override protected java.net.HttpURLConnection openConnection(Request request) throws IOException {
            java.net.HttpURLConnection connection = super.openConnection(request);
            connection.setConnectTimeout(5 * 1000);
            connection.setReadTimeout(10 * 1000);
            return connection;
        }
    }

    String getSparkDeviceName() {
        if (this.selectedSparkDevice != null)
            return this.selectedSparkDevice.name;

        return "Spark";
    }

    private void retrieveSparkDevice(String deviceId, String authenticationToken) {
        if (authenticationToken != "" && deviceId != "") {
            this.setCurrentState(State.TestingConnectionToSpark, String.format("Trying to connect to %s...", getSparkDeviceName()));

            sparkService.getDevice(deviceId, new SetActiveDeviceCallback(deviceId));
        }
        else {
            this.setCurrentState(State.SparkNotConfigured);
        }
    }

    private void testConnectionToSparkDevice() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = sharedPreferences.getString(PreferenceKeys.DEVICE_ID, "");
        String authenticationToken = sharedPreferences.getString(PreferenceKeys.AUTHENTICATION_TOKEN, "");
        this.retrieveSparkDevice(deviceId, authenticationToken);
    }

    private void setCurrentState(State newState) {
        String message = null;

        switch (newState) {
            case SparkNotConfigured:
                message = "Please configure your Spark device in Settings";
                break;
            case TargetHostNotConfigured:
                message = "Please configure the computer to wake up in Settings";
                break;
        }

        this.setCurrentState(newState, message);
    }

    private void setCurrentState(State newState, String message) {
        this.currentState = newState;
        this.messageTextView.setText(message);
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.isRefreshAvailable() != this.isRefreshActionVisible) {
            this.isRefreshActionVisible = this.isRefreshAvailable();
            invalidateOptionsMenu();
        }
        this.flashSparkButton.setVisibility(this.currentState.equals(State.SparkNotFlashed) ? View.VISIBLE : View.GONE);
        this.wakeComputerButton.setVisibility(this.currentState.equals(State.ConnectedToSpark) ? View.VISIBLE : View.GONE);

        this.progress.setVisibility(this.isProgressVisible() ? View.VISIBLE : View.GONE);
    }

    private boolean isProgressVisible() {
        return this.currentState.equals(State.TestingConnectionToSpark) || this.currentState.equals(State.FlashingSpark) || this.currentState.equals(State.SendingWakeOnLan);
    }

    private boolean isRefreshAvailable() {
        return this.currentState.equals(State.NoConnectionToSpark) || this.currentState.equals(State.ConnectedToSpark) || this.currentState.equals(State.SparkNotFlashed);
    }

    public static void createFile(final File outputFile,
                                  final Context context, final Integer[] inputRawResources)
            throws IOException {

        final OutputStream outputStream = new FileOutputStream(outputFile);

        final Resources resources = context.getResources();
        final byte[] largeBuffer = new byte[1024 * 4];
        int totalBytes = 0;
        int bytesRead = 0;

        for (Integer resource : inputRawResources) {
            final InputStream inputStream = resources.openRawResource(resource
                    .intValue());
            while ((bytesRead = inputStream.read(largeBuffer)) > 0) {
                if (largeBuffer.length == bytesRead) {
                    outputStream.write(largeBuffer);
                } else {
                    final byte[] shortBuffer = new byte[bytesRead];
                    System.arraycopy(largeBuffer, 0, shortBuffer, 0, bytesRead);
                    outputStream.write(shortBuffer);
                }
                totalBytes += bytesRead;
            }
            inputStream.close();
        }

        outputStream.flush();
        outputStream.close();
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
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String ipAddress = sharedPreferences.getString(PreferenceKeys.IP_ADDRESS, "");
            String macAddress = sharedPreferences.getString(PreferenceKeys.MAC_ADDRESS, "");
            if (ipAddress == "" || macAddress == "")
                setCurrentState(State.TargetHostNotConfigured);
            else
                setCurrentState(State.ConnectedToSpark, String.format("%s is online", getSparkDeviceName()));
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            Response response = retrofitError.getResponse();
            if (response != null && response.getStatus() == 404) {
                setCurrentState(State.SparkNotFlashed, String.format("%s needs to be flashed with the WOL firmware", getSparkDeviceName()));
                return;
            }

            displayRestFailure(retrofitError, State.NoConnectionToSpark);
        }
    }

    private void displayRestFailure(RetrofitError retrofitError, State newState) {
        Response response = retrofitError.getResponse();
        Log.w("FromOnPostExecute", retrofitError.getMessage());

        displayToast(String.format("Could not communicate with %s", getSparkDeviceName()));
        if (response != null)
            setCurrentState(newState, String.format("Could not communicate with %s: %s", getSparkDeviceName(), response.getReason()));
        else
            setCurrentState(newState, String.format("Could not communicate with %s", getSparkDeviceName()));
    }

    private class WakeUpHostCallback implements Callback<Response> {
        private final String deviceId;

        public WakeUpHostCallback(String deviceId) {
            this.deviceId = deviceId;

            setCurrentState(State.SendingWakeOnLan, "Waking up computer...");
        }

        @Override
        public void success(Response sparkVariable, Response response) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    sparkService.getVariable("state", deviceId, new WaitForHostToWakeUpCallback(deviceId));
                }
            };

            Handler handler = new Handler();
            handler.postDelayed(runnable, 2000);
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            displayToast(String.format("Could not retrieve status from %s", getSparkDeviceName()));
            Log.w("FromOnPostExecute", retrofitError.getMessage());

            setCurrentState(State.ConnectedToSpark, "");
        }
    }

    private class WaitForHostToWakeUpCallback implements Callback<SparkVariable> {
        private final String deviceId;

        public WaitForHostToWakeUpCallback(String deviceId) {
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
                            sparkService.getVariable("state", deviceId, new WaitForHostToWakeUpCallback(deviceId));
                        }
                    };

                    Handler handler = new Handler();
                    handler.postDelayed(runnable, 2000);
                    break;
                }
                case "Unreachable":
                    displayToast("Target computer is unreachable");
                    setCurrentState(State.ConnectedToSpark, String.format("%s could not contact the target computer", getSparkDeviceName()));
                    break;
                case "Reachable":
                    displayToast("Target computer is awake!");
                    setCurrentState(State.ConnectedToSpark, "Target computer is awake!");
                    break;
            }
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            displayRestFailure(retrofitError, State.ConnectedToSpark);
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
            messageTextView.setText(String.format("Connecting to %s", getSparkDeviceName()));
            flashSparkButton.setText(String.format("Flash %s", getSparkDeviceName()));
            sparkService.getVariable("state", deviceId, new DetermineSparkStatusCallback());
        }

        @Override
        public void failure(RetrofitError retrofitError) {
            displayRestFailure(retrofitError, State.NoConnectionToSpark);
        }
    }
}
