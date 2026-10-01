package de.danoeh.antennapod.net.download.service.summaries;

import androidx.test.platform.app.InstrumentationRegistry;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SummaryGeneratorParseTest {

    @Before
    public void setUp() {
        // Robolectric provides the real org.json implementation used by the parser.
        InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void parsesPlainJsonResponse() throws JSONException {
        String response = "{\"summary\":\"Great episode.\",\"topics\":["
                + "{\"title\":\"Intro\",\"start_seconds\":0},"
                + "{\"title\":\"Interview\",\"start_seconds\":125.4}]}";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals("Great episode.", data.text);
        assertEquals(2, data.topics.size());
        assertEquals("Interview", data.topics.get(1).getTitle());
        assertEquals(125_400, data.topics.get(1).getStartMs());
    }

    @Test
    public void parsesFencedJsonResponse() throws JSONException {
        String response = "```json\n{\"summary\":\"Fenced.\",\"topics\":[]}\n```";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals("Fenced.", data.text);
        assertTrue(data.topics.isEmpty());
    }

    @Test
    public void fallsBackToTextField() throws JSONException {
        String response = "{\"text\":\"Alternative key.\",\"topics\":[]}";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals("Alternative key.", data.text);
    }

    @Test
    public void sortsTopicsByStartAndDropsInvalidEntries() throws JSONException {
        String response = "{\"summary\":\"Sorted.\",\"topics\":["
                + "{\"title\":\"Late\",\"start_seconds\":300},"
                + "{\"title\":\"No timestamp\"},"
                + "{\"title\":\"Negative\",\"start_seconds\":-5},"
                + "{\"title\":\"Early\",\"start_seconds\":10}]}";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals(2, data.topics.size());
        assertEquals("Early", data.topics.get(0).getTitle());
        assertEquals("Late", data.topics.get(1).getTitle());
    }

    @Test(expected = JSONException.class)
    public void rejectsNonJsonResponse() throws JSONException {
        SummaryGenerator.parseResponse("The model replied in prose without JSON.");
    }

    @Test
    public void topicModelExposesSegmentBoundaries() {
        EpisodeSummary summary = new EpisodeSummary(1);
        summary.getTopics().add(new EpisodeSummary.Topic("A", 0));
        summary.getTopics().add(new EpisodeSummary.Topic("B", 60_000));
        assertEquals(60_000, summary.getSegmentEndMs(0));
        assertEquals(-1, summary.getSegmentEndMs(1));
    }
}
