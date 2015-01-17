package com.pedropombeiro.sparkwol;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity {
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;
    private SparkService sparkService;
    private AuthorizationTokenRequestInterceptor requestInterceptor;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        createSparkService();

        setupSimplePreferencesScreen();
    }

    private void createSparkService() {
        this.requestInterceptor = new AuthorizationTokenRequestInterceptor();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint("https://api.spark.io")
                .setRequestInterceptor(this.requestInterceptor)
                .build();
        this.sparkService = restAdapter.create(SparkService.class);
    }

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(PreferenceKeys.DEVICE_ID));
            bindPreferenceSummaryToValue(findPreference(PreferenceKeys.AUTHENTICATION_TOKEN));
            bindPreferenceSummaryToValue(findPreference(PreferenceKeys.IP_ADDRESS));
            bindPreferenceSummaryToValue(findPreference(PreferenceKeys.MAC_ADDRESS));
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);
        IPAddressPreference ipAddressPreference = new IPAddressPreference(this);
        ipAddressPreference.setKey(PreferenceKeys.IP_ADDRESS);
        ipAddressPreference.setTitle(R.string.pref_ip_address);
        getPreferenceScreen().addPreference(ipAddressPreference);
        MacAddressPreference macAddressPreference = new MacAddressPreference(this);
        macAddressPreference.setKey(PreferenceKeys.MAC_ADDRESS);
        macAddressPreference.setTitle(R.string.pref_mac_address);
        getPreferenceScreen().addPreference(macAddressPreference);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference(PreferenceKeys.AUTHENTICATION_TOKEN));
        bindPreferenceSummaryToValue(findPreference(PreferenceKeys.IP_ADDRESS));
        bindPreferenceSummaryToValue(findPreference(PreferenceKeys.MAC_ADDRESS));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof IPAddressPreference) {
                if (stringValue.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                    String macAddress = NetworkHelpers.GetMacFromArpCache(stringValue);

                    preference.setSummary(stringValue);

                    if (macAddress != null) {
                        macAddress = macAddress.toUpperCase();

                        if (!macAddress.equals("00:00:00:00:00:00")) {
                            MacAddressPreference macAddressPreference = (MacAddressPreference) preference.getPreferenceManager().findPreference(PreferenceKeys.MAC_ADDRESS);
                            macAddressPreference.setText(macAddress);
                            bindPreferenceSummaryToValue(macAddressPreference);
                        }
                    }
                }
                else {
                    preference.setSummary(String.format("%s (Unknown/invalid)", stringValue));
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }

            ListPreference deviceIdPreference = null;
            CharSequence[] deviceIdEntries = null;
            String authToken = "";
            String preferenceKey = preference.getKey();
            if (preferenceKey.equals(PreferenceKeys.AUTHENTICATION_TOKEN)) {
                authToken = stringValue;
                deviceIdPreference = (ListPreference) preference.getPreferenceManager().findPreference(PreferenceKeys.DEVICE_ID);
                deviceIdEntries = deviceIdPreference.getEntries();
            }
            else if (preferenceKey.equals(PreferenceKeys.DEVICE_ID)) {
                EditTextPreference authTokenPreference = (EditTextPreference) preference.getPreferenceManager().findPreference(PreferenceKeys.AUTHENTICATION_TOKEN);

                deviceIdPreference = (ListPreference) preference;
                deviceIdEntries = deviceIdPreference.getEntries();
                authToken = authTokenPreference.getText();
            }

            if (deviceIdPreference != null) {
                // update device list preference enabled state
                deviceIdPreference.setEnabled(authToken.length() > 0 && deviceIdEntries != null && deviceIdEntries.length > 0);

                // update device list preference summary
                if (authToken.length() > 0) {
                    if (deviceIdPreference != null && deviceIdEntries != null) {
                        if (deviceIdEntries.length > 0) {
                            if (deviceIdPreference.getEntry() == null) {
                                deviceIdPreference.setSummary(R.string.pref_device_select_device);
                            }
                        } else {
                            deviceIdPreference.setSummary(R.string.pref_device_no_devices_found);
                        }
                    }
                } else {
                    deviceIdPreference.setSummary("");
                }

                // If authentication token changes, request list of devices again
                if (preferenceKey.equals(PreferenceKeys.AUTHENTICATION_TOKEN) && authToken.length() > 0) {
                    requestInterceptor.setAuthorizationToken(authToken);
                    sparkService.getDevices(new Callback<List<SparkDevice>>() {
                        @Override
                        public void success(List<SparkDevice> sparkDevices, Response response) {
                            ListPreference deviceIdPreference = (ListPreference) findPreference(PreferenceKeys.DEVICE_ID);
                            ArrayList<String> entries = new ArrayList<String>(sparkDevices.size());
                            ArrayList<String> entryValues = new ArrayList<String>(sparkDevices.size());

                            for (int i = 0; i < sparkDevices.size(); ++i) {
                                entries.add(sparkDevices.get(i).name);
                                entryValues.add(sparkDevices.get(i).id);
                            }
                            deviceIdPreference.setEntries(entries.toArray(new String[entries.size()]));
                            deviceIdPreference.setEntryValues(entryValues.toArray(new String[entryValues.size()]));

                            bindPreferenceSummaryToValue(deviceIdPreference);
                        }

                        @Override
                        public void failure(RetrofitError retrofitError) {
                            Toast.makeText(getBaseContext(), "Error requesting Spark device list", Toast.LENGTH_LONG).show();
                            Log.w("FromOnPostExecute", retrofitError.getMessage());
                        }
                    });
                }
            }

            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
        }
    }

    private class AuthorizationTokenRequestInterceptor implements RequestInterceptor {
        private String authorizationToken = "";

        public void setAuthorizationToken(String authorizationToken) {
            this.authorizationToken = authorizationToken;
        }

        @Override
        public void intercept(RequestFacade request) {
            if (this.authorizationToken != "")
                request.addHeader("Authorization", String.format("Bearer %s", this.authorizationToken));
        }
    }
}
