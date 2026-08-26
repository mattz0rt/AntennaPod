package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AutoDownloadManagerImpl extends AutoDownloadManager {
    private static final String TAG = "AutoDownloadManager";
    private static final String WORK_ID_AUTO_DOWNLOAD = "de.danoeh.antennapod.AutoDownloadWorker";

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private static final ExecutorService autodownloadExec;

    private static AutomaticDownloadAlgorithm downloadAlgorithm = new AutomaticDownloadAlgorithm();

    static {
        autodownloadExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    /**
     * Looks for non-downloaded episodes in the queue or list of unread items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    public Future<?> autodownloadUndownloadedItems(final Context context) {
        Log.d(TAG, "autodownloadUndownloadedItems");
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadUndownloadedItems(context));
    }

    @Override
    public Future<?> runScheduledDownload(final Context context) {
        Log.d(TAG, "runScheduledDownload");
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadUndownloadedItems(context, true));
    }

    @Override
    public void restartSchedule(final Context context, boolean replace) {
        WorkManager workManager = WorkManager.getInstance(context);
        if (!UserPreferences.isAutodownloadScheduled()) {
            workManager.cancelUniqueWork(WORK_ID_AUTO_DOWNLOAD);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(UserPreferences.isAllowMobileAutoDownload()
                        ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .setRequiresCharging(!UserPreferences.isEnableAutodownloadOnBattery())
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoDownloadWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelay(
                        System.currentTimeMillis(), UserPreferences.getAutodownloadTimeMinutes()),
                        TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_ID_AUTO_DOWNLOAD,
                replace ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                        : ExistingPeriodicWorkPolicy.KEEP, request);
    }

    static long calculateInitialDelay(long currentTimeMillis, int minuteOfDay) {
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(currentTimeMillis);
        next.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        next.set(Calendar.MINUTE, minuteOfDay % 60);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= currentTimeMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - currentTimeMillis;
    }

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'lastPlayedTimeHistory'-value will be deleted first.
     * <p/>
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     */
    public void performAutoCleanup(final Context context) {
        EpisodeCleanupAlgorithmFactory.build().performCleanup(context);
    }
}
