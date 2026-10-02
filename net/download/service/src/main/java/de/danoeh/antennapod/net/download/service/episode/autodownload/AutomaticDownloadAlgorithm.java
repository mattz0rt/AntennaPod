package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.net.common.NetworkUtils;

/**
 * Implements the automatic download algorithm used by AntennaPod. This class assumes that
 * the client uses the {@link EpisodeCleanupAlgorithm}.
 */
public class AutomaticDownloadAlgorithm {
    private static final String TAG = "DownloadAlgorithm";

    /**
     * Looks for undownloaded episodes in the queue or list of new items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Runnable that will be submitted to an ExecutorService.
     */
    public Runnable autoDownloadUndownloadedItems(final Context context) {
        return () -> {

            boolean networkShouldAutoDl = NetworkUtils.isAutoDownloadAllowed();
            boolean powerShouldAutoDl = deviceCharging(context) || UserPreferences.isEnableAutodownloadOnBattery();
            boolean timeShouldAutoDl = isInAutodownloadWindow(java.util.Calendar.getInstance());

            if (networkShouldAutoDl && powerShouldAutoDl && timeShouldAutoDl) {

                Log.d(TAG, "Performing auto-dl of undownloaded episodes");

                final List<FeedItem> newItems = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                        new FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD);
                final List<FeedItem> candidates = new ArrayList<>();
                for (FeedItem newItem : newItems) {
                    FeedPreferences feedPrefs = newItem.getFeed().getPreferences();
                    if (feedPrefs.isAutoDownload(UserPreferences.isEnableAutodownloadGlobal())
                            && !candidates.contains(newItem)
                            && feedPrefs.getFilter().shouldAutoDownload(newItem)) {
                        candidates.add(newItem);
                    }
                }

                if (UserPreferences.isEnableAutodownloadQueue()) {
                    final List<FeedItem> queue = DBReader.getQueue();
                    for (FeedItem item : queue) {
                        if (!candidates.contains(item)) {
                            candidates.add(item);
                        }
                    }
                }

                // filter items that are not auto downloadable
                Iterator<FeedItem> it = candidates.iterator();
                while (it.hasNext()) {
                    FeedItem item = it.next();
                    if (!item.isAutoDownloadEnabled()
                            || item.isDownloaded()
                            || !item.hasMedia()
                            || item.getFeed().isLocalFeed()) {
                        it.remove();
                    }
                }

                int autoDownloadableEpisodes = candidates.size();
                int downloadedEpisodes = DBReader.getTotalEpisodeCount(new FeedItemFilter(FeedItemFilter.DOWNLOADED));
                downloadedEpisodes += DownloadServiceInterface.get().getNumberOfActiveDownloads(context);
                int deletedEpisodes = EpisodeCleanupAlgorithmFactory.build()
                        .makeRoomForEpisodes(context, autoDownloadableEpisodes);
                boolean cacheIsUnlimited =
                        UserPreferences.getEpisodeCacheSize() == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED;
                int episodeCacheSize = UserPreferences.getEpisodeCacheSize();

                int episodeSpaceLeft;
                if (cacheIsUnlimited || episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) {
                    episodeSpaceLeft = autoDownloadableEpisodes;
                } else {
                    episodeSpaceLeft = episodeCacheSize - (downloadedEpisodes - deletedEpisodes);
                }

                List<FeedItem> itemsToDownload = candidates.subList(0, episodeSpaceLeft);
                if (!itemsToDownload.isEmpty()) {
                    Log.d(TAG, "Enqueueing " + itemsToDownload.size() + " items for download");

                    for (FeedItem episode : itemsToDownload) {
                        DownloadServiceInterface.get().download(context, episode);
                    }
                }
            }
        };
    }

    static boolean isInAutodownloadWindow(java.util.Calendar now) {
        if (!UserPreferences.isAutodownloadTimeRestricted()) {
            return true;
        }
        int current = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int from = UserPreferences.getAutodownloadTimeFrom();
        int to = UserPreferences.getAutodownloadTimeTo();
        if (from == to) {
            return false;
        }
        if (from < to) {
            return from <= current && current < to;
        }
        return current >= from || current < to;
    }

    public static boolean deviceCharging(Context context) {
        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }
}
