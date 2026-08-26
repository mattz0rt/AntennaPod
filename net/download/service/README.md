# :net:download:service

The download service.
Uses WorkManager workers (`EpisodeDownloadWorker`, `FeedUpdateWorker`) for background downloads and feed refreshes.
Automatic downloads can run opportunistically or through the daily `AutoDownloadWorker`; scheduling is exposed to consumers through `AutoDownloadManager`.
Scheduled runs use the same episode eligibility settings as opportunistic runs, so the schedule controls timing but does not enable automatic downloads.
