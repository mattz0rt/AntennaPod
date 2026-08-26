package de.danoeh.antennapod.ui.preferences.screen;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.R;

import java.util.Calendar;

public class AutoDownloadPreferencesFragment extends AnimatedPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_autodownload);
        findPreference(UserPreferences.PREF_AUTODL_TIME).setOnPreferenceClickListener(preference -> {
            int minutes = UserPreferences.getAutodownloadTimeMinutes();
            new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
                UserPreferences.setAutodownloadTimeMinutes(hourOfDay * 60 + minute);
                updateTimeSummary();
                AutoDownloadManager.getInstance().restartSchedule(getContext(), true);
            }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(getContext())).show();
            return true;
        });
        updateTimeSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.pref_automatic_download_title);
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (UserPreferences.PREF_AUTODL_SCHEDULED.equals(key)
                || UserPreferences.PREF_ENABLE_AUTODL_ON_BATTERY.equals(key)) {
            AutoDownloadManager.getInstance().restartSchedule(getContext(), true);
        }
    }

    private void updateTimeSummary() {
        int minutes = UserPreferences.getAutodownloadTimeMinutes();
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, minutes / 60);
        time.set(Calendar.MINUTE, minutes % 60);
        Preference preference = findPreference(UserPreferences.PREF_AUTODL_TIME);
        preference.setSummary(DateFormat.getTimeFormat(getContext()).format(time.getTime()));
    }
}
