package de.danoeh.antennapod.ui.chapters;

import de.danoeh.antennapod.model.feed.Chapter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShownotesChapterParser {
    private static final Pattern TIMECODE = Pattern.compile("(?<!\\d)(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})(?!\\d)");
    private static final Pattern SEPARATORS = Pattern.compile("^[\\s\\-–—:|•*\\[\\]()]+|[\\s\\-–—:|•*\\[\\]()]+$");

    private ShownotesChapterParser() {
    }

    public static List<Chapter> parse(String shownotes, long duration) {
        List<Chapter> chapters = new ArrayList<>();
        if (shownotes == null || shownotes.isEmpty()) {
            return chapters;
        }
        Document document = Jsoup.parse(shownotes);
        for (Element element : document.select("br, p, div, li, h1, h2, h3, h4, h5, h6")) {
            element.after("\n");
        }
        for (String line : document.body().wholeText().split("\\R+")) {
            Matcher matcher = TIMECODE.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            long start = parseTime(matcher);
            String title = SEPARATORS.matcher(line.substring(0, matcher.start())
                    + " " + line.substring(matcher.end())).replaceAll("").trim();
            if (title.isEmpty() || start < 0 || (duration > 0 && start >= duration)) {
                continue;
            }
            chapters.add(new Chapter(start, title, null, null));
        }
        chapters.sort(Comparator.comparingLong(Chapter::getStart));
        for (int i = chapters.size() - 1; i > 0; i--) {
            if (chapters.get(i).getStart() == chapters.get(i - 1).getStart()) {
                chapters.remove(i);
            }
        }
        if (chapters.size() < 2) {
            chapters.clear();
        }
        return chapters;
    }

    private static long parseTime(Matcher matcher) {
        try {
            long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            long minutes = Long.parseLong(matcher.group(2));
            long seconds = Long.parseLong(matcher.group(3));
            if ((minutes >= 60 && matcher.group(1) != null) || seconds >= 60) {
                return -1;
            }
            return (hours * 3600 + minutes * 60 + seconds) * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
