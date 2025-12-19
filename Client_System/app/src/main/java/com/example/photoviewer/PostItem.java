package com.example.photoviewer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PostItem {
    private int id;
    private String title;
    private String text;
    private String created_date;
    private String published_date;
    private String image;
    private String author;

    public PostItem(int id, String title, String text,
                    String created_date, String published_date,
                    String image, String author) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.created_date = created_date;
        this.published_date = published_date;
        this.image = image;
        this.author = author;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public String getCreatedDateRaw() { return created_date; }
    public String getPublishedDateRaw() { return published_date; }

    public String getDate() {
        return (published_date != null && !published_date.isEmpty())
                ? published_date
                : created_date;
    }

    public String getImageUrl() { return image; }
    public String getAuthor() { return author; }

    // ✅ 정렬용 epoch(ms)
    public long getCreatedEpochMs() {
        return parseIsoToEpoch(created_date);
    }

    // ✅ 화면 표시용 "오전/오후 hh:mm"
    public String getCreatedTimeKor() {
        return formatIsoToKorTime(created_date);
    }

    private long parseIsoToEpoch(String iso) {
        // 예: 2025-12-18T19:11:09.123456+09:00
        if (iso == null || iso.length() < 19) return 0L;
        try {
            String s = iso;

            // 밀리초/마이크로초 제거
            int dot = s.indexOf('.');
            if (dot != -1) s = s.substring(0, dot);

            // timezone 제거 (+09:00 등)
            int plus = s.indexOf('+', 19);
            if (plus != -1) s = s.substring(0, plus);
            int z = s.indexOf('Z', 19);
            if (z != -1) s = s.substring(0, z);

            s = s.replace('T', ' '); // "yyyy-MM-dd HH:mm:ss"

            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            // 서버 시간대가 +09:00이면 굳이 안 맞춰도 되지만, 안정적으로 KST로
            f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            Date d = f.parse(s);
            return d != null ? d.getTime() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String formatIsoToKorTime(String iso) {
        long ms = parseIsoToEpoch(iso);
        if (ms == 0L) return "";
        try {
            SimpleDateFormat out = new SimpleDateFormat("a hh:mm", Locale.KOREA);
            out.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            return out.format(new Date(ms));
        } catch (Exception e) {
            return "";
        }
    }
}
