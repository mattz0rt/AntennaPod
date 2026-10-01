package de.danoeh.antennapod.net.download.service.summaries;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Creates an AI summary and a locally narrated audio file after an episode is downloaded. */
public final class SummaryGenerator {
    private static final String TAG = "SummaryGenerator";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";
    private static final long MAX_GROQ_AUDIO_BYTES = 25L * 1024 * 1024;
    private static final long MAX_GEMINI_INLINE_AUDIO_BYTES = 15L * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EpisodeSummaryGenerator");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private SummaryGenerator() {
    }

    public static void enqueueIfEnabled(@NonNull Context context, @NonNull FeedMedia media) {
        if (!UserPreferences.areEpisodeSummariesEnabled() || media.getLocalFileUrl() == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> generate(appContext, media.getId()));
    }

    private static void generate(Context context, long mediaId) {
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        if (media == null || media.getLocalFileUrl() == null) {
            return;
        }
        FeedItem item = media.getItem();
        if (item == null) {
            return;
        }
        EpisodeSummary existing = DBReader.getEpisodeSummary(item.getId());
        if (existing != null && (existing.getStatus() == EpisodeSummary.STATUS_PENDING
                || existing.getStatus() == EpisodeSummary.STATUS_DONE)) {
            return;
        }

        EpisodeSummary pending = new EpisodeSummary(item.getId());
        pending.setStatus(EpisodeSummary.STATUS_PENDING);
        pending.setCreatedAt(System.currentTimeMillis());
        try {
            DBWriter.setEpisodeSummary(pending).get();
            File audioFile = new File(media.getLocalFileUrl());
            String source = buildEpisodeSource(item);
            String provider = UserPreferences.getSummaryProvider().toLowerCase(Locale.US);
            SummaryData data = null;
            if (!"gemini".equals(provider) && audioFile.length() <= MAX_GROQ_AUDIO_BYTES) {
                data = createWithGroq(audioFile, source);
            }
            if (data == null && audioFile.length() <= MAX_GEMINI_INLINE_AUDIO_BYTES) {
                data = createWithGemini(audioFile, source, audioFile.getName());
            }
            if (data == null && !source.trim().isEmpty()) {
                data = createTextOnlyWithGemini(source);
            }
            if (data == null || data.text.trim().isEmpty()) {
                throw new IOException("No summary provider returned a result");
            }

            File summaryDirectory = new File(context.getExternalFilesDir(null), "episode-summaries");
            if (!summaryDirectory.exists() && !summaryDirectory.mkdirs()) {
                throw new IOException("Unable to create summary directory");
            }
            File output = new File(summaryDirectory, item.getId() + ".wav");
            synthesize(context, data.text, output);

            EpisodeSummary result = new EpisodeSummary(item.getId());
            result.setText(data.text);
            result.setAudioPath(output.getAbsolutePath());
            result.setDurationMs(UserPreferences.getSummaryLengthSeconds() * 1000L);
            result.setStatus(EpisodeSummary.STATUS_DONE);
            result.setCreatedAt(System.currentTimeMillis());
            result.setTopics(data.topics);
            DBWriter.setEpisodeSummary(result).get();
        } catch (Exception e) {
            Log.e(TAG, "Unable to generate summary for media " + mediaId, e);
            EpisodeSummary failed = new EpisodeSummary(item.getId());
            failed.setStatus(EpisodeSummary.STATUS_FAILED);
            failed.setCreatedAt(System.currentTimeMillis());
            try {
                DBWriter.setEpisodeSummary(failed).get();
            } catch (Exception databaseError) {
                Log.e(TAG, "Unable to persist summary failure", databaseError);
            }
        }
    }

    @NonNull
    private static String buildEpisodeSource(@NonNull FeedItem item) {
        StringBuilder source = new StringBuilder();
        if (item.getDescription() != null) {
            source.append(item.getDescription()).append('\n');
        }
        if (item.getChapters() != null) {
            source.append("Chapters with start timestamps:\n");
            for (de.danoeh.antennapod.model.feed.Chapter chapter : item.getChapters()) {
                source.append('[').append(chapter.getStart() / 1000).append(" seconds] ")
                        .append(chapter.getTitle()).append('\n');
            }
        }
        return source.toString();
    }

    @Nullable
    private static SummaryData createWithGroq(@NonNull File audioFile, @NonNull String source) throws IOException {
        String key = UserPreferences.getGroqKey();
        if (key.trim().isEmpty()) {
            return null;
        }
        OkHttpClient client = new OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build();
        RequestBody audio = RequestBody.create(audioFile, MediaType.parse("application/octet-stream"));
        RequestBody form = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), audio)
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .addFormDataPart("response_format", "verbose_json")
                .build();
        Request request = new Request.Builder()
                .url(GROQ_URL + "/audio/transcriptions")
                .header("Authorization", "Bearer " + key)
                .post(form)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JSONObject transcription = new JSONObject(response.body().string());
            StringBuilder timedText = new StringBuilder(source);
            JSONArray segments = transcription.optJSONArray("segments");
            if (segments != null) {
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject segment = segments.optJSONObject(i);
                    if (segment != null) {
                        timedText.append('[').append(segment.optDouble("start", 0)).append(" seconds] ")
                                .append(segment.optString("text", "")).append('\n');
                    }
                }
            } else {
                timedText.append(transcription.optString("text", ""));
            }
            return createChatCompletion(client, key, timedText.toString());
        } catch (JSONException e) {
            throw new IOException("Invalid Groq transcription response", e);
        }
    }

    @Nullable
    private static SummaryData createChatCompletion(@NonNull OkHttpClient client, @NonNull String key,
                                                     @NonNull String source) throws IOException {
        String prompt = String.format(Locale.US, UserPreferences.getSummaryPrompt(),
                UserPreferences.getSummaryLengthSeconds());
        JSONObject body = new JSONObject();
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", prompt));
            messages.put(new JSONObject().put("role", "user").put("content", source));
            body.put("model", "llama-3.3-70b-versatile");
            body.put("messages", messages);
            body.put("temperature", 0.2);
            body.put("response_format", new JSONObject().put("type", "json_object"));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        Request request = new Request.Builder()
                .url(GROQ_URL + "/chat/completions")
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JSONObject result = new JSONObject(response.body().string());
            String content = result.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
            return parseResponse(content);
        } catch (JSONException e) {
            throw new IOException("Invalid Groq completion response", e);
        }
    }

    @Nullable
    private static SummaryData createWithGemini(@NonNull File audioFile, @NonNull String source,
                                                 @NonNull String displayName) throws IOException {
        String key = UserPreferences.getGeminiKey();
        if (key.trim().isEmpty()) {
            return null;
        }
        String mimeType = audioFile.getName().endsWith(".m4a") ? "audio/mp4" : "audio/mpeg";
        String encoded = Base64.encodeToString(readFile(audioFile), Base64.NO_WRAP);
        String prompt = String.format(Locale.US, UserPreferences.getSummaryPrompt(),
                UserPreferences.getSummaryLengthSeconds()) + "\n" + source;
        JSONObject body = new JSONObject();
        try {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            parts.put(new JSONObject().put("inline_data", new JSONObject()
                    .put("mime_type", mimeType).put("data", encoded)));
            body.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return requestGemini(key, body);
    }

    @Nullable
    private static SummaryData createTextOnlyWithGemini(@NonNull String source) throws IOException {
        String key = UserPreferences.getGeminiKey();
        if (key.trim().isEmpty()) {
            return null;
        }
        JSONObject body = new JSONObject();
        try {
            String prompt = String.format(Locale.US, UserPreferences.getSummaryPrompt(),
                    UserPreferences.getSummaryLengthSeconds());
            body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", prompt + "\n" + source)))));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return requestGemini(key, body);
    }

    @Nullable
    private static SummaryData requestGemini(@NonNull String key, @NonNull JSONObject body) throws IOException {
        Request request = new Request.Builder()
                .url(GEMINI_URL + "?key=" + key)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = new OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
                .newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JSONObject result = new JSONObject(response.body().string());
            String content = result.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text");
            return parseResponse(content);
        } catch (JSONException e) {
            throw new IOException("Invalid Gemini response", e);
        }
    }

    @NonNull
    private static byte[] readFile(@NonNull File file) throws IOException {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) != -1) {
                offset += read;
            }
            return bytes;
        } finally {
            input.close();
        }
    }

    /** Parses provider output and accepts both plain JSON and fenced JSON. */
    @NonNull
    public static SummaryData parseResponse(@NonNull String response) throws JSONException {
        String json = response.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int closingFence = json.lastIndexOf("```");
            if (firstNewline >= 0 && closingFence > firstNewline) {
                json = json.substring(firstNewline + 1, closingFence).trim();
            }
        }
        JSONObject object = new JSONObject(json);
        String text = object.optString("summary", object.optString("text", "")).trim();
        List<EpisodeSummary.Topic> topics = new ArrayList<>();
        JSONArray topicArray = object.optJSONArray("topics");
        if (topicArray != null) {
            for (int i = 0; i < topicArray.length(); i++) {
                JSONObject topic = topicArray.optJSONObject(i);
                if (topic == null) {
                    continue;
                }
                String title = topic.optString("title", "").trim();
                if (title.isEmpty()) {
                    continue;
                }
                double seconds = topic.has("start_seconds")
                        ? topic.optDouble("start_seconds", -1)
                        : topic.optDouble("start", -1);
                if (seconds >= 0) {
                    topics.add(new EpisodeSummary.Topic(title, Math.max(0, Math.round(seconds * 1000))));
                }
            }
        }
        topics.sort((first, second) -> Long.compare(first.getStartMs(), second.getStartMs()));
        return new SummaryData(text, topics);
    }

    private static void synthesize(@NonNull Context context, @NonNull String text, @NonNull File output)
            throws InterruptedException, IOException {
        CountDownLatch initialized = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        final int[] result = {TextToSpeech.ERROR};
        TextToSpeech tts = new TextToSpeech(context, status -> {
            result[0] = status;
            initialized.countDown();
        });
        if (!initialized.await(30, TimeUnit.SECONDS) || result[0] != TextToSpeech.SUCCESS) {
            tts.shutdown();
            throw new IOException("Text-to-speech initialization failed");
        }
        tts.setLanguage(Locale.getDefault());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                completed.countDown();
            }

            @Override
            public void onError(String utteranceId) {
                completed.countDown();
            }
        });
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "episode-summary");
        int status = tts.synthesizeToFile(text, params, output, "episode-summary");
        if (status != TextToSpeech.SUCCESS || !completed.await(2, TimeUnit.MINUTES)) {
            tts.shutdown();
            throw new IOException("Text-to-speech synthesis failed");
        }
        tts.shutdown();
    }

    public static final class SummaryData {
        public final String text;
        public final List<EpisodeSummary.Topic> topics;

        SummaryData(@NonNull String text, @NonNull List<EpisodeSummary.Topic> topics) {
            this.text = text;
            this.topics = topics;
        }
    }
}
