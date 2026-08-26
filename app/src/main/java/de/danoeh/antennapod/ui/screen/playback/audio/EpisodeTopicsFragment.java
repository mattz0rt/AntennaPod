package de.danoeh.antennapod.ui.screen.playback.audio;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.model.feed.EpisodeSummary;
import de.danoeh.antennapod.model.feed.EpisodeTopic;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class EpisodeTopicsFragment extends Fragment {
    private static final String TAG = "EpisodeTopicsFragment";
    private Disposable disposable;
    private TextView summaryTitle;
    private TextView summaryText;
    private TextView topicsTitle;
    private LinearLayout topicsView;
    private TextView emptyMessage;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.episode_topics_fragment, container, false);
        summaryTitle = view.findViewById(R.id.summaryTitle);
        summaryText = view.findViewById(R.id.summaryText);
        topicsTitle = view.findViewById(R.id.topicsTitle);
        topicsView = view.findViewById(R.id.topics);
        emptyMessage = view.findViewById(R.id.emptyMessage);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadSummary();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        summaryTitle = null;
        summaryText = null;
        topicsTitle = null;
        topicsView = null;
        emptyMessage = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusEvent(PlayerStatusEvent event) {
        loadSummary();
    }

    private void loadSummary() {
        if (disposable != null) {
            disposable.dispose();
        }
        Context context = requireContext().getApplicationContext();
        disposable = Maybe.<SummaryContent>create(emitter -> {
            FeedMedia media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            EpisodeSummary summary = media == null ? null : DBReader.getEpisodeSummary(media.getId());
            List<MediaItem> topicItems = new ArrayList<>();
            if (media != null && summary != null) {
                for (EpisodeTopic topic : summary.getTopics()) {
                    topicItems.add(MediaItemAdapter.fromEpisodeTopic(context, media, topic));
                }
            }
            emitter.onSuccess(new SummaryContent(summary, topicItems));
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::displaySummary, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void displaySummary(SummaryContent content) {
        if (summaryText == null) {
            return;
        }
        EpisodeSummary summary = content.summary;
        boolean available = summary != null;
        summaryTitle.setVisibility(available ? View.VISIBLE : View.GONE);
        summaryText.setVisibility(available ? View.VISIBLE : View.GONE);
        emptyMessage.setVisibility(available ? View.GONE : View.VISIBLE);
        topicsView.removeAllViews();
        if (!available) {
            topicsTitle.setVisibility(View.GONE);
            return;
        }
        summaryText.setText(TextUtils.isEmpty(summary.getText()) ? "" : summary.getText());
        topicsTitle.setVisibility(summary.getTopics().isEmpty() ? View.GONE : View.VISIBLE);
        topicsView.setVisibility(summary.getTopics().isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < summary.getTopics().size(); i++) {
            EpisodeTopic topic = summary.getTopics().get(i);
            MediaItem topicItem = content.topicItems.get(i);
            View topicView = inflater.inflate(R.layout.episode_topic_item, topicsView, false);
            ((TextView) topicView.findViewById(R.id.topicTitle)).setText(topic.getTitle());
            ((TextView) topicView.findViewById(R.id.topicTime)).setText(
                    Converter.getDurationStringLong((int) topic.getStart()) + " – "
                            + Converter.getDurationStringLong((int) topic.getEnd()));
            topicView.setContentDescription(getString(R.string.play_episode_topic, topic.getTitle()));
            topicView.setOnClickListener(view -> playTopic(topicItem));
            topicsView.addView(topicView);
        }
    }

    private void playTopic(MediaItem topic) {
        PlaybackController.bindToMedia3Service(requireContext(), controller -> {
            controller.setMediaItem(topic);
            controller.prepare();
            controller.play();
        });
    }

    private static class SummaryContent {
        final EpisodeSummary summary;
        final List<MediaItem> topicItems;

        SummaryContent(EpisodeSummary summary, List<MediaItem> topicItems) {
            this.summary = summary;
            this.topicItems = topicItems;
        }
    }
}
