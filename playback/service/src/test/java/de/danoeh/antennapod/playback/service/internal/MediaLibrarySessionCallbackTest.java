package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import com.google.common.collect.ImmutableList;
import androidx.media3.session.MediaSession;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
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
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class MediaLibrarySessionCallbackTest {
    private static final String EPISODE_TITLE = "Episode Title";
    private Context context;
    private MediaLibrarySessionCallback callback;
    private final MediaSession session = mock(MediaSession.class);
    private final MediaSession.ControllerInfo controllerInfo = mock(MediaSession.ControllerInfo.class);

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
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
    public void onSetMediaItemsStub() throws Exception {
        long mediaId = seedEpisode().getId();
        MediaItem browseItem = MediaItemAdapter.fromMediaIdStub(mediaId);
        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(
                session, controllerInfo, Collections.singletonList(browseItem), C.INDEX_UNSET, C.TIME_UNSET)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, result.mediaItems.size());
        assertEquals(String.valueOf(mediaId), result.mediaItems.get(0).mediaId);
        assertEquals(EPISODE_TITLE, result.mediaItems.get(0).mediaMetadata.title);
    }

    @Test
    public void onPlaybackResumption() throws Exception {
        FeedMedia media = seedEpisode();
        PlaybackPreferences.writeMediaPlaying(media);
        MediaSession.MediaItemsWithStartPosition result = callback.onPlaybackResumption(session, controllerInfo)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, result.mediaItems.size());
        assertEquals(String.valueOf(media.getId()), result.mediaItems.get(0).mediaId);
    }

    @Test
    public void onAndroidAutoVoiceSearchQuery() throws Exception {
        long mediaId = seedEpisode().getId();
        MediaItem searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setSearchQuery(EPISODE_TITLE).build())
                .build();
        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS);
        assertEquals(1, result.mediaItems.size());
        assertEquals(String.valueOf(mediaId), result.mediaItems.get(0).mediaId);

        // No match: nothing to play
        searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setSearchQuery("Unrelated").build())
                .build();
        result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS);
        assertEquals(0, result.mediaItems.size());

        // Empty query ("play something"): fall back to playing something rather than nothing, per
        // Android Auto/Assistant voice action guidelines.
        searchItem = MediaItem.EMPTY.buildUpon()
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setSearchQuery("").build())
                .build();
        result = callback.onSetMediaItems(session, controllerInfo,
                Collections.singletonList(searchItem), C.INDEX_UNSET, C.TIME_UNSET).get(5, TimeUnit.SECONDS);
        assertEquals(1, result.mediaItems.size());
        assertEquals(String.valueOf(mediaId), result.mediaItems.get(0).mediaId);
    }


    @Test
    public void summariesNodeListsEpisodesWithSummaries() throws Exception {
        FeedMedia media = seedEpisode();
        EpisodeSummary summary = new EpisodeSummary(media.getItem().getId());
        summary.setText("Narrated recap.");
        media.setLocalFileUrl("/tmp/episode.mp3");
        media.setDownloaded(true, 60_000);
        media.setDuration(120_000);
        PodDBAdapter seedAdapter = PodDBAdapter.getInstance();
        seedAdapter.open();
        seedAdapter.setMedia(media);
        seedAdapter.close();

        summary.setAudioPath("/tmp/summary.wav");
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setTopics(java.util.Arrays.asList(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Deep dive", 60_000)));
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setEpisodeSummary(summary);
        adapter.close();

        LibraryResult<ImmutableList<MediaItem>> folderResult = callback.onGetChildren(
                null, controllerInfo, MediaLibrarySessionCallback.MEDIA_ID_SUMMARIES,
                0, 100, null).get(5, TimeUnit.SECONDS);
        ImmutableList<MediaItem> folders = folderResult.value;
        assertEquals(1, folders.size());
        assertEquals(MediaLibrarySessionCallback.MEDIA_ID_SUMMARY_PREFIX + media.getItem().getId(),
                folders.get(0).mediaId);

        LibraryResult<ImmutableList<MediaItem>> childResult = callback.onGetChildren(null, controllerInfo,
                MediaLibrarySessionCallback.MEDIA_ID_SUMMARY_PREFIX + media.getItem().getId(),
                0, 100, null).get(5, TimeUnit.SECONDS);
        java.util.List<MediaItem> children = childResult.value;
        assertEquals(3, children.size());
        assertEquals(MediaLibrarySessionCallback.MEDIA_ID_SUMMARY_AUDIO_PREFIX + media.getItem().getId(),
                children.get(0).mediaId);
        assertEquals(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX + media.getItem().getId() + ":0:60000",
                children.get(1).mediaId);
        assertEquals(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX + media.getItem().getId() + ":60000:120000",
                children.get(2).mediaId);
    }

    @Test
    public void segmentEntryPointStartsAtTopicOffset() throws Exception {
        FeedMedia media = seedEpisode();
        MediaItem stub = new MediaItem.Builder()
                .setMediaId(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX
                        + media.getItem().getId() + ":30000:90000")
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("t").build())
                .build();
        MediaSession.MediaItemsWithStartPosition result = callback.onSetMediaItems(
                session, controllerInfo, Collections.singletonList(stub), C.INDEX_UNSET, C.TIME_UNSET)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, result.mediaItems.size());
        assertEquals(30_000L, result.startPositionMs);
        android.os.Bundle extras = result.mediaItems.get(0).requestMetadata.extras;
        assertEquals(90_000L, extras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_END));
    }

    @Test
    public void segmentResolutionCarriesStartAndEndExtras() throws Exception {
        FeedMedia media = seedEpisode();
        MediaItem stub = new MediaItem.Builder()
                .setMediaId(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX
                        + media.getItem().getId() + ":30000:90000")
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("t").build())
                .build();
        java.util.List<MediaItem> resolved = callback.onAddMediaItems(
                session, controllerInfo, Collections.singletonList(stub)).get(5, TimeUnit.SECONDS);
        assertEquals(1, resolved.size());
        android.os.Bundle extras = resolved.get(0).requestMetadata.extras;
        assertEquals(30_000L, extras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_START));
        assertEquals(90_000L, extras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_END));
    }

    @Test
    public void malformedSegmentIdResolvesToNothing() throws Exception {
        seedEpisode();
        MediaItem stub = new MediaItem.Builder()
                .setMediaId(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX + "oops")
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("t").build())
                .build();
        java.util.List<MediaItem> resolved = callback.onAddMediaItems(
                session, controllerInfo, Collections.singletonList(stub)).get(5, TimeUnit.SECONDS);
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void negativeSegmentStartClampsToZero() throws Exception {
        FeedMedia media = seedEpisode();
        MediaItem stub = new MediaItem.Builder()
                .setMediaId(MediaLibrarySessionCallback.MEDIA_ID_SEGMENT_PREFIX
                        + media.getItem().getId() + ":-5000:90000")
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("t").build())
                .build();
        java.util.List<MediaItem> resolved = callback.onAddMediaItems(
                session, controllerInfo, Collections.singletonList(stub)).get(5, TimeUnit.SECONDS);
        assertEquals(1, resolved.size());
        android.os.Bundle extras = resolved.get(0).requestMetadata.extras;
        assertEquals(0L, extras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_START));
        assertEquals(90_000L, extras.getLong(MediaLibrarySessionCallback.EXTRA_SUMMARY_END));
    }

    private FeedMedia seedEpisode() {
        Feed feed = new Feed("url", null, null);
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem();
        item.setItemIdentifier("id");
        item.setTitle(EPISODE_TITLE);
        item.setMedia(new FeedMedia(item, "http://example.com", 2, "mime"));
        item.setFeed(feed);
        feed.getItems().add(item);
        return FeedDatabaseWriter.updateFeed(context, feed, false).getItems().get(0).getMedia();
    }
}
