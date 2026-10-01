package de.test.antennapod.ui;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.intent.rule.IntentsTestRule;

import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNull;

/**
 * End-to-end wiring check: a cold app start must create the database including the
 * episode summary tables (fresh-install schema path) and reach the main screen.
 * Uses IntentsTestRule like the rest of the suite; ActivityScenario deadlocks against
 * Firebase Test Lab's companion overlay.
 */
@RunWith(AndroidJUnit4.class)
public class AppLaunchWithSchemaTest {

    @Rule
    public IntentsTestRule<MainActivity> activityRule =
            new IntentsTestRule<>(MainActivity.class, false, true);

    @Test
    public void mainActivityLaunchesAndSummaryTablesExist() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PodDBAdapter.init(context);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        try {
            assertNull(adapter.getEpisodeSummary(987654321L));
        } finally {
            adapter.close();
        }
    }
}
