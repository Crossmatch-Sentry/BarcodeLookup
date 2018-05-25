package com.crossmatch.barcodelookup;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.widget.Toast;

/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 */
public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState,
                                    String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

//        EditTextPreference pref;
//        pref = (EditTextPreference) findPreference("ssid_preference");
//        pref.setSummary(pref.getText());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());


        onSharedPreferenceChanged(sharedPreferences, SettingsActivity.KEY_PREF_URL);

    }

    @Override
    public void onResume() {
        super.onResume();
        //unregister the preferenceChange listener
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        String msg = "Changing key: " + key;
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        if (key.equals(SettingsActivity.KEY_PREF_URL)) {

            EditTextPreference pref;
            pref = (EditTextPreference) findPreference(SettingsActivity.KEY_PREF_URL);
            pref.setSummary(pref.getText());

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //unregister the preference change listener
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
