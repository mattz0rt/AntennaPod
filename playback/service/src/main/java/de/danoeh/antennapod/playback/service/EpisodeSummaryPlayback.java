package de.danoeh.antennapod.playback.service;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import java.io.File;

public final class EpisodeSummaryPlayback {
    private EpisodeSummaryPlayback() {
    }

    public static boolean shouldInclude(FeedMedia media, EpisodeSummary summary, boolean globalDefault) {
        if (media == null || summary == null || summary.getAudioFileUrl() == null
                || !new File(summary.getAudioFileUrl()).isFile()) {
            return false;
        }
        FeedItem item = media.getItem();
        return item != null && item.isTagged(FeedItem.TAG_QUEUE) && item.getFeed() != null
                && item.getFeed().getPreferences() != null
                && item.getFeed().getPreferences().isEpisodeSummaryEnabled(globalDefault);
    }
}
