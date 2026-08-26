package de.danoeh.antennapod.ui.screen.preferences;

import android.os.Bundle;
import android.text.InputType;
import androidx.preference.EditTextPreference;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;

public class EpisodeSummaryPreferencesFragment extends AnimatedPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_episode_summaries);
        EditTextPreference length = findPreference(UserPreferences.PREF_EPISODE_SUMMARY_LENGTH);
        length.setOnBindEditTextListener(editText ->
                editText.setInputType(InputType.TYPE_CLASS_NUMBER));
        configureSecret(UserPreferences.PREF_GROQ_API_KEY);
        configureSecret(UserPreferences.PREF_GEMINI_API_KEY);
    }

    private void configureSecret(String key) {
        EditTextPreference preference = findPreference(key);
        preference.setOnBindEditTextListener(editText -> editText.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }
}
