package de.danoeh.antennapod.playback.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

public class EpisodeSummaryPlaybackTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void includesQueuedSummaryEnabledByPodcast() throws Exception {
        FeedMedia media = createMedia();
        media.getItem().addTag(FeedItem.TAG_QUEUE);
        media.getItem().getFeed().getPreferences()
                .setEpisodeSummary(FeedPreferences.EpisodeSummarySetting.ENABLED);

        assertTrue(EpisodeSummaryPlayback.shouldInclude(media, createSummary(), false));
    }

    @Test
    public void excludesSummaryDisabledByPodcast() throws Exception {
        FeedMedia media = createMedia();
        media.getItem().addTag(FeedItem.TAG_QUEUE);
        media.getItem().getFeed().getPreferences()
                .setEpisodeSummary(FeedPreferences.EpisodeSummarySetting.DISABLED);

        assertFalse(EpisodeSummaryPlayback.shouldInclude(media, createSummary(), true));
    }

    @Test
    public void excludesSummaryOutsideQueue() throws Exception {
        FeedMedia media = createMedia();

        assertFalse(EpisodeSummaryPlayback.shouldInclude(media, createSummary(), true));
    }

    @Test
    public void includesQueuedSummaryEnabledByGlobalDefault() throws Exception {
        FeedMedia media = createMedia();
        media.getItem().addTag(FeedItem.TAG_QUEUE);

        assertTrue(EpisodeSummaryPlayback.shouldInclude(media, createSummary(), true));
    }

    @Test
    public void excludesSummaryWithoutAudioFile() {
        FeedMedia media = createMedia();
        media.getItem().addTag(FeedItem.TAG_QUEUE);
        EpisodeSummary summary = new EpisodeSummary(42, "Summary", "missing", Collections.emptyList());

        assertFalse(EpisodeSummaryPlayback.shouldInclude(media, summary, true));
    }

    private FeedMedia createMedia() {
        Feed feed = new Feed("feed", null, "Podcast", null, null);
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        FeedMedia media = new FeedMedia(item, "media", 180_000, "audio/mpeg");
        media.setId(42);
        item.setMedia(media);
        return media;
    }

    private EpisodeSummary createSummary() throws Exception {
        File audio = temporaryFolder.newFile();
        return new EpisodeSummary(42, "Summary", audio.getAbsolutePath(), Collections.emptyList());
    }
}
