package de.danoeh.antennapod.storage.database;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.FeedItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class EpisodeSummaryStorageTest {
    private PodDBAdapter adapter;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
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
        summary.setText("A short narration of the episode.");
        summary.setAudioPath("/data/summaries/" + itemId + ".wav");
        summary.setDurationMs(61_000);
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setCreatedAt(1234L);
        summary.setTopics(Arrays.asList(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Deep dive", 90_000),
                new EpisodeSummary.Topic("Outro", 300_000)));
        return summary;
    }

    @Test
    public void roundTripPersistsAllFields() {
        adapter.setEpisodeSummary(buildSummary(11));

        EpisodeSummary loaded = adapter.getEpisodeSummary(11);
        assertNotNull(loaded);
        assertEquals("A short narration of the episode.", loaded.getText());
        assertEquals("/data/summaries/11.wav", loaded.getAudioPath());
        assertEquals(61_000, loaded.getDurationMs());
        assertEquals(EpisodeSummary.STATUS_DONE, loaded.getStatus());
        assertEquals(1234L, loaded.getCreatedAt());
        assertEquals(3, loaded.getTopics().size());
        assertEquals("Intro", loaded.getTopics().get(0).getTitle());
        assertEquals(0, loaded.getTopics().get(0).getStartMs());
        assertEquals("Deep dive", loaded.getTopics().get(1).getTitle());
        assertEquals(90_000, loaded.getTopics().get(1).getStartMs());
        assertEquals("Outro", loaded.getTopics().get(2).getTitle());
        assertEquals(300_000, loaded.getTopics().get(2).getStartMs());
    }

    @Test
    public void missingSummaryReturnsNull() {
        assertNull(adapter.getEpisodeSummary(404));
    }

    @Test
    public void replacingSummaryRemovesOldTopics() {
        adapter.setEpisodeSummary(buildSummary(12));
        EpisodeSummary replacement = buildSummary(12);
        replacement.setText("Updated text.");
        replacement.setTopics(Collections.singletonList(new EpisodeSummary.Topic("Only topic", 1000)));
        adapter.setEpisodeSummary(replacement);

        EpisodeSummary loaded = adapter.getEpisodeSummary(12);
        assertNotNull(loaded);
        assertEquals("Updated text.", loaded.getText());
        assertEquals(1, loaded.getTopics().size());
        assertEquals("Only topic", loaded.getTopics().get(0).getTitle());
    }

    @Test
    public void deleteRemovesSummaryAndTopics() {
        adapter.setEpisodeSummary(buildSummary(13));
        adapter.deleteEpisodeSummary(13);
        assertNull(adapter.getEpisodeSummary(13));
    }

    @Test
    public void deletingEpisodeRemovesSummaryAudioFile() throws Exception {
        File audioFile = File.createTempFile("episode-summary", ".wav");
        try {
            EpisodeSummary summary = buildSummary(14);
            summary.setAudioPath(audioFile.getAbsolutePath());
            adapter.setEpisodeSummary(summary);
            adapter.close();

            FeedItem item = new FeedItem();
            item.setId(14);
            Context context = InstrumentationRegistry.getInstrumentation().getContext();
            DBWriter.deleteFeedItems(context, Collections.singletonList(item)).get();

            assertFalse(audioFile.exists());
            assertNull(DBReader.getEpisodeSummary(14));
        } finally {
            audioFile.delete();
        }
    }

    @Test
    public void deleteBatchRemovesOnlyRequestedItems() {
        adapter.setEpisodeSummary(buildSummary(21));
        adapter.setEpisodeSummary(buildSummary(22));
        List<Long> ids = Collections.singletonList(21L);
        adapter.deleteEpisodeSummaries(ids);

        assertNull(adapter.getEpisodeSummary(21));
        assertNotNull(adapter.getEpisodeSummary(22));
    }
}
