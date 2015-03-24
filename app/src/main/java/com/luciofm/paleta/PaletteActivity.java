package com.luciofm.paleta;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class PaletteActivity extends ActionBarActivity {

    @InjectView(R.id.pager)
    ViewPager pager;
    @InjectView(R.id.toolbar_actionbar)
    Toolbar toolbar;
    int toolbarBG = Color.BLACK;
    int titleColor = Color.WHITE;
    ObjectAnimator bgAnim;
    ObjectAnimator titleAnim;

    PaletteAdapter adapter;
    List<Uri> images;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_palette);

        ButterKnife.inject(this);

        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            List<Uri> images = new ArrayList<>();
            images.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            setupAdapter(images);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null && type.startsWith("image/")) {
            setupAdapter(intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
        } else {
            Toast.makeText(this, "Invalid image type...", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    public void setupAdapter(List<Uri> images) {
        this.images = images;
        adapter = new PaletteAdapter(getSupportFragmentManager(), images);
        pager.setAdapter(adapter);
    }

    public void setupToolbar(Palette palette) {
        Palette.Swatch swatch = palette.getDarkMutedSwatch();
        if (swatch == null)
            swatch = palette.getSwatches().get(0);
        if (swatch == null)
            return;

        if (titleAnim != null)
            titleAnim.cancel();
        if (bgAnim != null)
            bgAnim.cancel();

        bgAnim = ObjectAnimator.ofObject(toolbar, "backgroundColor", new ArgbEvaluator(), toolbarBG, swatch.getRgb());
        titleAnim = ObjectAnimator.ofObject(toolbar, "titleTextColor", new ArgbEvaluator(), titleColor, swatch.getBodyTextColor());

        toolbarBG = swatch.getRgb();
        titleColor = swatch.getBodyTextColor();
        bgAnim.start();
        titleAnim.start();
    }

    public class PaletteAdapter extends FragmentPagerAdapter {

        final List<Uri> images;

        public PaletteAdapter(FragmentManager fm, List<Uri> images) {
            super(fm);
            this.images = images;
        }

        @Override
        public Fragment getItem(int position) {
            return PaletteFragment.newInstance(images.get(position));
        }

        @Override
        public int getCount() {
            return images.size();
        }
    }
}
