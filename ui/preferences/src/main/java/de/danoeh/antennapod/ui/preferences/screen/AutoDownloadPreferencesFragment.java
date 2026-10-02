package de.danoeh.antennapod.ui.preferences.screen;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.R;
import java.util.Calendar;
import java.util.Locale;

public class AutoDownloadPreferencesFragment extends AnimatedPreferenceFragment {
    private static final String KEY_TIME_FROM = "prefAutodownloadTimeFrom";
    private static final String KEY_TIME_TO = "prefAutodownloadTimeTo";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_autodownload);
        bindTimePreference(KEY_TIME_FROM, true);
        bindTimePreference(KEY_TIME_TO, false);
    }

    private void bindTimePreference(String key, boolean isFrom) {
        Preference preference = findPreference(key);
        updateTimeSummary(preference, isFrom);
        preference.setOnPreferenceClickListener(clicked -> {
            int minutes = isFrom ? UserPreferences.getAutodownloadTimeFrom() : UserPreferences.getAutodownloadTimeTo();
            TimePickerDialog dialog = new TimePickerDialog(getContext(),
                    (view, hourOfDay, minute) -> {
                        int selected = hourOfDay * 60 + minute;
                        UserPreferences.setAutodownloadTime(UserPreferences.isAutodownloadTimeRestricted(),
                                isFrom ? selected : UserPreferences.getAutodownloadTimeFrom(),
                                isFrom ? UserPreferences.getAutodownloadTimeTo() : selected);
                        updateTimeSummary(clicked, isFrom);
                    }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(getContext()));
            dialog.show();
            return true;
        });
    }

    private void updateTimeSummary(Preference preference, boolean isFrom) {
        int minutes = isFrom ? UserPreferences.getAutodownloadTimeFrom() : UserPreferences.getAutodownloadTimeTo();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        preference.setSummary(DateFormat.getTimeFormat(getContext()).format(calendar.getTime()));
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.pref_automatic_download_title);
    }
}
