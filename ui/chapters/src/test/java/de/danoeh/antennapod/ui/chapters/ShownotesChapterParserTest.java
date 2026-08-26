package de.danoeh.antennapod.ui.chapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.danoeh.antennapod.model.feed.Chapter;
import org.junit.Test;

import java.util.List;

public class ShownotesChapterParserTest {
    @Test
    public void parseTimecodesAtStartAndEnd() {
        String shownotes = "<p>00:00 Introduction</p><p>Deep dive — 12:34</p><p>1:02:03 Questions</p>";

        List<Chapter> chapters = ShownotesChapterParser.parse(shownotes, 4_000_000);

        assertEquals(3, chapters.size());
        assertEquals("Introduction", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStart());
        assertEquals("Deep dive", chapters.get(1).getTitle());
        assertEquals(754_000, chapters.get(1).getStart());
        assertEquals("Questions", chapters.get(2).getTitle());
        assertEquals(3_723_000, chapters.get(2).getStart());
    }

    @Test
    public void parsePlainTextAndRemoveDuplicateTimes() {
        String shownotes = "* Welcome 00:00\n- Duplicate 00:00\n* Topic 05:30";

        List<Chapter> chapters = ShownotesChapterParser.parse(shownotes, 600_000);

        assertEquals(2, chapters.size());
        assertEquals("Welcome", chapters.get(0).getTitle());
        assertEquals("Topic", chapters.get(1).getTitle());
    }

    @Test
    public void rejectSingleOrOutOfRangeTimecode() {
        assertTrue(ShownotesChapterParser.parse("00:00 Introduction", 60_000).isEmpty());
        assertTrue(ShownotesChapterParser.parse("00:00 Introduction\n02:00 Ending", 60_000).isEmpty());
    }
}
