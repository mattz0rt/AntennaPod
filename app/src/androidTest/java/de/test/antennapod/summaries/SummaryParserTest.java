package de.test.antennapod.summaries;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import de.danoeh.antennapod.net.download.service.summaries.SummaryGenerator;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the LLM response parser against the real org.json available on device. */
@RunWith(AndroidJUnit4.class)
public class SummaryParserTest {

    @Test
    public void parsesPlainJson() throws JSONException {
        String response = "{\"summary\":\"Recap of the show.\",\"topics\":["
                + "{\"title\":\"News\",\"start_seconds\":0},"
                + "{\"title\":\"Deep dive\",\"start_seconds\":240}]}";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals("Recap of the show.", data.text);
        assertEquals(2, data.topics.size());
        assertEquals(240_000, data.topics.get(1).getStartMs());
    }

    @Test
    public void parsesFencedJson() throws JSONException {
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(
                "```json\n{\"summary\":\"Fenced.\",\"topics\":[]}\n```");
        assertEquals("Fenced.", data.text);
        assertTrue(data.topics.isEmpty());
    }

    @Test
    public void sortsTopicsAndDropsInvalidEntries() throws JSONException {
        String response = "{\"summary\":\"Sorted.\",\"topics\":["
                + "{\"title\":\"Late\",\"start_seconds\":300},"
                + "{\"title\":\"Broken\"},"
                + "{\"title\":\"Early\",\"start_seconds\":15.5}]}";
        SummaryGenerator.SummaryData data = SummaryGenerator.parseResponse(response);
        assertEquals(2, data.topics.size());
        assertEquals("Early", data.topics.get(0).getTitle());
    }

    @Test(expected = JSONException.class)
    public void rejectsProseResponse() throws JSONException {
        SummaryGenerator.parseResponse("No JSON here at all.");
    }
}
