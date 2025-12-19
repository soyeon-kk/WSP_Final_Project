package com.example.photoviewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvTime, tvStatusBadge, tvDesc, tvQueueCount, tvSeatCount, tvTip;
    private ImageView heroImage;
    private ProgressBar progressSeats;
    private Button btnHistory;

    private final Handler handler = new Handler();
    private Runnable autoRefreshTask;

    private int lastCount = 0;

    // ✅ PythonAnywhere
    private static final String BASE_URL   = "https://soyeonkk.pythonanywhere.com";
    private static final String SERVER_URL = BASE_URL + "/api_root/Post/";

    private static final DateTimeFormatterKOR KOR_TIME = new DateTimeFormatterKOR();

    private int parseInt(String text, String regex) {
        if (text == null) return -1;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find() && m.group(1) != null) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    private ParsedStats parseStatsFromText(String text) {
        int totalSeats  = parseInt(text, "총 좌석 수:\\s*(\\d+)석");
        int seated      = parseInt(text, "착석 인원:\\s*(\\d+)명");
        int queue       = parseInt(text, "대기열 인원.*?:\\s*(\\d+)명");
        int remainSeats = parseInt(text, "남은 좌석:\\s*(\\d+)석");

        return new ParsedStats(totalSeats, seated, queue, remainSeats);
    }

    private static class ParsedStats {
        final int totalSeats;
        final int seated;
        final int queue;
        final int remainSeats;

        ParsedStats(int totalSeats, int seated, int queue, int remainSeats) {
            this.totalSeats = totalSeats;
            this.seated = seated;
            this.queue = queue;
            this.remainSeats = remainSeats;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTime = findViewById(R.id.tvTime);

        heroImage = findViewById(R.id.heroImage);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvDesc = findViewById(R.id.tvDesc);

        tvQueueCount = findViewById(R.id.tvQueueCount);
        tvSeatCount = findViewById(R.id.tvSeatCount);
        progressSeats = findViewById(R.id.progressSeats);

        tvTip = findViewById(R.id.tvTip);
        btnHistory = findViewById(R.id.btnHistory);

        btnHistory.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistoryActivity.class))
        );

        loadAndRender();
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        autoRefreshTask = new Runnable() {
            @Override
            public void run() {
                loadAndRender();
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(autoRefreshTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRefreshTask != null) handler.removeCallbacks(autoRefreshTask);
    }

    private void loadAndRender() {
        new LoadPostsTask().execute(SERVER_URL);
    }

    private void triggerAlert() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP);
        } catch (Exception ignored) {}

        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("새 이벤트 감지!")
                .setMessage("서버에서 새로운 데이터가 감지되었습니다.")
                .setPositiveButton("확인", null)
                .show());
    }

    private void renderDashboard(List<PostItem> posts) {
        if (posts == null || posts.isEmpty()) return;

        // 최신순 정렬 (epoch로)
        Collections.sort(posts, (a, b) -> Long.compare(b.getCreatedEpochMs(), a.getCreatedEpochMs()));

        PostItem latest = posts.get(0);
        setTimeFromLatest(latest);
        updateHeroFromLatest(latest);

        ParsedStats ps = parseStatsFromText(latest.getText());

        if (ps.totalSeats > 0 && ps.remainSeats >= 0) {
            tvQueueCount.setText(ps.queue + "명");
            tvSeatCount.setText(ps.remainSeats + " / " + ps.totalSeats + "석");

            int percent = (int) ((ps.remainSeats / (float) ps.totalSeats) * 100f);
            progressSeats.setProgress(clamp(percent, 0, 100));

            if (ps.queue > 0 || ps.remainSeats <= 0) {
                tvStatusBadge.setText("😵  혼잡");
                tvDesc.setText("많이 붐비는 상태입니다. 대기열이 발생했어요.");
            } else if (ps.remainSeats <= ps.totalSeats * 0.3) {
                tvStatusBadge.setText("🙂  보통");
                tvDesc.setText("적당히 붐비는 상태입니다.");
            } else {
                tvStatusBadge.setText("😊  여유");
                tvDesc.setText("여유로운 상태입니다.");
            }
        } else {
            tvQueueCount.setText("-");
            tvSeatCount.setText("-");
            progressSeats.setProgress(0);
            tvStatusBadge.setText("-");
            tvDesc.setText("");
        }

        tvTip.setText("점심시간 피크는 12:00-12:30, 저녁시간 피크는 18:00-18:30입니다.");
    }

    private void setTimeFromLatest(PostItem latest) {
        // ✅ API24 호환: PostItem이 문자열 만들어줌
        tvTime.setText(latest.getCreatedTimeKor());
    }

    private void updateHeroFromLatest(PostItem latest) {
        String imagePath = latest.getImageUrl();
        if (imagePath == null || imagePath.isEmpty()) return;

        String fullUrl = imagePath.startsWith("http") ? imagePath : BASE_URL + imagePath;
        new ImageLoadTask(heroImage).execute(fullUrl);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private class LoadPostsTask extends AsyncTask<String, Void, List<PostItem>> {
        @Override
        protected List<PostItem> doInBackground(String... urls) {
            return PostFetcher.fetchPosts(); // URL 파라미터 쓰지 않아도 됨
        }

        @Override
        protected void onPostExecute(List<PostItem> posts) {
            if (posts == null) return;

            boolean isNewPost = posts.size() > lastCount;
            lastCount = posts.size();

            renderDashboard(posts);

            if (isNewPost) triggerAlert();
        }
    }

    public static class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView targetView;

        public ImageLoadTask(ImageView targetView) {
            this.targetView = targetView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String urlStr = urls[0];
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoInput(true);
                conn.connect();
                InputStream is = conn.getInputStream();
                return BitmapFactory.decodeStream(is);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && targetView != null) {
                targetView.setImageBitmap(bitmap);
            }
        }
    }

    // (API24용) 간단 포맷터 래퍼
    private static class DateTimeFormatterKOR {
        String format(String iso) {
            // ISO "2025-12-18T19:11:09+09:00" or "2025-12-18T19:11:09"
            if (iso == null || iso.length() < 16) return "";
            try {
                String t = iso.replace("T", " ");
                // "2025-12-18 19:11:09"
                String hhmm = t.substring(11, 16);
                int hh = Integer.parseInt(hhmm.substring(0, 2));
                String ampm = (hh < 12) ? "오전" : "오후";
                int hh12 = hh % 12;
                if (hh12 == 0) hh12 = 12;
                String mm = hhmm.substring(3, 5);
                return String.format(Locale.KOREA, "%s %02d:%s", ampm, hh12, mm);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
