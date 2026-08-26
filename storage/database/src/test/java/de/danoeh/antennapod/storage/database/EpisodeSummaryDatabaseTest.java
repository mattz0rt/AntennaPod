package de.danoeh.antennapod.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.EpisodeTopic;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class EpisodeSummaryDatabaseTest {
    private PodDBAdapter adapter;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        adapter = PodDBAdapter.getInstance();
        adapter.open();
    }

    @After
    public void tearDown() {
        adapter.close();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void storeLoadAndRemoveSummary() {
        EpisodeSummary summary = new EpisodeSummary(42, "Summary", "/tmp/42.wav", Arrays.asList(
                new EpisodeTopic("Introduction", 0, 10_000),
                new EpisodeTopic("Conclusion", 10_000, 20_000)));

        adapter.setEpisodeSummary(summary);
        EpisodeSummary stored = adapter.getEpisodeSummary(42);

        assertEquals(42, stored.getMediaId());
        assertEquals("Summary", stored.getText());
        assertEquals("/tmp/42.wav", stored.getAudioFileUrl());
        assertEquals(summary.getTopics(), stored.getTopics());

        adapter.removeEpisodeSummary(42);
        assertNull(adapter.getEpisodeSummary(42));
    }

    @Test
    public void writerOnlyStoresSummaryWhileEpisodeIsDownloaded() throws Exception {
        Feed feed = new Feed("feed", null, "Feed", null, null);
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        item.setTitle("Episode");
        item.setItemIdentifier("episode");
        FeedMedia media = new FeedMedia(item, "media", 60_000, "audio/mpeg");
        item.setMedia(media);
        feed.setItems(Collections.singletonList(item));
        adapter.setCompleteFeed(feed);

        EpisodeSummary summary = new EpisodeSummary(media.getId(), "Summary", "/tmp/summary.wav",
                Collections.singletonList(new EpisodeTopic("Topic", 0, 10_000)));
        assertFalse(DBWriter.setEpisodeSummary(summary).get());
        assertNull(adapter.getEpisodeSummary(media.getId()));

        media.setLocalFileUrl("/tmp/episode.mp3");
        media.setDownloaded(true, 1);
        adapter.setMediaDownloadInformation(media);
        assertTrue(DBWriter.setEpisodeSummary(summary).get());
        assertEquals(summary.getTopics(), adapter.getEpisodeSummary(media.getId()).getTopics());

        adapter.removeFeedItems(Arrays.asList(item));
        assertFalse(DBWriter.setEpisodeSummary(summary).get());
        assertNull(adapter.getEpisodeSummary(media.getId()));
    }

    @Test
    public void storeAndLoadFeedSummaryOverride() {
        Feed feed = new Feed("feed", null, "Feed", null, null);
        adapter.setCompleteFeed(feed);

        feed.getPreferences().setEpisodeSummary(FeedPreferences.EpisodeSummarySetting.ENABLED);
        adapter.setFeedPreferences(feed.getPreferences());

        Feed stored = DBReader.getFeed(feed.getId(), false, 0, 0);
        assertEquals(FeedPreferences.EpisodeSummarySetting.ENABLED,
                stored.getPreferences().getEpisodeSummary());
    }
}
