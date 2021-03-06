package rouchuan.viewpagerlayoutmanager.circle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.leochuan.CenterScrollListener;
import com.leochuan.CircleLayoutManager;

import java.util.Locale;

import rouchuan.viewpagerlayoutmanager.R;
import rouchuan.viewpagerlayoutmanager.SettingPopUpWindow;
import rouchuan.viewpagerlayoutmanager.Util;

/**
 * Created by Dajavu on 25/10/2017.
 */

@SuppressLint("InflateParams")
@SuppressWarnings("FieldCanBeLocal")
class CirclePopUpWindow extends SettingPopUpWindow
        implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {

    private CircleLayoutManager circleLayoutManager;
    private RecyclerView recyclerView;
    private TextView radiusValue;
    private TextView intervalValue;
    private TextView speedValue;
    private SwitchCompat centerInFront;
    private SwitchCompat infinite;
    private SwitchCompat scrollBack;
    private SwitchCompat reverse;
    private CenterScrollListener scrollListener;

    CirclePopUpWindow(Context context, CircleLayoutManager circleLayoutManager, RecyclerView recyclerView) {
        super(context);
        this.circleLayoutManager = circleLayoutManager;
        this.recyclerView = recyclerView;
        setOutsideTouchable(true);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_circle_setting, null);
        setWidth(Util.Dp2px(context, 400));
        setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        setContentView(view);

        scrollListener = new CenterScrollListener();

        SeekBar radius = (SeekBar) view.findViewById(R.id.sb_radius);
        SeekBar interval = (SeekBar) view.findViewById(R.id.sb_interval);
        SeekBar speed = (SeekBar) view.findViewById(R.id.sb_speed);

        radiusValue = (TextView) view.findViewById(R.id.radius_value);
        intervalValue = (TextView) view.findViewById(R.id.interval_value);
        speedValue = (TextView) view.findViewById(R.id.speed_value);

        centerInFront = (SwitchCompat) view.findViewById(R.id.s_center_in_front);
        infinite = (SwitchCompat) view.findViewById(R.id.s_infinite);
        scrollBack = (SwitchCompat) view.findViewById(R.id.s_scroll_back);
        reverse = (SwitchCompat) view.findViewById(R.id.s_reverse);

        radius.setOnSeekBarChangeListener(this);
        interval.setOnSeekBarChangeListener(this);
        speed.setOnSeekBarChangeListener(this);

        final int maxRadius = Util.Dp2px(radius.getContext(), 400);
        radius.setProgress((int) (circleLayoutManager.getRadius() * 1f / maxRadius * 100));
        interval.setProgress((int) (circleLayoutManager.getAngleInterval() / 0.9f));
        speed.setProgress((int) (circleLayoutManager.getRotateSpeed() / 0.005f));

        radiusValue.setText(String.valueOf(circleLayoutManager.getRadius()));
        intervalValue.setText(String.valueOf(circleLayoutManager.getAngleInterval()));
        speedValue.setText(String.format(Locale.getDefault(), "%.3f", circleLayoutManager.getRotateSpeed()));

        centerInFront.setChecked(circleLayoutManager.getEnableBringCenterToFront());
        infinite.setChecked(circleLayoutManager.getInfinite());
        reverse.setChecked(circleLayoutManager.getReverseLayout());

        centerInFront.setOnCheckedChangeListener(this);
        infinite.setOnCheckedChangeListener(this);
        scrollBack.setOnCheckedChangeListener(this);
        reverse.setOnCheckedChangeListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        switch (seekBar.getId()) {
            case R.id.sb_radius:
                final int maxRadius = Util.Dp2px(seekBar.getContext(), 400);
                final int radius = (int) (progress / 100f * maxRadius);
                circleLayoutManager.setRadius(radius);
                radiusValue.setText(String.valueOf(radius));
                break;
            case R.id.sb_interval:
                final int interval = (int) (progress * 0.9f);
                circleLayoutManager.setAngleInterval(interval);
                intervalValue.setText(String.valueOf(interval));
                break;
            case R.id.sb_speed:
                final float speed = progress * 0.005f;
                circleLayoutManager.setRotateSpeed(speed);
                speedValue.setText(String.format(Locale.getDefault(), "%.3f", speed));
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.s_infinite:
                recyclerView.scrollToPosition(0);
                circleLayoutManager.setInfinite(isChecked);
                break;
            case R.id.s_scroll_back:
                if (isChecked) {
                    recyclerView.addOnScrollListener(scrollListener);
                } else {
                    recyclerView.removeOnScrollListener(scrollListener);
                }
                break;
            case R.id.s_center_in_front:
                circleLayoutManager.setEnableBringCenterToFront(isChecked);
                break;
            case R.id.s_reverse:
                circleLayoutManager.scrollToPosition(0);
                circleLayoutManager.setReverseLayout(isChecked);
        }
    }
}
