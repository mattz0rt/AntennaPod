package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.os.Bundle;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaSession;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class SummaryPlaybackEndToEndTest {
    private Context context;
    private MediaLibrarySessionCallback callback;
    private final MediaSession session = mock(MediaSession.class);
    private final MediaSession.ControllerInfo controllerInfo = mock(MediaSession.ControllerInfo.class);

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub());
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        callback = new MediaLibrarySessionCallback(context);
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void summaryFirstPlaybackShowsNarrationThenTopicPortions() throws Exception {
        FeedMedia media = seedEpisodeWithSummary("Recap of the game.",
                Arrays.asList(
                        new EpisodeSummary.Topic("Intro", 0),
                        new EpisodeSummary.Topic("Deep dive", 90_000),
                        new EpisodeSummary.Topic("Outro", 240_000)));

        LibraryResult<ImmutableList<MediaItem>> folders = callback.onGetChildren(
                null, controllerInfo, MediaLibrarySessionCallback.MEDIA_ID_SUMMARIES,
                0, 100, null).get(5, TimeUnit.SECONDS);
        assertEquals(1, folders.value.size());

        LibraryResult<ImmutableList<MediaItem>> children = callback.onGetChildren(
                null, controllerInfo,
                MediaLibrarySessionCallback.MEDIA_ID_SUMMARY_PREFIX + media.getItem().getId(),
                0, 100, null).get(5, TimeUnit.SECONDS);
        List<MediaItem> items = children.value;
        assertEquals(4, items.size());
        assertEquals(MediaLibrarySessionCallback.MEDIA_ID_SUMMARY_AUDIO_PREFIX + media.getItem().getId(),
                items.get(0).mediaId);
        assertEquals("Summary: E2E Episode", items.get(0).mediaMetadata.title.toString());

        List<MediaItem> narrationItems = callback.onAddMediaItems(
                session, controllerInfo, Collections.singletonList(items.get(0)))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, narrationItems.size());
        assertEquals(0L, narrationItems.get(0).requestMetadata.extras
                .getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_START));

        String firstTopic = MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX
                + media.getItem().getId() + ":0:90000";
        assertEquals(firstTopic, items.get(1).mediaId);
        MediaSession.MediaItemsWithStartPosition portion = callback.onSetMediaItems(
                session, controllerInfo, Collections.singletonList(items.get(1)),
                C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS);
        assertEquals(1, portion.mediaItems.size());
        assertEquals(0L, portion.startPositionMs);
        Bundle portionExtras = portion.mediaItems.get(0).requestMetadata.extras;
        assertEquals(0L, portionExtras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_START));
        assertEquals(90_000L,
                portionExtras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_END));

        String middleTopic = MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX
                + media.getItem().getId() + ":90000:240000";
        assertEquals(middleTopic, items.get(2).mediaId);
        MediaSession.MediaItemsWithStartPosition middle = callback.onSetMediaItems(
                session, controllerInfo, Collections.singletonList(items.get(2)),
                C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS);
        assertEquals(90_000L, middle.startPositionMs);
        assertEquals(240_000L, middle.mediaItems.get(0).requestMetadata.extras
                .getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_END));
    }

    @Test
    public void summaryLifecycleEndsWhenEpisodeRemoved() throws Exception {
        FeedMedia media = seedEpisodeWithSummary("Temporary recap.",
                Collections.singletonList(new EpisodeSummary.Topic("Only", 0)));
        assertNotNull(DBReader.getEpisodeSummary(media.getItem().getId()));

        DBWriter.deleteFeedItems(context, Collections.singletonList(media.getItem())).get();

        assertNull(DBReader.getEpisodeSummary(media.getItem().getId()));
        LibraryResult<ImmutableList<MediaItem>> folders = callback.onGetChildren(
                null, controllerInfo, MediaLibrarySessionCallback.MEDIA_ID_SUMMARIES,
                0, 100, null).get(5, TimeUnit.SECONDS);
        assertTrue(folders.value.isEmpty());
    }

    private FeedMedia seedEpisodeWithSummary(String text, List<EpisodeSummary.Topic> topics) {
        Feed feed = new Feed("http://example.com/e2e-playback", null, "E2E Playback Podcast");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem();
        item.setItemIdentifier("e2e-playback-episode-" + System.nanoTime());
        item.setTitle("E2E Episode");
        item.setFeed(feed);
        item.setMedia(new FeedMedia(item, "http://example.com/e2e-playback.mp3", 200, "audio/mpeg"));
        feed.getItems().add(item);
        FeedMedia persisted = FeedDatabaseWriter.updateFeed(context, feed, false)
                .getItems().get(0).getMedia();
        persisted.setDownloaded(true, System.currentTimeMillis());
        persisted.setLocalFileUrl("/tmp/e2e-playback.mp3");
        persisted.setDuration(300_000);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setMedia(persisted);
        adapter.close();

        EpisodeSummary summary = new EpisodeSummary(persisted.getItem().getId());
        summary.setText(text);
        summary.setAudioPath("/tmp/e2e-playback-summary.wav");
        summary.setDurationMs(60_000);
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setTopics(topics);
        PodDBAdapter summaryAdapter = PodDBAdapter.getInstance();
        summaryAdapter.open();
        summaryAdapter.setEpisodeSummary(summary);
        summaryAdapter.close();
        return persisted;
    }

}
