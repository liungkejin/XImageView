package cn.kejin.ximageview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2016/4/26
 */
public class GestureManager extends
        GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener
{
    public final static boolean DEBUG = XImageView.DEBUG;
    public final static String TAG = "GestureManager";

    /**
     * 默认双击放大的时间
     */
    private final static int DOUBLE_SCALE_TIME = 400;


    private IBitmapManager mBM = null;

    private IXImageView mXImageView = null;

    private XImageView.OnActionListener mActionListener = null;

    private XGestureDetector mGestureDetector = null;

    public GestureManager(IXImageView xiv,
                          IBitmapManager ibm)
    {
        mBM = ibm;
        mXImageView = xiv;

        mGestureDetector = new XGestureDetector(mXImageView.getInstance().getContext(), this);
    }

    public void setActionListener(XImageView.OnActionListener listener)
    {
        mActionListener = listener;
    }

    public void onTouchEvent(MotionEvent event)
    {
        mGestureDetector.onTouchEvent(event);
    }


    private class XGestureDetector extends GestureDetector
    {
        /**
         * 放大手势检测
         */
        private ScaleGestureDetector mScaleDetector = null;

        public XGestureDetector(Context context, GestureManager listener)
        {
            super(context, listener);

            float density = context.getResources().getDisplayMetrics().density;
            float dpi = density * 160.0f;
            mPhysicalCoeff = SensorManager.GRAVITY_EARTH  * 39.37f * dpi * 0.84f;

            mScaleDetector = new ScaleGestureDetector(context, listener);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event)
        {
            stopFling();

            mScaleDetector.onTouchEvent(event);

            return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e)
    {
        int x = (int) e.getX();
        int y = (int) e.getY();
        if (DEBUG) {
            Log.e(TAG, "On Tapped: X: " + x + " Y: " + y + " Is: " + (mBM != null && mBM.isTapOnImage(x, y)));
        }
        if (mActionListener != null) {
            mActionListener.onSingleTapped(mXImageView.getInstance(), e, mBM != null && mBM.isTapOnImage(x, y));
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e)
    {
        if (mBM == null) {
            return false;
        }

        boolean handled = false;
        if (mActionListener != null) {
            handled = mActionListener.onDoubleTapped(mXImageView.getInstance(), e);
        }
        if (!handled) {
            int x = (int) e.getX();
            int y = (int) e.getY();
            mBM.doubleTapScale(x, y, true, DOUBLE_SCALE_TIME);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e)
    {
        if (mActionListener != null) {
            mActionListener.onLongPressed(mXImageView.getInstance(), e);
        }
    }

    /*************************************滑动****************************************/
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
    {
        if (mBM == null) {
            return false;
        }

        int state = mBM.move((int) -distanceX, (int) -distanceY);

        if ((state & BitmapManager.LEFT) == BitmapManager.LEFT ||
                (state & BitmapManager.RIGHT) == BitmapManager.RIGHT) {
            mXImageView.interceptParentTouchEvent(false);
        }

        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
    {
        startFling(velocityX * 1.2f, velocityY * 1.2f);
        return true;
    }

    /*************************************缩放**************************************/

    @Override
    public boolean onScale(ScaleGestureDetector detector)
    {
        if (mBM == null) {
            return false;
        }

        float factor = detector.getScaleFactor();
        mBM.scale(detector.getFocusX(), detector.getFocusY(), factor);

        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector)
    {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector)
    {
        /**
         * 当缩放结束后，计算最新的的SampleSize, 如果SampleSize改变了，则重新解码最新的bitmap
         */
        if (mBM != null) {
            mBM.updateSampleSize();
        }
    }


    /**********************************滑动惯性*******************************/

    private float mPhysicalCoeff;
    private float mFlingFriction = ViewConfiguration.getScrollFriction();
    private final static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    private final static float INFLEXION = 0.35f;

    private ValueAnimator mValueAnimator = null;

    private void stopFling()
    {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
        }
    }

    private void startFling(final float velocityX, final float velocityY)
    {
        stopFling();

        final float fx = (velocityX < 0 ? 1 : -1);
        final float fy = (velocityY < 0 ? 1 : -1);

        final float velocity = (float) Math.hypot(velocityX, velocityY);
        final long duration = getSplineFlingDuration(velocity);

        mValueAnimator = ValueAnimator.ofFloat(1f, 0);
        mValueAnimator.setInterpolator(new LinearInterpolator());
        mValueAnimator.setDuration(duration);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
        {
            private Double mLastDisX = Double.NaN;
            private Double mLastDisY = Double.NaN;

            @Override
            public void onAnimationUpdate(ValueAnimator animation)
            {
                float value = (float) animation.getAnimatedValue();

                double curDisX = getSplineFlingDistance(value * velocityX) * fx;
                double curDisY = getSplineFlingDistance(value * velocityY) * fy;

                if (mLastDisX.isNaN() || mLastDisY.isNaN()) {
                    mLastDisX = curDisX;
                    mLastDisY = curDisY;
                    return;
                }

                int dx = (int) (curDisX - mLastDisX);
                int dy = (int) (curDisY - mLastDisY);

//                Log.e(TAG, "Dx: " + dx + "  DY: " + dy);

                if (mBM != null) {
                    mBM.move(dx, dy);
                }

                mLastDisX = curDisX;
                mLastDisY = curDisY;
            }
        });

        mValueAnimator.start();
    }

    private double getSplineDeceleration(float velocity)
    {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
    }

    private int getSplineFlingDuration(float velocity)
    {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    private double getSplineFlingDistance(float velocity)
    {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }
}
