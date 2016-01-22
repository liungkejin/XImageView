package cn.kejin.android.views;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/13
 */

/**
 * TODO: 可选双击操作
 */
public class SimpleGestureDetector
{
    private static final String TAG = "SimpleGesture";

    private final static int DOUBLE_CLICK_INTER_TIME = 200; // 100ms
    private final static int CLICK_INTER_TIME = 200;

    /**
     * 判断为移动的最小距离
     */
    private final int MIN_MOVE_LENGTH;

    private float mDisplayDensity = 1;

    /**
     * 点击事件记录数据
     */
    private int mPreClickX = -1;
    private int mPreClickY = -1;
    private long mPreClickTime = 0;
    private int mClickedCount = 0;

    private final ClickRunnable mClickRunnable = new ClickRunnable(0, 0);

    private Handler mHandler = new Handler();

    /**
     * 移动事件的记录数据
     */
    private int mPreMoveX = -1;
    private int mPreMoveY = -1;
    private boolean mIsMoving = false;
    private long mPrevTime = 0;

    /**
     * 放大手势检测
     */
    private ScaleGestureDetector mScaleDetector = null;

    private GestureListener mListener = null;

    public SimpleGestureDetector(Context context, GestureListener listener)
    {
        mListener = listener;

        mDisplayDensity = context.getResources().getDisplayMetrics().density;
        MIN_MOVE_LENGTH = dpToPx(20);

        mScaleDetector = new ScaleGestureDetector(context, mScaleGestureListener);
    }

    public void onTouchEvent(MotionEvent event)
    {
        int pointerCount = event.getPointerCount();

        switch (pointerCount) {
            case 1:
                handleMoveOrClick(event);
                break;

            default:
                // 一旦不是单点触控，就结束滑动
                clearMoveState();
                clearClickState();
                mScaleDetector.onTouchEvent(event);
                break;
        }
    }


    private void handleMoveOrClick(MotionEvent event)
    {
        int x = (int) event.getX();
        int y = (int) event.getY();

        long time = System.currentTimeMillis();
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (mClickedCount == 0) {
                    mPreClickX = x;
                    mPreClickY = y;
                }

                if (mAnimatorHandler != null) {
                    mAnimatorHandler.stopAnimator();
                }
//                Log.e(TAG, "X: " + x + " Y: " + y);
                mPreClickTime = System.currentTimeMillis();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mIsMoving) {
                    handlePreMoveEvent(event);
                }
                else {
                    handleMovingEvent(event);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mIsMoving) {
                    handleMovingEvent(event);
                }
                int distance = distance(mPreClickX, mPreClickY, x, y);
//                Log.e(TAG, "Distance: " + distance + " MIN: " + MIN_MOVE_LENGTH);
                if (distance < MIN_MOVE_LENGTH &&
                        (time - mPreClickTime) < CLICK_INTER_TIME) {
                    mClickedCount += 1;
                }
                else {
                    mClickedCount = 0;
                }

                mHandler.removeCallbacks(mClickRunnable);
                switch (mClickedCount) {
                    case 1:
                        mClickRunnable.setPosition(x, y);
                        mHandler.postDelayed(mClickRunnable, CLICK_INTER_TIME);
                        break;

                    case 2:
                        if (mListener != null) {
                            mListener.onDoubleClicked(x, y);
                        }
                        clearClickState();
                        break;

                    default:
                        clearClickState();
                        break;
                }

                clearMoveState();
                break;

