package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AutodownloadWindowTest {
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        UserPreferences.setAutodownloadTime(false, 0, 0);
    }

    private static Calendar at(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return calendar;
    }

    @Test
    public void unrestrictedWindowAlwaysMatches() {
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(3, 15)));
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(14, 45)));
    }

    @Test
    public void sameDayWindowMatchesBounds() {
        UserPreferences.setAutodownloadTime(true, 120, 300);
        assertFalse(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(1, 59)));
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(2, 0)));
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(4, 59)));
        assertFalse(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(5, 0)));
    }

    @Test
    public void overnightWindowWrapsMidnight() {
        UserPreferences.setAutodownloadTime(true, 1320, 360);
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(23, 30)));
        assertTrue(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(0, 30)));
        assertFalse(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(12, 0)));
    }

    @Test
    public void zeroLengthWindowNeverMatches() {
        UserPreferences.setAutodownloadTime(true, 120, 120);
        assertFalse(AutomaticDownloadAlgorithm.isInAutodownloadWindow(at(2, 0)));
    }
}
