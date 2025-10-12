package com.ess.anime.wallpaper.ui.activity;

import android.os.Bundle;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerReverseSearchWebsiteAdapter;
import com.ess.anime.wallpaper.model.entity.ReverseSearchWebsiteItem;
import com.ess.anime.wallpaper.model.helper.ReverseSearchWebsiteDataHelper;

import java.util.List;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ReverseSearchActivity extends BaseActivity {

    @Override
    protected int layoutRes() {
        return R.layout.activity_reverse_search;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        initToolBarLayout();
        initRecyclerWebsite();
    }

    private void initToolBarLayout() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initRecyclerWebsite() {
        RecyclerView rvWebsite = findViewById(R.id.rv_website);
        rvWebsite.setLayoutManager(new LinearLayoutManager(this));
        rvWebsite.setAdapter(new RecyclerReverseSearchWebsiteAdapter(getWebsiteItemList()));
    }

    private List<ReverseSearchWebsiteItem> getWebsiteItemList() {
        return ReverseSearchWebsiteDataHelper.getWebsiteItemList(this);
    }

}
