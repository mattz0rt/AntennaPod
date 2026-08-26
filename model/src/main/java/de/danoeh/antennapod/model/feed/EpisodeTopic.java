package de.danoeh.antennapod.model.feed;

import java.util.Objects;

public class EpisodeTopic {
    private final String title;
    private final long start;
    private final long end;

    public EpisodeTopic(String title, long start, long end) {
        this.title = title;
        this.start = start;
        this.end = end;
    }

    public String getTitle() {
        return title;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EpisodeTopic)) {
            return false;
        }
        EpisodeTopic topic = (EpisodeTopic) other;
        return start == topic.start && end == topic.end && Objects.equals(title, topic.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, start, end);
    }
}
