package de.danoeh.antennapod.model.feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EpisodeSummary {
    private final long mediaId;
    private final String text;
    private final String audioFileUrl;
    private final List<EpisodeTopic> topics;

    public EpisodeSummary(long mediaId, String text, String audioFileUrl, List<EpisodeTopic> topics) {
        this.mediaId = mediaId;
        this.text = text;
        this.audioFileUrl = audioFileUrl;
        this.topics = Collections.unmodifiableList(new ArrayList<>(topics));
    }

    public long getMediaId() {
        return mediaId;
    }

    public String getText() {
        return text;
    }

    public String getAudioFileUrl() {
        return audioFileUrl;
    }

    public List<EpisodeTopic> getTopics() {
        return topics;
    }
}
