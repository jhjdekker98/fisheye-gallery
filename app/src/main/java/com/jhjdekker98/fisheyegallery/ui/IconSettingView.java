package com.jhjdekker98.fisheyegallery.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import com.jhjdekker98.fisheyegallery.R;

public class IconSettingView extends LinearLayout {
    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;

    public IconSettingView(Context context) {
        this(context, null);
    }

    public IconSettingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconSettingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.view_icon_setting, this, true);
        iconView = findViewById(R.id.icon);
        titleView = findViewById(R.id.title);
        subtitleView = findViewById(R.id.subtitle);

        if (attrs == null) {
            return;
        }
        final TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.ViewIconSetting);
        final Drawable icon = arr.getDrawable(R.styleable.ViewIconSetting_icon);
        final CharSequence title = arr.getText(R.styleable.ViewIconSetting_titleText);
        final CharSequence subtitle = arr.getText(R.styleable.ViewIconSetting_subtitleText);

        if (icon != null) {
            iconView.setImageDrawable(icon);
        }
        titleView.setText(title == null ? "" : title);
        subtitleView.setText(title == null ? "" : subtitle);

        arr.recycle();
    }

    public void setIcon(Drawable drawable) {
        iconView.setImageDrawable(drawable);
    }

    public void setIcon(@DrawableRes int resId) {
        iconView.setImageResource(resId);
    }

    public void setIconSize(int width, int height) {
        final ViewGroup.LayoutParams lp = iconView.getLayoutParams();
        lp.width = width;
        lp.height = height;
        iconView.setLayoutParams(lp);
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public void setSubtitle(String subtitle) {
        subtitleView.setText(subtitle);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) {
        findViewById(R.id.rootLayout).setOnClickListener(listener);
    }
}
