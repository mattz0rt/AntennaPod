package de.danoeh.antennapod.net.download.service.episode.autodownload;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class AutoDownloadManagerImplTest {
    private TimeZone originalTimeZone;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void initialDelayTargetsNextSelectedTime() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.JULY, 14, 1, 30, 0);
        now.set(Calendar.MILLISECOND, 0);

        assertEquals(TimeUnit.MINUTES.toMillis(30),
                AutoDownloadManagerImpl.calculateInitialDelay(now.getTimeInMillis(), 120));
        assertEquals(TimeUnit.HOURS.toMillis(23) + TimeUnit.MINUTES.toMillis(30),
                AutoDownloadManagerImpl.calculateInitialDelay(now.getTimeInMillis(), 60));
    }
}
