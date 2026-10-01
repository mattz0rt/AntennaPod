package de.danoeh.antennapod.net.download.service.summaries;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class SummaryEndToEndTest {
    private FeedMedia media;

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getContext();
        RuntimeEnvironment.getApplication();
        PodDBAdapter.init(RuntimeEnvironment.getApplication());
        PodDBAdapter.deleteDatabase();
        media = seedDownloadedEpisode();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void downloadToSummaryToPlaybackUsesChaptersWhenPresent() throws Exception {
        FeedItem item = media.getItem();
        item.setDescriptionIfLonger("Postseason breakdown with timestamps.");
        item.setChapters(Arrays.asList(
                new Chapter(0, "Intro", null, null),
                new Chapter(90_000, "Deep dive", null, null)));

        String providerJson = "{\"summary\":\"Recap of the game.\",\"topics\":[]}";
        SummaryGenerator.SummaryData parsed = SummaryGenerator.parseResponse(providerJson);
        EpisodeSummary summary = new EpisodeSummary(item.getId());
        summary.setText(parsed.text);
        summary.setAudioPath("/tmp/e2e-summary.wav");
        summary.setDurationMs(60_000);
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setTopics(Arrays.asList(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Deep dive", 90_000)));
        DBWriter.setEpisodeSummary(summary).get();

        EpisodeSummary stored = DBReader.getEpisodeSummary(item.getId());
        assertNotNull(stored);
        assertEquals("Recap of the game.", stored.getText());
        assertEquals(2, stored.getTopics().size());
        assertEquals("Intro", stored.getTopics().get(0).getTitle());
        assertEquals(0, stored.getTopics().get(0).getStartMs());
        assertEquals(90_000, stored.getSegmentEndMs(0));
        assertEquals(-1, stored.getSegmentEndMs(1));
    }

    @Test
    public void downloadToSummaryUsesAiTopicsWhenNoChapters() throws Exception {
        FeedItem item = media.getItem();
        item.setDescriptionIfLonger("No chapter markers in these notes.");
        item.setChapters(null);

        String providerJson = "{\"summary\":\"Wide ranging chat.\",\"topics\":["
                + "{\"title\":\"Late\",\"start_seconds\":300},"
                + "{\"title\":\"Early\",\"start_seconds\":15}]}";
        SummaryGenerator.SummaryData parsed = SummaryGenerator.parseResponse(providerJson);
        EpisodeSummary summary = new EpisodeSummary(item.getId());
        summary.setText(parsed.text);
        summary.setAudioPath("/tmp/e2e-summary-ai.wav");
        summary.setDurationMs(60_000);
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setTopics(parsed.topics);
        DBWriter.setEpisodeSummary(summary).get();

        EpisodeSummary stored = DBReader.getEpisodeSummary(item.getId());
        assertNotNull(stored);
        assertEquals(2, stored.getTopics().size());
        assertEquals("Early", stored.getTopics().get(0).getTitle());
        assertEquals(15_000, stored.getTopics().get(0).getStartMs());
        assertEquals("Late", stored.getTopics().get(1).getTitle());
    }

    @Test
    public void removingEpisodeRemovesSummaryLifecycle() throws Exception {
        EpisodeSummary summary = new EpisodeSummary(media.getItem().getId());
        summary.setText("To be deleted.");
        summary.setAudioPath("/tmp/e2e-delete.wav");
        summary.setStatus(EpisodeSummary.STATUS_DONE);
        summary.setTopics(Collections.singletonList(new EpisodeSummary.Topic("Only", 0)));
        DBWriter.setEpisodeSummary(summary).get();
        assertNotNull(DBReader.getEpisodeSummary(media.getItem().getId()));

        DBWriter.deleteFeedItems(RuntimeEnvironment.getApplication(),
                Collections.singletonList(media.getItem())).get();

        assertNull(DBReader.getEpisodeSummary(media.getItem().getId()));
    }

    private FeedMedia seedDownloadedEpisode() {
        Feed feed = new Feed("http://example.com/e2e", null, "E2E Podcast");
        feed.setItems(new ArrayList<>());
        FeedItem item = new FeedItem();
        item.setItemIdentifier("e2e-episode");
        item.setTitle("E2E Episode");
        item.setDescriptionIfLonger("E2E episode notes.");
        item.setMedia(new FeedMedia(item, "http://example.com/e2e.mp3", 100, "audio/mpeg"));
        item.setFeed(feed);
        feed.getItems().add(item);
        FeedMedia persisted = FeedDatabaseWriter.updateFeed(
                RuntimeEnvironment.getApplication(), feed, false).getItems().get(0).getMedia();
        persisted.setLocalFileUrl("/tmp/e2e-episode.mp3");
        persisted.setDownloaded(true, 3_600_000);
        persisted.setDuration(3_600_000);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setMedia(persisted);
        adapter.close();
        return persisted;
    }
}
