package de.danoeh.antennapod.net.download.service.episode;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.EpisodeTopic;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.download.service.R;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.chapters.ShownotesChapterParser;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import de.danoeh.antennapod.ui.transcript.TranscriptUtils;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EpisodeSummaryWorker extends Worker {
    private static final String TAG = "EpisodeSummaryWorker";
    private static final String WORK_DATA_MEDIA_ID = "media_id";
    private static final long MAX_GROQ_UPLOAD_SIZE = 24L * 1024L * 1024L;
    private static final String GROQ_TRANSCRIPTION_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String GEMINI_TEXT_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + "gemini-3.5-flash:generateContent";
    private static final String GEMINI_TTS_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + "gemini-3.1-flash-tts-preview:generateContent";

    public EpisodeSummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, FeedMedia media) {
        Data data = new Data.Builder().putLong(WORK_DATA_MEDIA_ID, media.getId()).build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EpisodeSummaryWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .addTag(DownloadServiceInterface.WORK_TAG)
                .addTag(DownloadServiceInterface.WORK_TAG_EPISODE_URL + media.getDownloadUrl())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("episode-summary-" + media.getId(),
                ExistingWorkPolicy.REPLACE, request);
    }

    @Override
    @NonNull
    public Result doWork() {
        long mediaId = getInputData().getLong(WORK_DATA_MEDIA_ID, 0);
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        if (media == null || !media.localFileAvailable()) {
            return Result.success();
        }
        if (media.getItem() == null || media.getItem().getFeed() == null
                || !media.getItem().getFeed().getPreferences()
                        .isEpisodeSummaryEnabled(UserPreferences.isEpisodeSummaryEnabled())) {
            return Result.success();
        }
        String groqKey = UserPreferences.getGroqApiKey();
        String geminiKey = UserPreferences.getGeminiApiKey();
        if (groqKey.isEmpty() || geminiKey.isEmpty()) {
            Log.i(TAG, "Episode summary credentials are not configured");
            return Result.success();
        }
        try {
            setForegroundAsync(createForegroundInfo(media)).get();
            TranscriptData transcript = loadTranscript(media, groqKey);
            List<EpisodeTopic> existingTopics = loadExistingTopics(media);
            SummaryData generated = generateSummary(media, transcript, geminiKey);
            List<EpisodeTopic> topics = existingTopics.isEmpty() ? generated.topics : existingTopics;
            String audioFile = generateNarration(mediaId, generated.summary, geminiKey);
            FeedMedia storedMedia = DBReader.getFeedMedia(mediaId);
            if (storedMedia == null || !storedMedia.localFileAvailable()) {
                new File(audioFile).delete();
                return Result.success();
            }
            boolean stored = false;
            try {
                stored = DBWriter.setEpisodeSummary(
                        new EpisodeSummary(mediaId, generated.summary, audioFile, topics)).get();
            } finally {
                if (!stored) {
                    new File(audioFile).delete();
                }
            }
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        } catch (ApiException e) {
            Log.e(TAG, "Could not create episode summary", e);
            return e.retryable ? Result.retry() : Result.failure();
        } catch (IOException e) {
            Log.e(TAG, "Could not create episode summary", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Could not create episode summary", e);
            return Result.failure();
        }
    }

    private ForegroundInfo createForegroundInfo(FeedMedia media) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setContentTitle(getApplicationContext().getString(R.string.episode_summary_notification))
                .setContentText(media.getEpisodeTitle())
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return new ForegroundInfo(R.id.notification_episode_summary, notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private TranscriptData loadTranscript(FeedMedia media, String groqKey) throws Exception {
        if (media.getItem() != null && media.getItem().getTranscriptUrl() != null) {
            Transcript transcript = TranscriptUtils.loadTranscript(media, false);
            if (transcript != null && transcript.getSegmentCount() > 0) {
                List<Segment> segments = new ArrayList<>();
                for (int i = 0; i < transcript.getSegmentCount(); i++) {
                    TranscriptSegment segment = transcript.getSegmentAt(i);
                    segments.add(new Segment(segment.getStartTime(), segment.getEndTime(), segment.getWords()));
                }
                return new TranscriptData(segments);
            }
        }
        File audioFile = new File(media.getLocalFileUrl());
        MultipartBody.Builder requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "segment");
        if (audioFile.length() <= MAX_GROQ_UPLOAD_SIZE) {
            MediaType mediaType = MediaType.parse(media.getMimeType() == null
                    ? "application/octet-stream" : media.getMimeType());
            requestBody.addFormDataPart("file", audioFile.getName(), RequestBody.create(audioFile, mediaType));
        } else {
            requestBody.addFormDataPart("url", media.getDownloadUrl());
        }
        Request request = new Request.Builder()
                .url(GROQ_TRANSCRIPTION_URL)
                .header("Authorization", "Bearer " + groqKey)
                .post(requestBody.build())
                .build();
        JSONObject json = executeJson(request);
        JSONArray values = json.getJSONArray("segments");
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            segments.add(new Segment(Math.round(value.getDouble("start") * 1000),
                    Math.round(value.getDouble("end") * 1000), value.getString("text").trim()));
        }
        return new TranscriptData(segments);
    }

    private List<EpisodeTopic> loadExistingTopics(FeedMedia media) throws Exception {
        if (media.getItem() == null) {
            return new ArrayList<>();
        }
        List<Chapter> chapters = null;
        if (media.getItem().hasChapters()) {
            chapters = DBReader.loadChaptersOfFeedItem(media.getItem());
        } else {
            DBReader.loadDescriptionOfFeedItem(media.getItem());
            chapters = ShownotesChapterParser.parse(media.getItem().getDescription(), media.getDuration());
            if (!chapters.isEmpty()) {
                media.getItem().setChapters(chapters);
                DBWriter.setFeedItem(media.getItem(), false).get();
            }
        }
        List<EpisodeTopic> topics = new ArrayList<>();
        if (chapters == null) {
            return topics;
        }
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            long end = i + 1 < chapters.size() ? chapters.get(i + 1).getStart() : media.getDuration();
            if (end > chapter.getStart() && chapter.getTitle() != null && !chapter.getTitle().trim().isEmpty()) {
                topics.add(new EpisodeTopic(chapter.getTitle().trim(), chapter.getStart(), end));
            }
        }
        return topics;
    }

    private SummaryData generateSummary(FeedMedia media, TranscriptData transcript, String geminiKey)
            throws Exception {
        int minutes = UserPreferences.getEpisodeSummaryLengthMinutes();
        String customPrompt = UserPreferences.getEpisodeSummaryPrompt();
        if (customPrompt.isEmpty()) {
            customPrompt = getApplicationContext().getString(R.string.pref_episode_summary_prompt_default);
        }
        String prompt = customPrompt + "\nWrite about " + (minutes * 150) + " words for approximately "
                + minutes + " minutes of narration. Return a summary and a concise list of non-overlapping topics. "
                + "Each topic must use millisecond timestamps grounded in the transcript. Episode duration: "
                + media.getDuration() + " ms.\n\n" + transcript.formatted;
        JSONObject topicSchema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("title", new JSONObject().put("type", "STRING"))
                        .put("start_ms", new JSONObject().put("type", "INTEGER"))
                        .put("end_ms", new JSONObject().put("type", "INTEGER")))
                .put("required", new JSONArray().put("title").put("start_ms").put("end_ms"));
        JSONObject schema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("summary", new JSONObject().put("type", "STRING"))
                        .put("topics", new JSONObject().put("type", "ARRAY").put("items", topicSchema)))
                .put("required", new JSONArray().put("summary").put("topics"));
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", schema));
        JSONObject response = executeJson(geminiRequest(GEMINI_TEXT_URL, geminiKey, body));
        JSONObject result = new JSONObject(response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"));
        List<EpisodeTopic> topics = new ArrayList<>();
        JSONArray values = result.getJSONArray("topics");
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            long start = Math.max(0, value.getLong("start_ms"));
            long end = Math.min(media.getDuration(), value.getLong("end_ms"));
            String title = value.getString("title").trim();
            if (start < end && !title.isEmpty()) {
                topics.add(new EpisodeTopic(title, start, end));
            }
        }
        topics.sort(Comparator.comparingLong(EpisodeTopic::getStart));
        if (topics.isEmpty()) {
            throw new IOException("AI response did not contain episode topics");
        }
        return new SummaryData(result.getString("summary"), topics);
    }

    private String generateNarration(long mediaId, String summary, String geminiKey) throws Exception {
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", summary)))))
                .put("generationConfig", new JSONObject()
                        .put("responseModalities", new JSONArray().put("AUDIO"))
                        .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                                .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", "Kore")))));
        JSONObject response = executeJson(geminiRequest(GEMINI_TTS_URL, geminiKey, body));
        String encoded = response.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data");
        byte[] pcm = Base64.decode(encoded, Base64.DEFAULT);
        File directory = new File(getApplicationContext().getFilesDir(), "episode-summaries");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create episode summary directory");
        }
        File output = new File(directory, mediaId + ".wav");
        writeWaveFile(output, pcm);
        return output.getAbsolutePath();
    }

    private Request geminiRequest(String url, String apiKey, JSONObject body) {
        return new Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
    }

    private JSONObject executeJson(Request request) throws Exception {
        try (Response response = AntennapodHttpClient.getHttpClient().newBuilder()
                .readTimeout(5, TimeUnit.MINUTES).build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                int code = response.code();
                throw new ApiException("AI request failed with HTTP " + code, code == 429 || code >= 500);
            }
            return new JSONObject(response.body().string());
        }
    }

    private void writeWaveFile(File output, byte[] pcm) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndian(stream, 36 + pcm.length, 4);
            stream.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndian(stream, 16, 4);
            writeLittleEndian(stream, 1, 2);
            writeLittleEndian(stream, 1, 2);
            writeLittleEndian(stream, 24000, 4);
            writeLittleEndian(stream, 48000, 4);
            writeLittleEndian(stream, 2, 2);
            writeLittleEndian(stream, 16, 2);
            stream.write("data".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndian(stream, pcm.length, 4);
            stream.write(pcm);
        }
    }

    private void writeLittleEndian(FileOutputStream stream, int value, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) {
            stream.write((value >> (8 * i)) & 0xff);
        }
    }

    private static class Segment {
        final long start;
        final long end;
        final String text;

        Segment(long start, long end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private static class ApiException extends IOException {
        final boolean retryable;

        ApiException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private static class TranscriptData {
        final String formatted;

        TranscriptData(List<Segment> segments) {
            StringBuilder value = new StringBuilder();
            for (Segment segment : segments) {
                value.append('[').append(segment.start).append('-').append(segment.end).append("] ")
                        .append(segment.text).append('\n');
            }
            formatted = value.toString();
        }
    }

    private static class SummaryData {
        final String summary;
        final List<EpisodeTopic> topics;

        SummaryData(String summary, List<EpisodeTopic> topics) {
            this.summary = summary;
            this.topics = topics;
        }
    }
}
