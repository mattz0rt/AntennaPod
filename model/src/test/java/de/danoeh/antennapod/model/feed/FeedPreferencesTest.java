package de.danoeh.antennapod.model.feed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FeedPreferencesTest {
    @Test
    public void episodeSummarySettingOverridesGlobalDefault() {
        FeedPreferences preferences = new Feed("feed", null, "Feed", null, null).getPreferences();

        assertTrue(preferences.isEpisodeSummaryEnabled(true));
        assertFalse(preferences.isEpisodeSummaryEnabled(false));

        preferences.setEpisodeSummary(FeedPreferences.EpisodeSummarySetting.ENABLED);
        assertTrue(preferences.isEpisodeSummaryEnabled(false));

        preferences.setEpisodeSummary(FeedPreferences.EpisodeSummarySetting.DISABLED);
        assertFalse(preferences.isEpisodeSummaryEnabled(true));
    }
}
