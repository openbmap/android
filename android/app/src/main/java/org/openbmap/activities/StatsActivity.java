/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openbmap.activities;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.openbmap.R;
import org.openbmap.RadioBeacon;
import org.openbmap.db.models.CellRecord;
import org.openbmap.db.models.Session;
import org.openbmap.events.onCellUpdated;
import org.openbmap.events.onWifiAdded;

/**
 * Activity for displaying basic session infos (# of cells, wifis, etc.)
 */
public class StatsActivity extends Fragment {

    private static final String TAG = StatsActivity.class.getSimpleName();

    /**
     * Fade message after which time (in millis)
     */
    private static final long FADE_TIME = 10 * 1000;

    /**
     * Display periodic messages how often (in millis)
     */
    private final static int REFRESH_INTERVAL = 1000 * 2;

    /**
     * UI controls
     */
    private TextView tvCellDescription;
    private TextView tvCellStrength;
    private TextView tvWifiDescription;
    private TextView tvWifiStrength;
    private TextView tvTechnology;
    private TextView tvIgnored;
    private TextView tvFree;
    private ImageView ivFree;
    private ImageView ivAlert;
    private GraphView gvGraph;
    private LineGraphSeries mMeasurements;
    private PointsGraphSeries highlight;

    private Session mSession;

    /**
     * Time of last new wifi in millis
     */
    private long mLastWifiUpdate;

    /**
     * Time of last new cell in millis
     */
    private long mLastCellUpdate;

    /**
     * Fades ignore messages after certain time
     */
    private Runnable mFadeIgnoreTask;
    private final Handler mFadeIgnoreHandler = new Handler();

    private Runnable mFadeFreeTask;
    private final Handler mFadeFreeHandler = new Handler();

    /**
     * Update certain infos at periodic intervals
     */
    private final Handler mRefreshHandler = new Handler();
    private Runnable mPeriodicRefreshTask;

    private int mCurrentLevel;

    private String mLastTechnology;

    private double graph2LastXValue;

