package de.danoeh.antennapod.playback.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.test.core.app.ApplicationProvider;
import de.danoeh.antennapod.model.feed.EpisodeTopic;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public class MediaItemAdapterTest {
    @Test
    public void topicItemContainsRangeAndEpisodeMetadata() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FeedMedia media = createMedia();
        EpisodeTopic topic = new EpisodeTopic("Interview", 65_000, 125_000);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        MediaItem item;
        try {
            item = executor.submit(() -> MediaItemAdapter.fromEpisodeTopic(context, media, topic)).get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals("topic:42:65000:125000", item.mediaId);
        assertEquals(65_000, item.clippingConfiguration.startPositionMs);
        assertEquals(125_000, item.clippingConfiguration.endPositionMs);
        assertEquals("Interview", item.mediaMetadata.title);
        assertEquals("1:05 · Episode", item.mediaMetadata.subtitle);
        assertTrue(item.mediaMetadata.isPlayable);
        assertFalse(item.mediaMetadata.isBrowsable);
        assertEquals(MediaItemAdapter.COMMAND_PLAY_BROWSE_ITEM, item.mediaMetadata.supportedCommands.get(0));
    }

    @Test
    public void topicPageIsBrowsableAndNextItemIsPlayable() {
        Context context = ApplicationProvider.getApplicationContext();
        FeedMedia media = createMedia();

        MediaItem page = MediaItemAdapter.fromTopicPage(media);
        MediaItem next = MediaItemAdapter.fromNextEpisode(context, media);

        assertEquals("topics:42", page.mediaId);
        assertTrue(page.mediaMetadata.isBrowsable);
        assertFalse(page.mediaMetadata.isPlayable);
        assertEquals(MediaItemAdapter.COMMAND_PLAY_BROWSE_ITEM, page.mediaMetadata.supportedCommands.get(0));
        assertEquals("next:42", next.mediaId);
        assertTrue(next.mediaMetadata.isPlayable);
        assertFalse(next.mediaMetadata.isBrowsable);
        assertEquals(MediaItemAdapter.COMMAND_PLAY_BROWSE_ITEM, next.mediaMetadata.supportedCommands.get(0));
    }

    private FeedMedia createMedia() {
        Feed feed = new Feed("feed", null, "Podcast");
        FeedItem item = new FeedItem();
        item.setFeed(feed);
        item.setTitle("Episode");
        FeedMedia media = new FeedMedia(item, "media", 180_000, "audio/mpeg");
        media.setId(42);
        item.setMedia(media);
        return media;
    }
}