            case MotionEvent.ACTION_CANCEL:
                clearMoveState();
                clearClickState();
                break;

        }
    }

    private void handlePreMoveEvent(MotionEvent event)
    {
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (mPreMoveX < 0) {
            mPreMoveX = x;
            mPreMoveY = y;
            return;
        }

        int dis = distance(mPreMoveX, mPreMoveY, x, y);
        if (dis > MIN_MOVE_LENGTH) {
            mPreMoveX = x;
            mPreMoveY = y;
            mIsMoving = true;
            mPrevTime = System.currentTimeMillis();
            if (mListener != null) {
                mListener.onMoving(GestureListener.STATE_BEG, 0, 0);
            }
        }
    }

    /**
     * 惯性时间
     */
    private final static int INERTIA_TIME = 1500;
    private AnimatorUpdateHandler mAnimatorHandler = new AnimatorUpdateHandler();

    private float mLastVx = 0;
    private float mLastVy = 0;

    /**
     * 惯性效果
     */
    private class AnimatorUpdateHandler implements ValueAnimator.AnimatorUpdateListener
    {
        private ValueAnimator mValueAnimator = null;

        /**
         * 开始的速度
         */
        private float mSVX = 0;
        private float mSVY = 0;

        /**
         * 开始的时间
         */
        private long mLastTime = 0;

        private final long TIME = 800;

        public void startAnimator(float svx, float svy)
        {
            if (Math.abs(svx) * TIME < 200 && Math.abs(svy) * TIME < 200) {
                return;
            }

            if (mListener == null) {
                return;
            }

            if (mValueAnimator != null && mValueAnimator.isRunning()) {
                mValueAnimator.cancel();
            }

            mLastTime = 0;

            mSVX = svx;
            if (Math.abs(svx) < 1) {
                mSVX = svx > 0 ? 1 : -1;
            }

            mSVY = svy;
            if (Math.abs(svy) < 1) {
                mSVY = svy > 0 ? 1 : -1;
            }

            long duration = (long) (Math.max(Math.abs(mSVX), Math.abs(mSVY)) * TIME);
            Log.e(TAG, "Duration: " + duration);

            mValueAnimator = ValueAnimator.ofFloat(1f, 0);
            mValueAnimator.setInterpolator(new LinearInterpolator());
            mValueAnimator.setDuration(duration);
            mValueAnimator.addUpdateListener(this);
            mValueAnimator.addListener(new Animator.AnimatorListener()
            {
                @Override
                public void onAnimationStart(Animator animation)
                {
                    //
                }

                @Override
                public void onAnimationEnd(Animator animation)
                {
                    if (mListener != null) {
                        mListener.onMoving(GestureListener.STATE_END, 0, 0);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation)
                {
                    if (mListener != null) {
                        mListener.onMoving(GestureListener.STATE_END, 0, 0);
                    }
                }

                @Override
                public void onAnimationRepeat(Animator animation)
                {

                }
            });

            mValueAnimator.start();
        }

        public void stopAnimator()
        {
            mSVX = 0;
            mSVY = 0;
            mLastTime = 0;
            if (mValueAnimator != null) {
                mValueAnimator.cancel();
            }
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation)
        {
            if (mLastTime == 0) {
                mLastTime = System.currentTimeMillis();
                return;
            }

            long time = System.currentTimeMillis() - mLastTime;

            float value = (float) animation.getAnimatedValue();
            int dx = dpToPx(mSVX * value * time);
            int dy = dpToPx(mSVY * value * time);

            Log.e(TAG, "Dx: " + dx + "  DY: " + dy + " Time: " + time);
            if (dx == 0 && dy == 0) {
                animation.cancel();
                return;
            }

            if (mListener != null) {
                mListener.onMoving(GestureListener.STATE_FLING, dx, dy);
            }

            mLastTime = System.currentTimeMillis();
        }
    }


    private void handleMovingEvent(MotionEvent event)
    {
        int x = (int) event.getX();
        int y = (int) event.getY();

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            long time = (System.currentTimeMillis() - mPrevTime) + 1;
            mLastVx = pxToDp(event.getX() - mPreMoveX) * 1.0f / time;
            mLastVy = pxToDp(event.getY() - mPreMoveY) * 1.0f / time;
            Log.e(TAG, "VX: " + mLastVx);
            Log.e(TAG, "VY: " + mLastVy);
        }
        /**
         * 求出速度
         */
        if (mListener != null) {
            boolean result = mListener.onMoving(GestureListener.STATE_ING, x - mPreMoveX, y - mPreMoveY);
            if (result && event.getAction() == MotionEvent.ACTION_UP) {
                // TODO: 进行惯性滑动
                mAnimatorHandler.startAnimator(mLastVx, mLastVy);
            }
        }

        mPreMoveX = x;
        mPreMoveY = y;
        mPrevTime = System.currentTimeMillis();
    }

    private void clearMoveState()
    {
        mPreMoveX = -1;
        mPreMoveY = -1;
        mLastVx = 0;
        mLastVy = 0;
        mIsMoving = false;
    }

    private void clearClickState()
    {
        mPreClickX = -1;
        mPreClickY = -1;
        mClickedCount = 0;
        mPreClickTime = 0;
    }

    private int distance(int ox, int oy, int nx, int ny)
    {
        return (int) Math.sqrt((nx - ox)*(nx - ox) + (ny - oy) * (ny - oy));
    }

    private int dpToPx(float dp)
    {
        return (int) (dp * mDisplayDensity + 0.5f);
    }

    private int pxToDp(float px)
    {
        return (int) (px / mDisplayDensity + 0.5f);
    }


    protected ScaleGestureDetector.OnScaleGestureListener
            mScaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener()
    {
        @Override
        public boolean onScale(ScaleGestureDetector detector)
        {
            float factor = detector.getScaleFactor();
            Log.e(TAG, "Scale Factor: " + factor);

            if (mListener != null) {
                return mListener.onScale(detector, GestureListener.STATE_ING);
            }

            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector)
        {
            if (mListener != null) {
                return mListener.onScale(detector, GestureListener.STATE_BEG);
            }

            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector)
        {
            if (mListener != null) {
                mListener.onScale(detector, GestureListener.STATE_END);
            }
        }
    };


    protected class ClickRunnable implements Runnable
    {
        protected int mX = 0;
        protected int mY = 0;

        public ClickRunnable(int x, int y)
        {
            mX = x;
            mY = y;
        }

        public void setPosition(int x, int y)
        {
            mX = x;
            mY = y;
        }

        @Override
        public void run()
        {
            if (mListener != null) {
                mListener.onTapped(mX, mY);
            }
            clearClickState();
        }
    }

    public interface GestureListener
    {
        int STATE_BEG = 0;
        int STATE_ING = 1;
        int STATE_END = 2;
        /**
         * 惯性滑动的状态
         */
        int STATE_FLING = 3;

        /**
         * 如果有双击，则这个操作在双击操作之后
         */
        void onTapped(int x, int y);

        /**
         * 双击
         */
        void onDoubleClicked(int x, int y);

        /**
         * 移动
         * 如果返回true 则，表示在ACTION_UP 之后，进行惯性滑动
         */
        boolean onMoving(int state, int dx, int dy);

        /**
         * 缩放
         */
        boolean onScale(ScaleGestureDetector detector, int state);
    }
}