    /**
     * Receives cell / wifi news
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(final Context context, final Intent intent) {

            // handle strange null values on some 4.4 devices
            if (intent == null) {
                Log.wtf(TAG, "Intent is null");
                return;
            }

            if (tvCellDescription == null) {
                Log.wtf(TAG, "tvLastCell is null");
                return;
            }

            if (tvIgnored == null || ivAlert == null || tvFree == null || ivFree == null) {
                Log.wtf(TAG, "Some controls are null");
                return;
            }

            Log.d(TAG, "Received intent " + intent.getAction());

            // handling cell and wifi broadcasts
            if (RadioBeacon.INTENT_NEW_SESSION.equals(intent.getAction())) {
                final String id = intent.getStringExtra(RadioBeacon.MSG_KEY);
                // tbd
            } else if (RadioBeacon.INTENT_WIFI_BLACKLISTED.equals(intent.getAction())) {
                // let's display warning for 10 seconds
                mFadeIgnoreHandler.removeCallbacks(mFadeIgnoreTask);

                final String reason = intent.getStringExtra(RadioBeacon.MSG_KEY);
                String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
                String bssid = intent.getStringExtra(RadioBeacon.MSG_BSSID);

                // can be null, so set default values
                if (ssid == null) {
                    ssid = "";
                }
                if (bssid == null) {
                    bssid = "";
                }

                if (reason.equals(RadioBeacon.MSG_BSSID)) {
                    tvIgnored.setText(ssid + " (" + bssid + ") " + getResources().getString(R.string.blacklisted_bssid));
                } else if (reason.equals(RadioBeacon.MSG_SSID)) {
                    tvIgnored.setText(ssid + " (" + bssid + ") " + getResources().getString(R.string.blacklisted_ssid));
                } else if (reason.equals(RadioBeacon.MSG_LOCATION)) {
                    tvIgnored.setText(R.string.blacklisted_area);
                }
                tvIgnored.setVisibility(View.VISIBLE);
                ivAlert.setVisibility(View.VISIBLE);
                mFadeIgnoreHandler.postDelayed(mFadeIgnoreTask, FADE_TIME);
            } else if (RadioBeacon.INTENT_WIFI_FREE.equals(intent.getAction())) {
                mFadeFreeHandler.removeCallbacks(mFadeFreeTask);
                final String ssid = intent.getStringExtra(RadioBeacon.MSG_SSID);
                if (ssid != null) {
                    tvFree.setText(getResources().getString(R.string.free_wifi) + "\n" + ssid);
                }
                tvFree.setVisibility(View.VISIBLE);
                ivFree.setVisibility(View.VISIBLE);
                mFadeFreeHandler.postDelayed(mFadeFreeTask, FADE_TIME);
            }
        }
    };

    @Subscribe
    public void onEvent(onWifiAdded event) {
        final String wifiDescription = event.wifiDescription;
        final int wifiStrength = event.level;
        if (wifiDescription != null) {
            tvWifiDescription.setText(wifiDescription);
        } else {
            tvWifiDescription.setText(getString(R.string.n_a));
        }

        tvWifiStrength.setText(String.format("%d dBm", wifiStrength));
        mLastWifiUpdate = System.currentTimeMillis();
    }

    @Subscribe
    public void onEvent(final onCellUpdated event) {

        Log.d(TAG, "Cell update received, level" + event.level);

        mLastTechnology = event.technology;
        mCurrentLevel = event.level;
        mLastCellUpdate = System.currentTimeMillis();

        String description = event.operator;

        if (description.length() > 0) {
            description += "\n";
        }
        if (event.mcc != CellRecord.MCC_UNKNOWN && event.mnc != CellRecord.MNC_UNKNOWN) {
            // typical available information for GSM/LTE cells: MCC, MNC and area
            description += String.format("%s/%s/%s/%s", event.mcc, event.mnc, event.area);
        }
        if (event.sid != CellRecord.SYSTEM_ID_UNKNOWN && event.nid != CellRecord.NETWORK_ID_UNKOWN && event.bid != CellRecord.BASE_ID_UNKNOWN) {
            // typical available information for CDMA cells: system id, network id and base id
            description += String.format("%s/%s/%s", event.sid, event.nid, event.bid);
        }
        description += String.format("/%s", event.cell_id);

        tvCellDescription.setText(description);
        tvCellStrength.setText(String.format("%d dBm", mCurrentLevel));

        if (!event.technology.equals(mLastTechnology)) {
            final Animation in = new AlphaAnimation(0.0f, 1.0f);
            in.setDuration(2000);
            final Animation out = new AlphaAnimation(1.0f, 0.0f);
            out.setDuration(2000);
            out.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    tvTechnology.setText(event.technology);
                    tvTechnology.startAnimation(in);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            tvTechnology.startAnimation(out);
        }
    }

    @Override
    public final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFadeIgnoreTask = new Runnable() {
            @Override
            public void run() {
                tvIgnored.setVisibility(View.INVISIBLE);
                ivAlert.setVisibility(View.INVISIBLE);
            }
        };

        mFadeFreeTask = new Runnable() {
            @Override
            public void run() {
                tvFree.setVisibility(View.INVISIBLE);
                ivFree.setVisibility(View.INVISIBLE);
            }
        };

        mPeriodicRefreshTask = new Runnable() {
            @SuppressLint("DefaultLocale")
            @Override
            public void run() {
                updateTimeSinceUpdate();
                updateGraph();
                mRefreshHandler.postDelayed(mPeriodicRefreshTask, REFRESH_INTERVAL);
            }
        };

        // setup broadcast filters
        registerReceiver();
    }

    @Override
    public final void onResume() {
        super.onResume();

        registerReceiver();

        startRepeatingTask();
    }

    void startRepeatingTask() {
        mPeriodicRefreshTask.run();
    }

    void stopRepeatingTask() {
        mRefreshHandler.removeCallbacks(mPeriodicRefreshTask);
    }

    @Override
    public final void onPause() {
        unregisterReceiver();

        stopRepeatingTask();

        super.onPause();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.stats, container, false);
        // setup UI controls
        initUi(view);

        return view;
    }

    /**
     * Init UI contols
     */
    private void initUi(final View view) {
        tvCellDescription = (TextView) view.findViewById(R.id.stats_cell_description);
        tvCellStrength = (TextView) view.findViewById(R.id.stats_cell_strength);

        tvWifiDescription = (TextView) view.findViewById(R.id.stats_wifi_description);
        tvWifiStrength = (TextView) view.findViewById(R.id.stats_wifi_strength);

        tvIgnored = (TextView) view.findViewById(R.id.stats_blacklisted);
        tvFree = (TextView) view.findViewById(R.id.stats_free);
        ivFree = (ImageView) view.findViewById(R.id.stats_icon_free);
        ivAlert = (ImageView) view.findViewById(R.id.stats_icon_alert);

        tvTechnology = (TextView) view.findViewById(R.id.tvTechnology);

        gvGraph = (GraphView) view.findViewById(R.id.graph);
        gvGraph.getViewport().setXAxisBoundsManual(true);
        gvGraph.getViewport().setYAxisBoundsManual(true);
        gvGraph.getViewport().setMinY(-100);
        gvGraph.getViewport().setMaxY(-50);
        gvGraph.setTitle(this.getString(R.string.graph_title));
        gvGraph.getGridLabelRenderer().setVerticalAxisTitle(this.getString(R.string.dbm));
        gvGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        gvGraph.getGridLabelRenderer().setHighlightZeroLines(false);
        gvGraph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
        gvGraph.getGridLabelRenderer().setNumVerticalLabels(3);
        gvGraph.getViewport().setMinX(0);
        gvGraph.getViewport().setMaxX(60);
    }

