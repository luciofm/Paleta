package com.luciofm.paleta;


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.luciofm.paleta.util.SpacesItemDecoration;
import com.luciofm.paleta.util.Utils;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestCreator;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class PaletteFragment extends Fragment {
    private static final String ARG_URI = "URI";

    @InjectView(R.id.list)
    RecyclerView list;
    @InjectView(R.id.progressBar)
    ProgressBar progress;

    Subscription sub;
    private Uri uri;
    private Palette palette;

    public static Fragment newInstance(Uri uri) {
        PaletteFragment fragment = new PaletteFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    public PaletteFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            uri = getArguments().getParcelable(ARG_URI);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_palette, container, false);
        ButterKnife.inject(this, view);

        list.setHasFixedSize(true);
        GridLayoutManager manager = new GridLayoutManager(getActivity(), 2);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? manager.getSpanCount() : 1;
            }
        });
        list.setLayoutManager(manager);
        list.addItemDecoration(new SpacesItemDecoration(5, 2));

        sub = loadBitmap(uri).flatMap(this::generatePalette).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showPalette);

        return view;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.reset(this);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.send && palette != null)
            sendPalette();
        return super.onOptionsItemSelected(item);
    }

    private void sendPalette() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto","", null));
        emailIntent.putExtra(Intent.EXTRA_TEXT, buildPaletteXml());
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Palette colors.xml");
        startActivity(Intent.createChooser(emailIntent, "Send Palette"));
    }

    private String buildPaletteXml() {
        StringBuilder builder = new StringBuilder();
        int pos = 1;
        for (Palette.Swatch swatch : palette.getSwatches()) {
            String name = getSwatchName(swatch);
            if (name == null)
                name = String.format("swatch_%02d", pos++);

            builder.append("<color name=\"").append(name).append("_rgb\">#").append(Integer.toHexString(swatch.getRgb())).append("</color>\n");
            builder.append("<color name=\"").append(name).append("_title\">#").append(Integer.toHexString(swatch.getTitleTextColor())).append("</color>\n");
            builder.append("<color name=\"").append(name).append("_body\">#").append(Integer.toHexString(swatch.getBodyTextColor())).append("</color>\n");
            builder.append("\n\n");
        }

        return builder.toString();
    }

    private void showPalette(Palette palette) {
        this.palette = palette;
        list.setAdapter(new SwatchAdapter(getActivity(), palette, uri));
        if (getUserVisibleHint())
            ((PaletteActivity) getActivity()).setupToolbar(palette);

        progress.setVisibility(View.GONE);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser && palette != null)
            ((PaletteActivity) getActivity()).setupToolbar(palette);
    }

    public class SwatchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final int width;
        private final int height;

        public class ViewHolder extends RecyclerView.ViewHolder {

            @InjectView(R.id.name)
            TextView name;
            @InjectView(R.id.background)
            TextView background;
            @InjectView(R.id.title)
            TextView title;
            @InjectView(R.id.body)
            TextView subtitle;

            public ViewHolder(View view) {
                super(view);
                ButterKnife.inject(this, view);
            }
        }

        private static final int ITEM_VIEW_TYPE_HEADER = 0;
        private static final int ITEM_VIEW_TYPE_SWATCH = 1;

        Context context;
        Palette palette;
        Uri imageUri;

        public SwatchAdapter(Context context, Palette palette, Uri imageUri) {
            this.context = context;
            this.imageUri = imageUri;
            this.palette = palette;

            width = Utils.getScreenWidth(context);
            height = context.getResources().getDimensionPixelSize(R.dimen.header_height);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_VIEW_TYPE_HEADER) {
                return new ImageViewHolder(new ImageView(context));
            }

            View view = LayoutInflater.from(context).inflate(R.layout.swatch_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder hold, int position) {
            if (isHeader(position)) {
                RequestCreator request = Picasso.with(getActivity()).load(imageUri)
                        .resize(width, height);

                Palette.Swatch swatch = getDefaultSwatch(palette);
                if (swatch != null)
                    request.placeholder(new ColorDrawable(swatch.getRgb()));
                request.centerInside().into(((ImageView) ((ImageViewHolder) hold).itemView));

                return;
            }

            ViewHolder holder = (ViewHolder) hold;
            Palette.Swatch swatch = palette.getSwatches().get(position - 1);

            holder.itemView.setBackgroundColor(swatch.getRgb());

            String name = getSwatchName(swatch);
            if (name != null) {
                holder.name.setVisibility(View.VISIBLE);
                holder.name.setText(name);
                holder.name.setTextColor(swatch.getTitleTextColor());
            } else
                holder.name.setVisibility(View.GONE);

            holder.background.setTextColor(swatch.getTitleTextColor());
            holder.background.setText("Background: 0x" + Integer.toHexString(swatch.getRgb()));

            holder.title.setTextColor(swatch.getTitleTextColor());
            holder.title.setText("Title: 0x" + Integer.toHexString(swatch.getTitleTextColor()));

            holder.subtitle.setTextColor(swatch.getBodyTextColor());
            holder.subtitle.setText("Body: 0x" + Integer.toHexString(swatch.getBodyTextColor()));
        }

        private String getSwatchName(Palette.Swatch swatch) {
            if (swatch.equals(palette.getDarkMutedSwatch()))
                return "Dark Muted";
            else if (swatch.equals(palette.getDarkVibrantSwatch()))
                return "Dark Vibrant";
            else if (swatch.equals(palette.getLightMutedSwatch()))
                return "Light Muted";
            else if (swatch.equals(palette.getLightVibrantSwatch()))
                return "Light Vibrant";
            else if (swatch.equals(palette.getMutedSwatch()))
                return "Muted";
            else if (swatch.equals(palette.getVibrantSwatch()))
                return "Vibrant";
            return null;
        }

        public boolean isHeader(int position) {
            return position == 0;
        }

        @Override
        public int getItemViewType(int position) {
            return isHeader(position) ? ITEM_VIEW_TYPE_HEADER : ITEM_VIEW_TYPE_SWATCH;
        }

        @Override
        public int getItemCount() {
            return palette.getSwatches().size() + 1;
        }
    }


    public class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageViewHolder(View itemView) {
            super(itemView);
            RecyclerView.LayoutParams params = new GridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.header_height));
            itemView.setLayoutParams(params);
            ((ImageView) itemView).setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
    }

    private String getSwatchName(Palette.Swatch swatch) {
        if (swatch.equals(palette.getDarkMutedSwatch()))
            return "dark_muted";
        else if (swatch.equals(palette.getDarkVibrantSwatch()))
            return "dark_vibrant";
        else if (swatch.equals(palette.getLightMutedSwatch()))
            return "light_muted";
        else if (swatch.equals(palette.getLightVibrantSwatch()))
            return "light_vibrant";
        else if (swatch.equals(palette.getMutedSwatch()))
            return "muted";
        else if (swatch.equals(palette.getVibrantSwatch()))
            return "vibrant";
        return null;
    }


    Observable<Bitmap> loadBitmap(final Uri source) {
        return Observable.create(new Observable.OnSubscribe<Bitmap>() {
            @Override
            public void call(Subscriber<? super Bitmap> subscriber) {
                try {
                    subscriber.onNext(Picasso.with(getActivity()).load(source).get());
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    Observable<Palette> generatePalette(final Bitmap image) {
        return Observable.create(new Observable.OnSubscribe<Palette>() {
            @Override
            public void call(Subscriber<? super Palette> subscriber) {
                subscriber.onNext(Palette.generate(image));
                subscriber.onCompleted();
            }
        });
    }

    public Palette.Swatch getDefaultSwatch(Palette palette) {
        Palette.Swatch swatch = palette.getDarkMutedSwatch();
        if (swatch == null)
            swatch = palette.getSwatches().get(0);

        return swatch;
    }
}
