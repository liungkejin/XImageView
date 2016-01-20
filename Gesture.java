package cn.kejin.android.views;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Author: xoracle ( Liang Ke Jin )
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
        }
    }


    private void handleMovingEvent(MotionEvent event)
    {
        int x = (int) event.getX();
        int y = (int) event.getY();

        /**
         * 求出速度
         */
        long time = System.currentTimeMillis() - mPrevTime;
        double vx = (x - mPreMoveX) * 1.0 / time;
        double vy = (y - mPreMoveY) * 1.0 / time;
        Log.e(TAG, "VX: " + vx);
        Log.e(TAG, "VY: " + vy);
        if (mListener != null) {
            boolean result = mListener.onMoving(event, x - mPreMoveX, y - mPreMoveY);
            if (result && event.getAction() == MotionEvent.ACTION_UP) {
                // TODO: 进行惯性滑动
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
        boolean onMoving(MotionEvent event, int dx, int dy);

        /**
         * 缩放
         */
        boolean onScale(ScaleGestureDetector detector, int state);
    }
}
