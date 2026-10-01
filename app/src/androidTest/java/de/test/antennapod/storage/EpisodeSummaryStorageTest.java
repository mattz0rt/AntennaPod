package de.test.antennapod.storage;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Runs the summary storage round-trip against real on-device SQLite. */
@RunWith(AndroidJUnit4.class)
public class EpisodeSummaryStorageTest {
    private PodDBAdapter adapter;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PodDBAdapter.init(context);
        adapter = PodDBAdapter.getInstance();
        adapter.open();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    private EpisodeSummary buildSummary(long itemId) {
        EpisodeSummary summary = new EpisodeSummary(itemId);
        summary.setText("Narrated episode recap.");
        summary.setAudioPath("/data/episode-summaries/" + itemId + ".wav");
        summary.setDurationMs(58_500);
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setCreatedAt(987654321L);
        summary.setTopics(Arrays.asList(
                new EpisodeSummary.Topic("Cold open", 0),
                new EpisodeSummary.Topic("Interview", 120_000),
                new EpisodeSummary.Topic("Wrap up", 600_000)));
        return summary;
    }

    @Test
    public void roundTripPersistsAllFields() {
        adapter.setEpisodeSummary(buildSummary(101));

        EpisodeSummary loaded = adapter.getEpisodeSummary(101);
        assertNotNull(loaded);
        assertEquals("Narrated episode recap.", loaded.getText());
        assertEquals("/data/episode-summaries/101.wav", loaded.getAudioPath());
        assertEquals(58_500, loaded.getDurationMs());
        assertEquals(EpisodeSummary.STATUS_DONE, loaded.getStatus());
        assertEquals(3, loaded.getTopics().size());
        assertEquals("Cold open", loaded.getTopics().get(0).getTitle());
        assertEquals(120_000, loaded.getTopics().get(1).getStartMs());
        assertEquals("Wrap up", loaded.getTopics().get(2).getTitle());
    }

    @Test
    public void missingSummaryReturnsNull() {
        assertNull(adapter.getEpisodeSummary(123456));
    }

    @Test
    public void replaceDropsOldTopics() {
        adapter.setEpisodeSummary(buildSummary(102));
        EpisodeSummary replacement = buildSummary(102);
        replacement.setText("Revised.");
        replacement.setTopics(Collections.singletonList(new EpisodeSummary.Topic("Only", 5_000)));
        adapter.setEpisodeSummary(replacement);

        EpisodeSummary loaded = adapter.getEpisodeSummary(102);
        assertNotNull(loaded);
        assertEquals("Revised.", loaded.getText());
        assertEquals(1, loaded.getTopics().size());
    }

    @Test
    public void deleteRemovesSummaryAndTopics() {
        adapter.setEpisodeSummary(buildSummary(103));
        adapter.deleteEpisodeSummary(103);
        assertNull(adapter.getEpisodeSummary(103));
    }
}
