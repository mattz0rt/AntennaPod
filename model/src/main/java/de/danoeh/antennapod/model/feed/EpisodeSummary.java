package de.danoeh.antennapod.model.feed;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AI-generated summary of a podcast episode together with its topics.
 * The summary is narrated as an audio file whose path is stored in {@link #audioPath}.
 */
public class EpisodeSummary {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    private long id;
    private long itemId;
    @Nullable
    private String text;
    @Nullable
    private String audioPath;
    private long durationMs;
    private int status = STATUS_PENDING;
    private long createdAt;
    private List<Topic> topics = new ArrayList<>();

    public EpisodeSummary(long itemId) {
        this.itemId = itemId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    @Nullable
    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    @Nullable
    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(@Nullable String audioPath) {
        this.audioPath = audioPath;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<Topic> getTopics() {
        return topics;
    }

    public void setTopics(List<Topic> topics) {
        this.topics = topics != null ? topics : new ArrayList<>();
    }

    public boolean hasTopics() {
        return !topics.isEmpty();
    }

    /**
     * Returns the position where playback of the topic with the given index should stop,
     * i.e. the start of the following topic or -1 if it is the last topic.
     */
    public long getSegmentEndMs(int topicIndex) {
        if (topicIndex < 0 || topicIndex >= topics.size()) {
            return -1;
        }
        if (topicIndex == topics.size() - 1) {
            return -1;
        }
        return topics.get(topicIndex + 1).getStartMs();
    }

    /**
     * One topic covered by the episode, anchored at a timestamp.
     */
    public static class Topic {
        private String title;
        private long startMs;

        public Topic(String title, long startMs) {
            this.title = title;
            this.startMs = startMs;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public long getStartMs() {
            return startMs;
        }

        public void setStartMs(long startMs) {
            this.startMs = startMs;
        }
    }
}
