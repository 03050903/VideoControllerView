package com.bruce.videocontrollerview;

/**
 * Created by Brucetoo
 * On 2015/10/19
 * At 16:33
 */

import android.animation.LayoutTransition;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.Locale;

public class VideoControllerView extends FrameLayout {

    private static final String TAG = "VideoControllerView";

    private static final int HANDLER_ANIMATE_OUT = 1;// out animate
    private static final int HANDLER_UPDATE_PROGRESS = 2;//cycle update progress
    private MediaPlayerControl mPlayer;// control media play
    private Context mContext;
    private ViewGroup mAnchorView;//anchor view
    private View mRootView; // root view of this
    private SeekBar mSeekBar;
    private TextView mEndTime, mCurrentTime;
    private boolean mShowing;//controller view showing?
    private boolean mDragging;
    private StringBuilder mFormatBuilder;
    private Formatter mFormatter;
    //top layout
    private View mTopLayout;//this can custom animate layout
    private ImageButton mBackButton;
    private TextView mTitleText;
    //bottom layout
    private View mBottomLayout;
    private ImageButton mPauseButton;
    private ImageButton mFullscreenButton;
    private Handler mHandler = new ControllerViewHandler(this);

    public VideoControllerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRootView = null;
        mContext = context;
        Log.i(TAG, TAG);
    }

    public VideoControllerView(Context context, boolean useFastForward) {
        super(context);
        mContext = context;
        Log.i(TAG, TAG);
    }

    public VideoControllerView(Context context) {
        this(context, true);
        Log.i(TAG, TAG);
    }


    /**
     * Handler prevent leak memory.
     */
    private static class ControllerViewHandler extends Handler {
        private final WeakReference<VideoControllerView> mView;

        ControllerViewHandler(VideoControllerView view) {
            mView = new WeakReference<VideoControllerView>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoControllerView view = mView.get();
            if (view == null || view.mPlayer == null) {
                return;
            }

            int pos;
            switch (msg.what) {
                case HANDLER_ANIMATE_OUT:
                    view.hide();
                    break;
                case HANDLER_UPDATE_PROGRESS://cycle update seek bar progress
                    pos = view.setSeekProgress();
                    if (!view.mDragging && view.mShowing && view.mPlayer.isPlaying()) {//just in case
                        //cycle update
                        msg = obtainMessage(HANDLER_UPDATE_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
            }
        }
    }


    /**
     * setControlListener update play state
     * @param player self
     */
    public void setControlListener(MediaPlayerControl player) {
        mPlayer = player;
        togglePausePlay();
        toggleFullScreen();
    }

    /**
     * set anchor view
     * @param view view that hold controller view
     */
    public void setAnchorView(ViewGroup view) {
        mAnchorView = view;
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        //remove all before add view
        removeAllViews();
//        setBackgroundColor(Color.BLUE);
        View v = makeControllerView();
        addView(v, frameParams);
    }

    /**
     * init controller view
     * @return
     */
    protected View makeControllerView() {
        LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRootView = inflate.inflate(R.layout.media_controller, null);
        initControllerView(mRootView);

        return mRootView;
    }

    private void initControllerView(View v) {
        //top layout
        mTopLayout = v.findViewById(R.id.layout_top);
        mBackButton = (ImageButton) v.findViewById(R.id.top_back);
        if(mBackButton != null){
            mBackButton.requestFocus();
            mBackButton.setOnClickListener(mBackListener);
        }

        mTitleText = (TextView) v.findViewById(R.id.top_title);

        //bottom layout
        mBottomLayout = v.findViewById(R.id.layout_bottom);
        mPauseButton = (ImageButton) v.findViewById(R.id.bottom_pause);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }

        mFullscreenButton = (ImageButton) v.findViewById(R.id.bottom_fullscreen);
        if (mFullscreenButton != null) {
            mFullscreenButton.requestFocus();
            mFullscreenButton.setOnClickListener(mFullscreenListener);
        }

        mSeekBar = (SeekBar) v.findViewById(R.id.bottom_seekbar);
        if (mSeekBar != null) {
            SeekBar seeker = (SeekBar) mSeekBar;
            seeker.setOnSeekBarChangeListener(mSeekListener);
            mSeekBar.setMax(1000);
        }

        mEndTime = (TextView) v.findViewById(R.id.bottom_time);
        mCurrentTime = (TextView) v.findViewById(R.id.bottom_time_current);

        //init formatter
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }

    /**
     * show controller view
     */
    public void show() {
        if (!mShowing && mAnchorView != null) {

            //animate anchorview when layout changes
            //equals android:animateLayoutChanges="true"
            mAnchorView.setLayoutTransition(new LayoutTransition());
            setSeekProgress();
            if (mPauseButton != null) {
                mPauseButton.requestFocus();
                if(!mPlayer.canPause()){
                    mPauseButton.setEnabled(false);
                }
            }

            //add controller view to bottom of the AnchorView
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
//            (int) (mContext.getResources().getDisplayMetrics().density * 45)
            mAnchorView.addView(this, tlp);
            mShowing = true;//set view state
        }
        togglePausePlay();
        toggleFullScreen();
        //update progress
        mHandler.sendEmptyMessage(HANDLER_UPDATE_PROGRESS);

    }

    /**
     * Control if show controllerview
     */
    public void toggleContollerView(){
        if(!isShowing()){
            show();
        }else {
            //animate out controller view
            Message msg = mHandler.obtainMessage(HANDLER_ANIMATE_OUT);
            mHandler.removeMessages(HANDLER_ANIMATE_OUT);
            mHandler.sendMessageDelayed(msg, 100);
        }
    }

    private void animateOut() {
        TranslateAnimation trans = new TranslateAnimation(Animation.RELATIVE_TO_SELF,0,Animation.RELATIVE_TO_SELF,0,Animation.RELATIVE_TO_SELF,0,Animation.RELATIVE_TO_SELF,1);
        trans.setInterpolator(new AccelerateInterpolator());
        setAnimation(trans);
    }

    /**
     * get isShowing?
     * @return
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * hide controller view with animation
     * Just use LayoutTransition
       mAnchorView.setLayoutTransition(new LayoutTransition());
       equals android:animateLayoutChanges="true"
     */
    public void hide() {
        if (mAnchorView == null) {
            return;
        }

        try {
            mAnchorView.removeView(this);
            mHandler.removeMessages(HANDLER_UPDATE_PROGRESS);
        } catch (IllegalArgumentException ex) {
            Log.w("MediaController", "already removed");
        }
        mShowing = false;
    }

    /**
     * convert string to time
     * @param timeMs
     * @return
     */
    private String stringToTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    /**
     * set seekbar progress
     * @return
     */
    private int setSeekProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }

        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mSeekBar != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mSeekBar.setProgress((int) pos);
            }
            //get buffer percentage
            int percent = mPlayer.getBufferPercentage();
            //set buffer progress
            mSeekBar.setSecondaryProgress(percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(stringToTime(duration));
        if (mCurrentTime != null)
            mCurrentTime.setText(stringToTime(position));

        mTitleText.setText(mPlayer.getTopTitle());
        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        toggleContollerView();
        return true;
    }

    /**
     * Handle system key event
     * Also can ignore this...
     * @param event
     * @return
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlayer == null) {
            return true;
        }

        int keyCode = event.getKeyCode();
        //handle unique down event
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;

        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {//pause video
            if (uniqueDown) {
                doPauseResume();
                show();
                if (mPauseButton != null) {
                    mPauseButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {//play video
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                togglePausePlay();
                show();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {// stop video
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                togglePausePlay();
                show();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show();
        return super.dispatchKeyEvent(event);
    }

    /**
     * toggle pause or play
     */
    public void togglePausePlay() {
        if (mRootView == null || mPauseButton == null || mPlayer == null) {
            return;
        }

        if (mPlayer.isPlaying()) {
            mPauseButton.setImageResource(R.drawable.ic_media_pause);
        } else {
            mPauseButton.setImageResource(R.drawable.ic_media_play);
        }
    }

    /**
     * toggle full screen or not
     */
    public void toggleFullScreen() {
        if (mRootView == null || mFullscreenButton == null || mPlayer == null) {
            return;
        }

        if (mPlayer.isFullScreen()) {
            mFullscreenButton.setImageResource(R.drawable.ic_media_fullscreen_shrink);
        } else {
            mFullscreenButton.setImageResource(R.drawable.ic_media_fullscreen_stretch);
        }
    }

    private void doPauseResume() {
        if (mPlayer == null) {
            return;
        }

        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        togglePausePlay();
    }

    private void doToggleFullscreen() {
        if (mPlayer == null) {
            return;
        }

        mPlayer.toggleFullScreen();
    }

    /**
     * Seek bar drag listener
     */
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            show();
            mDragging = true;
            mHandler.removeMessages(HANDLER_UPDATE_PROGRESS);
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (mPlayer == null) {
                return;
            }

            if (!fromuser) {
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            mPlayer.seekTo((int) newposition);
            if (mCurrentTime != null)
                mCurrentTime.setText(stringToTime((int) newposition));
        }

        public void onStopTrackingTouch(SeekBar bar) {
            mDragging = false;
            setSeekProgress();
            togglePausePlay();
            show();
            mHandler.sendEmptyMessage(HANDLER_UPDATE_PROGRESS);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mSeekBar != null) {
            mSeekBar.setEnabled(enabled);
        }
        super.setEnabled(enabled);
    }



    /**
     * set top back click listener
     */
    private View.OnClickListener mBackListener = new View.OnClickListener() {
        public void onClick(View v) {
            mPlayer.exit();
        }
    };


    /**
     * set pause click listener
     */
    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
            show();
        }
    };

    /**
     * set full screen click listener
     */
    private View.OnClickListener mFullscreenListener = new View.OnClickListener() {
        public void onClick(View v) {
            doToggleFullscreen();
            show();
        }
    };

    /**
     * set backward listener,may add gesture to handle this
     */
    private View.OnClickListener mBackwardListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPlayer == null) {
                return;
            }

            int pos = mPlayer.getCurrentPosition();
            pos -= 5000;
            mPlayer.seekTo(pos);
            setSeekProgress();

            show();
        }
    };

    /**
     * set forward listener
     */
    private View.OnClickListener mForwardListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPlayer == null) {
                return;
            }

            int pos = mPlayer.getCurrentPosition();
            pos += 15000;
            mPlayer.seekTo(pos);
            setSeekProgress();

            show();
        }
    };


    /**
     * Interface of Media Controller View
     */
    public interface MediaPlayerControl {
        /**
         * start play video
         */
        void start();

        /**
         * pause video
         */
        void pause();

        /**
         * get video total time
         * @return
         */
        int getDuration();

        /**
         * get current position
         * @return
         */
        int getCurrentPosition();

        /**
         * seek to position
         * @param pos
         */
        void seekTo(int pos);

        /**
         * video is playing state
         * @return
         */
        boolean isPlaying();

        /**
         * get buffer date
         * @return
         */
        int getBufferPercentage();

        /**
         * if the video can pause
         * @return
         */
        boolean canPause();

        /**
         * can seek backward
         * @return
         */
        boolean canSeekBackward();

        /**
         * can seek forward
         * @return
         */
        boolean canSeekForward();

        /**
         * video is full screen
         * in order to control image src...
         * @return
         */
        boolean isFullScreen();

        /**
         * toggle fullScreen
         */
        void toggleFullScreen();

        /**
         * exit media player
         */
        void exit();

        /**
         * get top title name
         */
        String getTopTitle();
    }

}