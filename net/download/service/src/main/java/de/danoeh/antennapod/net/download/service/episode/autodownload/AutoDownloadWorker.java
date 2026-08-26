package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;

public class AutoDownloadWorker extends Worker {
    public AutoDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    @NonNull
    public Result doWork() {
        try {
            AutoDownloadManager.getInstance().runScheduledDownload(getApplicationContext()).get();
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
