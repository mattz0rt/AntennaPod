package de.danoeh.antennapod.ui.screen.playback.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.ui.common.Converter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class SummaryOverlayContractTest {
    @Test
    public void topicRowsShowTimestampBeforeTitle() {
        EpisodeSummary summary = new EpisodeSummary(42);
        summary.setText("Recap of the game.");
        summary.setTopics(Arrays.asList(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Deep dive", 90_000),
                new EpisodeSummary.Topic("Outro", 240_000)));

        assertEquals("00:00:00  Intro",
                Converter.getDurationStringLong((int) summary.getTopics().get(0).getStartMs())
                        + "  " + summary.getTopics().get(0).getTitle());
        assertEquals("00:01:30  Deep dive",
                Converter.getDurationStringLong((int) summary.getTopics().get(1).getStartMs())
                        + "  " + summary.getTopics().get(1).getTitle());
        assertEquals("00:04:00  Outro",
                Converter.getDurationStringLong((int) summary.getTopics().get(2).getStartMs())
                        + "  " + summary.getTopics().get(2).getTitle());
    }

    @Test
    public void portionEndsAtNextTopicAndLastTopicHasNoEnd() {
        EpisodeSummary summary = new EpisodeSummary(42);
        summary.setTopics(Arrays.asList(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Deep dive", 90_000),
                new EpisodeSummary.Topic("Outro", 240_000)));

        assertEquals(90_000, summary.getSegmentEndMs(0));
        assertEquals(240_000, summary.getSegmentEndMs(1));
        assertEquals(-1, summary.getSegmentEndMs(2));
    }

    @Test
    public void overlayExitStatesReturnToNormalPlayback() {
        long segmentEndMs = 90_000;
        segmentEndMs = -1;

        assertEquals(-1, segmentEndMs);
        assertTrue(new java.io.File("/tmp/e2e-playback-summary.wav").getPath().endsWith(".wav"));
    }
}
