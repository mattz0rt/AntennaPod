package de.danoeh.antennapod.model.feed;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EpisodeSummaryTest {

    private EpisodeSummary summaryWithTopics(EpisodeSummary.Topic... topics) {
        EpisodeSummary summary = new EpisodeSummary(42);
        summary.getTopics().addAll(Arrays.asList(topics));
        return summary;
    }

    @Test
    public void segmentEndIsStartOfNextTopic() {
        EpisodeSummary summary = summaryWithTopics(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Main", 60_000),
                new EpisodeSummary.Topic("Outro", 120_000));
        assertEquals(60_000, summary.getSegmentEndMs(0));
        assertEquals(120_000, summary.getSegmentEndMs(1));
    }

    @Test
    public void lastTopicHasNoEnd() {
        EpisodeSummary summary = summaryWithTopics(
                new EpisodeSummary.Topic("Intro", 0),
                new EpisodeSummary.Topic("Outro", 100_000));
        assertEquals(-1, summary.getSegmentEndMs(1));
    }

    @Test
    public void singleTopicHasNoEnd() {
        EpisodeSummary summary = summaryWithTopics(new EpisodeSummary.Topic("Only", 5_000));
        assertEquals(-1, summary.getSegmentEndMs(0));
    }

    @Test
    public void invalidIndexHasNoEnd() {
        EpisodeSummary summary = summaryWithTopics(new EpisodeSummary.Topic("Only", 5_000));
        assertEquals(-1, summary.getSegmentEndMs(-1));
        assertEquals(-1, summary.getSegmentEndMs(99));
    }

    @Test
    public void emptySummaryHasNoTopics() {
        EpisodeSummary summary = new EpisodeSummary(1);
        assertFalse(summary.hasTopics());
        summary.setTopics(null);
        assertTrue(summary.getTopics().isEmpty());
    }

    @Test
    public void defaultsArePendingWithoutTextOrAudio() {
        EpisodeSummary summary = new EpisodeSummary(7);
        assertEquals(EpisodeSummary.STATUS_PENDING, summary.getStatus());
        assertNull(summary.getText());
        assertNull(summary.getAudioPath());
        assertEquals(7, summary.getItemId());
    }
}