    /**
     * Registers broadcast receivers.
     */
    private void registerReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(RadioBeacon.INTENT_SESSION_UPDATE);
        filter.addAction(RadioBeacon.INTENT_WIFI_BLACKLISTED);
        filter.addAction(RadioBeacon.INTENT_WIFI_FREE);
        getActivity().registerReceiver(mReceiver, filter);

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        } else {
            Log.i(TAG, "Event bus receiver already registered");
        }
    }

    /**
     * Unregisters receivers for GPS and wifi scan results.
     */
    private void unregisterReceiver() {
        try {
            getActivity().unregisterReceiver(mReceiver);
        } catch (final IllegalArgumentException e) {
            // do nothing here {@see
            // http://stackoverflow.com/questions/2682043/how-to-check-if-receiver-is-registered-in-android}
        }

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    /**
     * Displays time since last cell/wifi update
     */
    private void updateTimeSinceUpdate() {
        final String deltaCellString = "Last cell update " + getTimeSinceLastUpdate(mLastCellUpdate) + " ago";
        final String deltaWifiString = "Last cell update " + getTimeSinceLastUpdate(mLastWifiUpdate) + " ago";

        Log.d(TAG, deltaCellString);
        Log.d(TAG, deltaWifiString);
    }

    /**
     * Redraws cell strength graph
     */
    private void updateGraph() {
        //Log.d(TAG, "Updating graph, current level " + mCurrentLevel);
        if (mMeasurements == null) {
            //Log.i(TAG, "Adding new data series to chart");
            mMeasurements = new LineGraphSeries<>();
            gvGraph.addSeries(mMeasurements);
        }

        if (highlight == null) {
            highlight = new PointsGraphSeries<>();
            highlight.setColor(Color.argb(100, 255, 255, 0));
            gvGraph.addSeries(highlight);
        }

        if (mCurrentLevel != -1) {
            mMeasurements.appendData(new DataPoint(graph2LastXValue, mCurrentLevel), true, 60);
        } else {
            mMeasurements.appendData(new DataPoint(graph2LastXValue, -100d), true, 60);
        }

        if (mCurrentLevel > -60 && mCurrentLevel < -1) {
            highlight.appendData(new DataPoint(graph2LastXValue, mCurrentLevel), true, 60);
        } else {
            highlight.appendData(new DataPoint(graph2LastXValue, -105d), true, 60);
        }
        graph2LastXValue += 1d;
        mCurrentLevel = -1;
    }

    /**
     * Returns time since base value as human-readable string
     *
     * @return
     */
    private String getTimeSinceLastUpdate(final long base) {
        String deltaString = "";

        // no previous updates
        if (base == 0) {
            return deltaString;
        }

        final long delta = (System.currentTimeMillis() - base);

        if (delta < 60000) {
            deltaString = String.valueOf(delta / 1000) + getString(R.string.seconds);
        } else {
            deltaString = String.valueOf(delta / 60000) + getString(R.string.minutes);
        }
        return deltaString;
    }
}
