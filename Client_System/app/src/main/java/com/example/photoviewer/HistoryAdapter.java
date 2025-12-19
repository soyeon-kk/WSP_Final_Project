package com.example.photoviewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private final Context ctx;
    private final List<PostItem> list;

    // ✅ PythonAnywhere
    private static final String BASE_URL = "https://soyeonkk.pythonanywhere.com";

    public HistoryAdapter(Context ctx, List<PostItem> list) {
        this.ctx = ctx;
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PostItem item = list.get(position);

        h.tvNewest.setVisibility(position == 0 ? View.VISIBLE : View.GONE);

        // ✅ API24 호환: 문자열로 표시 (PostItem에서 포맷 만들어줌)
        h.tvTime.setText(item.getCreatedTimeKor());

        ParsedStats ps = parseStatsFromText(item.getText());

        if (ps.totalSeats > 0 && ps.remainSeats >= 0) {
            h.tvQueue.setText("대기열 " + ps.queue + "명");
            h.tvSeats.setText("남은좌석 " + ps.remainSeats + "/" + ps.totalSeats);
            h.tvBadge.setText(makeBadge(ps));
        } else {
            h.tvQueue.setText("대기열 -");
            h.tvSeats.setText("남은좌석 -/-");
            h.tvBadge.setText("-");
        }

        String imagePath = item.getImageUrl();
        if (imagePath != null && !imagePath.isEmpty()) {
            String full = imagePath.startsWith("http") ? imagePath : BASE_URL + imagePath;
            new MainActivity.ImageLoadTask(h.img).execute(full);
        } else {
            h.img.setImageDrawable(null);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private String makeBadge(ParsedStats ps) {
        if (ps.queue > 0 || ps.remainSeats <= 0) return "😵  혼잡";
        if (ps.remainSeats <= ps.totalSeats * 0.3) return "🙂  보통";
        return "😊  여유";
    }

    private int parseInt(String text, String regex) {
        if (text == null) return -1;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find() && m.group(1) != null) return Integer.parseInt(m.group(1));
        return -1;
    }

    private ParsedStats parseStatsFromText(String text) {
        int totalSeats  = parseInt(text, "총 좌석 수:\\s*(\\d+)석");
        int seated      = parseInt(text, "착석 인원:\\s*(\\d+)명");
        int queue       = parseInt(text, "대기열 인원.*?:\\s*(\\d+)명");
        int remainSeats = parseInt(text, "남은 좌석:\\s*(\\d+)석");
        return new ParsedStats(totalSeats, seated, queue, remainSeats);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView tvBadge, tvTime, tvNewest, tvQueue, tvSeats;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.img);
            tvBadge = itemView.findViewById(R.id.tvBadge);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvNewest = itemView.findViewById(R.id.tvNewest);
            tvQueue = itemView.findViewById(R.id.tvQueue);
            tvSeats = itemView.findViewById(R.id.tvSeats);
        }
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
}
