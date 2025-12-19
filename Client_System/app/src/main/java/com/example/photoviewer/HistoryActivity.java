package com.example.photoviewer;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ImageButton btnClose;

    private final List<PostItem> items = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recycler = findViewById(R.id.recyclerHistory);
        btnClose = findViewById(R.id.btnClose);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, items);
        recycler.setAdapter(adapter);

        btnClose.setOnClickListener(v -> finish());

        loadHistory();
    }

    private void loadHistory() {
        new AsyncTask<Void, Void, List<PostItem>>() {
            @Override
            protected List<PostItem> doInBackground(Void... voids) {
                return PostFetcher.fetchPosts();
            }

            @Override
            protected void onPostExecute(List<PostItem> posts) {
                if (posts == null) return;

                items.clear();
                items.addAll(posts);

                // 최신순 정렬
                Collections.sort(items, (a, b) -> Long.compare(b.getCreatedEpochMs(), a.getCreatedEpochMs()));

                adapter.notifyDataSetChanged();
            }
        }.execute();
    }
}
