package com.example.photoviewer;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PostFetcher {

    private static final String BASE_URL   = "https://soyeonkk.pythonanywhere.com";
    private static final String SERVER_URL = BASE_URL + "/api_root/Post/";
    private static final String TAG = "PostFetcher";

    public static List<PostItem> fetchPosts() {
        List<PostItem> list = new ArrayList<>();

        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(SERVER_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {

                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder jsonBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }

                String raw = jsonBuilder.toString().trim();

                // ✅ 1) 배열로 오는 경우: [...]
                // ✅ 2) 페이지네이션: {"count":..,"results":[...]}
                JSONArray array;
                if (raw.startsWith("[")) {
                    array = new JSONArray(raw);
                } else {
                    JSONObject root = new JSONObject(raw);
                    array = root.optJSONArray("results");
                    if (array == null) array = new JSONArray(); // 방어
                }

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    int id = obj.optInt("id", 0);
                    String title = obj.optString("title", "제목 없음");
                    String text = obj.optString("text", "");
                    String created = obj.optString("created_date", "");
                    String published = obj.optString("published_date", "");
                    String image = obj.optString("image", "");
                    String author = obj.optString("author", "익명");

                    list.add(new PostItem(id, title, text, created, published, image, author));
                }

            } else {
                Log.e(TAG, "서버 응답 코드 : " + code);
            }

        } catch (Exception e) {
            Log.e(TAG, "POST 불러오기 실패 : " + e.getMessage(), e);
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }

        return list;
    }
}
