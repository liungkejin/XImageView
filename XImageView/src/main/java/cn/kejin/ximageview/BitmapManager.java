package cn.kejin.ximageview;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2016/1/27
 */
public class BitmapManager
{
    public final static String TAG = "BitmapManager";
    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }

    /**
     * 切换线程
     */
    private Handler mMainHandler = new Handler();

    /**
     * 异步处理图片的解码
     */
    private Handler mLoadingHandler = null;
    private HandlerThread mLoadingThread = null;
    private final static String THREAD_NAME = "SuperImageLoad";

    /**
     * Decoder
     */
    private BitmapRegionDecoder mDecoder = null;

    /**
     * 如果直接设置bitmap
     */
    private Bitmap mSrcBitmap = null;

    /**
     * Cache 文件
     */
    private File mCacheFile = null;

    /**
     * 质量参数, 默认为 RGB_565
     */
    private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;

    /**
     * 当前图片的的采样率
     */
    private int mSampleSize = 0;

    /**
     * 缩略图时的 SampleSize
     */
    private int mThumbSampleSize = 0;

    /**
     * 原图的宽高
     * View 坐标系
     */
    private Rect mImageRect = new Rect();

    /**
     * 视图的宽高
     * View 坐标系
     */
    private Rect mViewRect = new Rect();

    /**
     * 视图相对bitmap（mShowBitmapRect）坐标系的 rect
     * Bitmap 坐标系
     */
    private Rect mViewBitmapRect = new Rect();

    /**
     * 需要显示的bitmap的宽高, 就是真实需要显示的宽高，
     * 从 mDecoder中出来的 bitmap 需要 缩放为 此宽高
     * 在计算的时候，需要缩放比例来
     * Bitmap 坐标系
     */
    private RectF mShowBitmapRect = new RectF();

    /**
     * 缩略图是的 rect
     * Bitmap 坐标系
     */
    private Rect mThumbShowBitmapRect = new Rect();

    /**
     * Bitmap 网格
     */
    private BitmapGrid mBitmapGrid = new BitmapGrid();

    /**
     * 动画
     */
    private ValueAnimator mValueAnimator = null;

    /**
     * 最大和最小可放大的value
     */
    public final static float MAX_SCALE_FACTOR = 4f;
    private float mMaxScaleValue = MAX_SCALE_FACTOR;
    private float mMinScaleValue = 1f;

    private final Object mBitmapLock = new Object();

    /**
     * 用于和XImageView 回调的接口
     */
    private IManagerCallback mManagerCallback = null;

    private View mImageView = null;

    /**
     * 表示正在初始化图片， 此时不能显示图片
     */
    private boolean mIsSettingImage = true;

    private boolean mInitFitView = false;
    public void setInitFitView(boolean init) {
        mInitFitView = init;
    }

    public BitmapManager(final View view,
                         final Bitmap bitmap,
                         final boolean cache,
                         @NonNull final IManagerCallback callback)
    {
        view.post(new Runnable()
        {
            @Override
            public void run()
            {
                initialize(view, callback);
                setSrcBitmap(bitmap, cache);
            }
        });
    }

    public BitmapManager(final View view,
                         final InputStream is,
                         final Bitmap.Config config,
                         @NonNull final IManagerCallback callback)
    {
        view.post(new Runnable()
        {
            @Override
            public void run()
            {
                initialize(view, callback);
                setBitmapDecoder(is, config);
            }
        });
    }

    /**
     * 开启HandlerThread
     */
    private void initialize(View view, @NonNull IManagerCallback callback)
    {
        mImageView = view;
        mManagerCallback = callback;
        onSetImageStart();

        mCacheFile = new File(view.getContext().getCacheDir(), UUID.randomUUID().toString());
        mCacheFile.deleteOnExit();


        mLoadingThread = new HandlerThread(THREAD_NAME + this.hashCode());
        mLoadingThread.start();

        mLoadingHandler = new Handler(mLoadingThread.getLooper());
    }

    /**
     * 当这个BitmapManager 被丢弃时，必须要执行这个onDestroy(), 确保线程已经退出
     */
    public void onDestroy()
    {
//        long stime = System.currentTimeMillis();
        mLoadingThread.quit();
        mCacheFile.delete(); // 删除临时文件
        recycleAll();
        postInvalidate();

//        Log.e(TAG, "Summary Time: " + (System.currentTimeMillis() - stime));
    }

//    public void invalidate()
//    {
//        mImageView.invalidate();
//    }

    public void postInvalidate()
    {
        mImageView.postInvalidate();
    }

    private void onSetImageStart()
    {
        mIsSettingImage = true;
        mManagerCallback.onSetImageStart();
    }

    /**
     * 开始设置图片的缩略图，View的rect 等数据
     */
    private void startInitImageThumb()
    {
        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                updateViewRect(mImageView.getWidth(), mImageView.getHeight());
            }
        });
    }

    /**
     * 设置图片结束
     */
    private synchronized void onSetImageFinished(final boolean success)
    {
        final Rect image = new Rect();
        if (success) {
            mIsSettingImage = false;
            image.set(mImageRect);

            if (mInitFitView && mViewRect.contains(mImageRect)) {
                scaleToMinFitView(mViewRect.centerX(), mViewRect.centerY(), false, 0);
            }
        }

        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                mManagerCallback.onSetImageFinished(BitmapManager.this, success, image);
            }
        });
        postInvalidate();
    }

    /**
     * 直接设置 Bitmap, 这个函数只会走一次
     */
    private void setSrcBitmap(final Bitmap bitmap, boolean cache)
    {
        if (bitmap == null) {
            onSetImageFinished(true);
            return;
        }

        if (cache) {
            mLoadingHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        FileOutputStream fos = new FileOutputStream(mCacheFile);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        fos.flush();
                        fos.close();

                        FileInputStream fis = new FileInputStream(mCacheFile);
                        setBitmapDecoder(fis, null);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        onSetImageFinished(false);
                    }
                }
            });
        }
        else {
            mSrcBitmap = bitmap;
            mImageRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

            updateViewRect(mImageView.getWidth(), mImageView.getHeight());
        }
    }

    /**
     * 设置 BitmapRegionDecoder 这个函数只会走一次
     */
    private void setBitmapDecoder(final InputStream is, Bitmap.Config config)
    {
        if (is == null) {
            onSetImageFinished(true);
            return;
        }

        mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
        mLoadingHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    mDecoder = BitmapRegionDecoder.newInstance(is, false);
                    mImageRect.set(0, 0, mDecoder.getWidth(), mDecoder.getHeight());
                }
                catch (IOException e) {
                    e.printStackTrace();
                    mDecoder = null;
                }

                if (mDecoder != null) {
                    startInitImageThumb();
                }
                else {
                    onSetImageFinished(false);
                }
            }
        });
    }

    /**
     * 设置视图的尺寸, 并初始化其他相关尺寸
     * @param vw view width
     * @param vh view height
     */
    private void updateViewRect(int vw, int vh)
    {
        int iw = mImageRect.width();
        int ih = mImageRect.height();

        if (vw * vh * iw * ih == 0) {
            onSetImageFinished(false);
            return;
        }

        /**
         * 计算最大和最小缩放值
         * 如果一边 < 对应的view边, 最大放大到 max(4, 最大适应view)， 最小缩小到 min(最小适应view, 1);
         * 如果两边 > 对应的view边, 最大放大到 4, 最小为 最小适应view
         */
        mMaxScaleValue = Math.max(MAX_SCALE_FACTOR, getMaxFitViewValue());
        mMinScaleValue = Math.min(1, getMinFitViewValue());
//        if (iw < vw || ih < vh) {
//            mMaxScaleValue = Math.max(MAX_SCALE_FACTOR, getMaxFitViewValue());
//            mMinScaleValue = Math.min(1, mMinScaleValue);
//        }

        mViewRect.set(0, 0, vw, vh);
        /**
         * 计算要缩放的比例
         */
        int width = (int) (iw * 1.0f / ih * vh);
        float ratio =  (width > vw) ? (iw * 1f / vw) : (ih * 1f / vh);

        /**
         * 如果要放大显示，就不用缩放了
         */
        ratio = ratio < 1 ? 1f : ratio;
        mShowBitmapRect.set(0, 0, (int) (iw / ratio), (int) (ih / ratio));

        /**
         * 保存初始大小
         */
//        mThumbShowBitmapRect.set(mShowBitmapRect);
        mShowBitmapRect.round(mThumbShowBitmapRect);

        /**
         * 设置为正中间
         */
        int left = (int) ((mShowBitmapRect.width() - mViewRect.width())/2);
        int right = left + mViewRect.width();
        int top = (int) ((mShowBitmapRect.height() - mViewRect.height())/2);
        int bottom = top + mViewRect.height();
        mViewBitmapRect.set(left, top, right, bottom);

        /**
         * 计算出Sample Size
         */
        mSampleSize = getCurSampleSize();

        /**
         * 初始化缩略图
         * 保存缩略图时的Sample值
         */
        mThumbSampleSize = mSampleSize;

        /**
         * 初始化Grid
         */
        mBitmapGrid.initializeBitmapGrid();
    }

    /**
     * 获取ImageRect
     * @return Rect
     */
    public Rect getImageRect()
    {
        return mImageRect;
    }

    /**
     * 获取显示出来的图片长宽
     * @return Rect
     */
    public Rect getShowImageRect()
    {
        return new Rect(0, 0, (int)mShowBitmapRect.width(), (int)mShowBitmapRect.height());
    }

    /**
     * 移动, 相对移动 view
     * Left = 0x01;
     * Right = 0x02;
     * Top = 0x04;
     * Bottom = 0x08;
     */
    public static final int NONE   = 0x00;
    public static final int LEFT   = 0x01;
    public static final int RIGHT  = 0x02;
    public static final int TOP    = 0x04;
    public static final int BOTTOM = 0x08;
    public int offsetShowBitmap(int dx, int dy)
    {
        if (checkImageNotAvailable()) {
            return NONE;
        }

        Rect oRect = new Rect();
        toViewCoordinate(mShowBitmapRect).round(oRect);

        /**
         * 检测边界
         */
        int rx = dx;
        int ry = dy;

        if (oRect.left >= 0 &&
                oRect.right <= mViewRect.right) {
            rx = Integer.MAX_VALUE;
        }

        if (oRect.top >= 0 &&
                oRect.bottom <= mViewRect.bottom) {
            ry = Integer.MAX_VALUE;
        }

        if (rx != Integer.MAX_VALUE) {
            if (oRect.left + dx > 0) {
                rx = -oRect.left;
            }

            if (oRect.right + dx < mViewRect.right) {
                rx = mViewRect.right - oRect.right;
            }

            if (oRect.left + dx > 0 && oRect.right + dx < mViewRect.right) {
                rx = mViewRect.centerX() - oRect.centerX();
            }
        }

        if (ry != Integer.MAX_VALUE) {
            if (oRect.top + dy > 0) {
                ry = -oRect.top;
            }

            if (oRect.bottom + dy < mViewRect.bottom) {
                ry = mViewRect.bottom - oRect.bottom;
            }

            if (oRect.top + dy > 0 && oRect.bottom + dy < mViewRect.bottom) {
                ry = mViewRect.centerY() - oRect.centerY();
            }
        }

        mViewBitmapRect.offset(-(rx == Integer.MAX_VALUE ? 0 : rx), -(ry == Integer.MAX_VALUE ? 0 : ry));
        postInvalidate();

        Rect detectRect = new Rect(mViewBitmapRect);
        int result = NONE;
        if (detectRect.left <= 0) {
            result |= LEFT;
        }
        if (detectRect.right >= (int)mShowBitmapRect.right) {
            result |= RIGHT;
        }

        if (detectRect.top <= 0) {
            result |= TOP;
        }
        if (detectRect.bottom >= (int)mShowBitmapRect.bottom) {
            result |= BOTTOM;
        }

        return result;
    }

    /**
     * 缩放显示的 bitmap,
     * @param cx 中心点的 x
     * @param cy 中心点的 y
     * @param sc 缩放系数
     */
    public void scaleShowBitmap(float cx, float cy, float sc)
    {
        if (checkImageNotAvailable()) {
            return;
        }
//        Log.e(TAG, "SC: " + sc);

        RectF viewRect = new RectF(mViewRect);
        /**
         * 如果图片的长或宽，全在视图内，则以中线进行缩放
         */
        RectF oRect = toViewCoordinate(mShowBitmapRect);

//        Log.e(TAG, "ShowRect:" + mShowBitmapRect +"  VC: " + oRect);
        /**
         * 如果宽全在视图内
         */
        if (oRect.left > 0 && oRect.right < mViewRect.right) {
            cx = viewRect.centerX();
        }

        /**
         * 如果高全在视图内
         */
        if (oRect.top > 0 && oRect.bottom < mViewRect.bottom) {
            cy = viewRect.centerY();
        }

        /**
         * 以cx, cy缩放
         */
        float left = (cx - Math.abs(cx - oRect.left) * sc);
        float top = (cy - Math.abs(cy - oRect.top) * sc);

        float right = left + oRect.width() * sc;
        float bottom = top + oRect.height() * sc;

        RectF nRect = new RectF(left, top, right, bottom);

        if (nRect.width() < mThumbShowBitmapRect.width() ||
                nRect.height() < mThumbShowBitmapRect.height()) {
            resetShowBitmapRect();
            return;
        }

        float scaleValue = nRect.width() / mImageRect.width();
        if (scaleValue > mMaxScaleValue || scaleValue < mMinScaleValue) {
            // 不能再放大或者缩小了
            return;
        }

        /**
         * 更新ViewBitmapRect坐标, 并更新显示的bitmap rect 大小
         */
        updateViewBitmapRect(nRect);
//        Log.e(TAG, "ShowRect:" + mShowBitmapRect + "  VC: " + nRect);

        /**
         * 如果还是小于视图宽度，则需要移动到正正中间
         */
        float nx = 0;
        float ny = 0;
        RectF aRect = toViewCoordinate(mShowBitmapRect);
        if (aRect.width() < viewRect.width()) {
            nx = viewRect.centerX() - aRect.centerX();
        }
        else {
            if (aRect.left > 0) {
                nx = -aRect.left;
            }
            else if (aRect.right < viewRect.width()) {
                nx = viewRect.width() - aRect.right;
            }
        }

        if (aRect.height() < viewRect.height()) {
            ny = viewRect.centerY() - aRect.centerY();
        }
        else {
            if (aRect.top > 0) {
                ny = -aRect.top;
            }
            else if (aRect.bottom < viewRect.height()) {
                ny = viewRect.height() - aRect.bottom;
            }
        }

        aRect.offset(nx, ny);
        updateViewBitmapRect(aRect);
//        Log.e(TAG, "ShowRect:" + mShowBitmapRect+ "  VC: " + nRect);

//        Log.e(TAG, "=====================================================================");

        postInvalidate();
    }

    /**
     * 以中心点缩放
     * @param dest 目标缩放大小
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleFromCenterTo(float dest, boolean smooth, long smoothTime)
    {
        scaleTo(mViewRect.centerX(), mViewRect.centerY(), dest, smooth, smoothTime);
    }

    /**
     * 缩放到最大适应屏幕
     * @param cx 中心点
     * @param cy 中心点
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleToMaxFitView(int cx, int cy, boolean smooth, long smoothTime)
    {
        scaleTo(cx, cy, getMaxFitViewScaleFactor(), smooth, smoothTime);
    }

    /**
     * 缩放到最小适应屏幕
     * @param cx 中心点
     * @param cy 中心点
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleToMinFitView(int cx, int cy, boolean smooth, long smoothTime)
    {
        scaleTo(cx, cy, getMinFitViewScaleFactor(), smooth, smoothTime);
    }

    /**
     * 获取最大适应view的缩放倍数
     */
    private float getMaxFitViewScaleFactor()
    {
        float ws = mShowBitmapRect.width() == 0 ? 0 : mViewRect.width() * 1f / mShowBitmapRect.width();
        float hs = mShowBitmapRect.height() == 0 ? 0 : mViewRect.height() * 1f / mShowBitmapRect.height();

        return Math.max(ws, hs);
    }

    /**
     * 获取最小适应view 的缩放倍数
     */
    private float getMinFitViewScaleFactor()
    {
        float ws = mShowBitmapRect.width() == 0 ? 0 : mViewRect.width() * 1f / mShowBitmapRect.width();
        float hs = mShowBitmapRect.height() == 0 ? 0 : mViewRect.height() * 1f / mShowBitmapRect.height();

        return Math.min(ws, hs);
    }

    /**
     * 获取当前的缩放倍数, 相对原图的尺寸
     * @return float
     */
    public float getCurScaleFactor()
    {
        if (checkImageNotAvailable()) {
            return 0;
        }

        return mShowBitmapRect.height() * 1f / mImageRect.height();
    }

    /**
     * 缩放到适应屏幕
     * 如果此时整个图片都在 视图可见区域中, 则放大到占满整个屏幕
     * 如果整个图片不再可见区域中， 则缩小到整个视图可见大小
     *
     * （最小适应屏幕） 一边和视图的一边想等，另外一边小于或等于
     * (最大适应屏幕) 一边和视图的一边相等, 另外一边大于对应的视图的边
     * @param type 适应类型
     * @param cx 中心点
     * @param cy 中心点
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleToFitView(XImageView.TYPE_FIT type, int cx, int cy, boolean smooth, long smoothTime)
    {
        if (mValueAnimator != null && mValueAnimator.isRunning()) {
            return;
        }

        if (checkImageNotAvailable() && isRectValid(mShowBitmapRect)) {
            return;
        }

        if (type == null) {
            type = XImageView.TYPE_FIT.FIT_VIEW;
        }

        float destScale;

        float sw = mShowBitmapRect.width();
        float sh = mShowBitmapRect.height();

        int tw = mThumbShowBitmapRect.width();
        int th = mThumbShowBitmapRect.height();

        float maxFitScale = getMaxFitViewScaleFactor();
        float minFitScale = getMinFitViewScaleFactor();

        if (type == XImageView.TYPE_FIT.FIT_VIEW) {
            /**
             * 如果是最小适应 view
             */
            if (sw < mViewRect.width() + 5f && sh < mViewRect.height() + 5f) {
                destScale = maxFitScale;
            }
            else {
                destScale = minFitScale;
            }
        }
        else {
            /**
             * 如果和小图差不多大小
             */
            if ((Math.abs(sw - tw) < 5 && Math.abs(sh - th) < 5)) {
                destScale = maxFitScale;
            }
            else {
                float ws = mImageRect.width() * 1f / mShowBitmapRect.width();
                float hs = mImageRect.height() * 1f/ mShowBitmapRect.height();

                destScale = Math.min(minFitScale, Math.min(ws, hs));
            }
        }

//        Log.e(TAG, "Dest Scale: " + destScale);

        scaleTo(cx, cy, destScale, smooth, smoothTime);
    }

    private float mLastAnimatedValue = 1f;

    /**
     * 缩放到指定的大小
     * @param cx 中心点
     * @param cy 中心点
     * @param dest 目标缩放倍数
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleTo(final int cx, final int cy,
                         float dest, boolean smooth, long smoothTime)
    {
        if (checkImageNotAvailable()) {
            return;
        }

        if (mValueAnimator != null && mValueAnimator.isRunning()) {
            mValueAnimator.end();
            mValueAnimator.cancel();
        }

        if (smooth) {
            mLastAnimatedValue = 1f;
            ObjectAnimator.ofFloat(1f, dest);
            mValueAnimator = ValueAnimator.ofFloat(1f, dest);
            mValueAnimator.setDuration(smoothTime);
            mValueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

            mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
            {
                @Override
                public void onAnimationUpdate(ValueAnimator animation)
                {
                    float value = (float) animation.getAnimatedValue();
                    scaleShowBitmap(cx, cy, value / mLastAnimatedValue);
                    mLastAnimatedValue = value;
                }
            });

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
                    updateSampleSize();
                }

                @Override
                public void onAnimationCancel(Animator animation)
                {
                    updateSampleSize();
                }

                @Override
                public void onAnimationRepeat(Animator animation)
                {

                }
            });
            mValueAnimator.start();
        }
        else {
            scaleShowBitmap(cx, cy, dest);
            updateSampleSize();
        }
    }

    /**
     * 计算最小适应view的缩放值
     * 即图片所有部分都显示在view中, 而且一边和view相等
     */
    private float getMinFitViewValue()
    {
        if (checkImageNotAvailable()) {
            return 0f;
        }

        float iw = mImageRect.width();
        float ih = mImageRect.height();

        float vw = mViewRect.width();
        float vh = mViewRect.height();

        return Math.min(vw / iw, vh / ih);
    }

    /**
     * 计算最大适应view的缩放值
     */
    private float getMaxFitViewValue()
    {
        if (checkImageNotAvailable()) {
            return 0f;
        }

        float iw = mImageRect.width();
        float ih = mImageRect.height();

        float vw = mViewRect.width();
        float vh = mViewRect.height();

        return Math.max(vw / iw, vh / ih);
    }

    /**
     * 重置为初始化状态
     */
    private void resetShowBitmapRect()
    {
        mShowBitmapRect.set(mThumbShowBitmapRect);

        int left = (int) ((mShowBitmapRect.width() - mViewRect.width())/2);
        int right = left + mViewRect.width();
        int top = (int) ((mShowBitmapRect.height() - mViewRect.height())/2);
        int bottom = top + mViewRect.height();
        mViewBitmapRect.set(left, top, right, bottom);
    }

    /**
     * 更新ViewBitmapRect
     * @param rect ShowBitmapRect相对view坐标系的rect
     */
    private void updateViewBitmapRect(RectF rect)
    {
        Rect vRect = new Rect(0, 0, mViewRect.width(), mViewRect.height());
        vRect.left = (int) -rect.left;
        vRect.right = vRect.left + mViewRect.width();
        vRect.top = (int) -rect.top;
        vRect.bottom = vRect.top + mViewRect.height();

        mViewBitmapRect.set(vRect);
        mShowBitmapRect.set(0, 0, rect.width(), rect.height());
    }

    /**
     * 检查或者更新视图的尺寸
     */
    private boolean checkOrUpdateViewRect(int width, int height)
    {
        if (mViewRect.width() != width || mViewRect.height() != height) {
            onSetImageStart();
            updateViewRect(width, height);
            return true;
        }

        return false;
    }

    /**
     * 检查当前整个BitmapManager 是否有效
     * @return boolean
     */
    public boolean checkImageNotAvailable()
    {
        return (mIsSettingImage || (mSrcBitmap == null && mDecoder == null)
                || mImageRect.width() <= 0 || mImageRect.height() <= 0);
    }

    /**
     * 画可见区域的的Bitmap
     * @param canvas 画布
     * @param width 宽
     * @param height 高
     * @return boolean (true 表示画图片成功, false 表示正在处理图片， 没有真正画出)
     */
    public boolean drawVisibleBitmap(@NonNull Canvas canvas, int width, int height)
    {
        if (checkImageNotAvailable()) {
            return false;
        }

        if (mSrcBitmap != null &&
                android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            int mw = canvas.getMaximumBitmapWidth();
            int mh = canvas.getMaximumBitmapHeight();

            /**
             * 如果图片太大，直接使用bitmap 会占用很大内存，所以建议缓存为文件再显示
             */
            if (mSrcBitmap.getHeight() > mh || mSrcBitmap.getWidth() > mw) {
                Log.e(TAG, "Bitmap is too large > canvas MaximumBitmapSize, You should cache it!");
            }
        }

        /**
         * 更新视图或者画出图片
         */
        return !checkOrUpdateViewRect(width, height) && mBitmapGrid.drawVisibleGrid(canvas);
    }


    /**
     * 计算显示和原图之间的比例
     */
    private float getShowImageRatio()
    {
        return mShowBitmapRect.height() * 1f / mImageRect.height();
    }

    /**
     * 计算原图和真实bitmap之间的比例
     */
    private float getRealImageRatio()
    {
        return 1f / mSampleSize;
    }

    /**
     * 计算真实bitmap和显示的bitmap之间的比例
     */
    private float getRealShowRatio()
    {
        return mImageRect.height() * 1f / (mSampleSize * mShowBitmapRect.height());
    }


    /**
     * 释放所有内存，停止线程
     */
    private void recycleAll()
    {
        mBitmapGrid.recycleAllGrids();

        synchronized (mBitmapLock) {
            if (mDecoder != null) {
                mDecoder.recycle();
                mDecoder = null;
            }

            mSrcBitmap = null;
        }
    }


    /**
     ***************************************************************
     **/


    /**
     * 获取当前的SampleSize 值
     */
    private int getCurSampleSize()
    {
        int iw = mImageRect.width();
        int ih = mImageRect.height();
        int bw = (int) mShowBitmapRect.width();
        int bh = (int) mShowBitmapRect.height();

        /**
         * 以 bitmap 的宽高为标准
         * 分别以 宽高为标准，计算对应的的宽高
         * 如果是宽图, 则以View的宽为标准
         * 否则为高图， 以view的高为标准
         * 求出 SampleSize
         */
        int width = (int) (iw * 1.0f / ih * bh);
        int sampleSize = (width > bw) ?
                computeSampleSize(iw / bw) : computeSampleSize(ih / bh);
        if (sampleSize < 1) {
            sampleSize = 1;
        }

        return sampleSize;
    }

    /**
     * 当缩放结束后，需要重新解码最新的bitmap
     */
    public void updateSampleSize()
    {
        if (checkImageNotAvailable()) {
            return;
        }

        int sampleSize = getCurSampleSize();
        if (sampleSize == mSampleSize) {
            return;
        }
        mSampleSize = sampleSize;
        postInvalidate();

//        Log.e(TAG, "Current Sample Size: " + mSampleSize);
    }

    /**
     * 检测是否这个点在图片上
     * @param x 坐标x
     * @param y 坐标Y
     * @return boolean
     */
    public boolean isTapOnImage(int x, int y)
    {
        return !checkImageNotAvailable() && toViewCoordinate(mShowBitmapRect).contains(x, y);
    }

    /**
     * 检查是否正在设置图片
     * @return boolean
     */
    public boolean isSettingImage()
    {
        return mIsSettingImage;
    }

    /**
     * 真实的 Bitmap 的尺寸就是 原图的 1/sampleSize
     */
//    private Rect getRealBitmapRect()
//    {
//        return rectMulti(mImageRect, 1f/mSampleSize);
//    }

    /**
     * 坐标转换, bitmap坐标转换为 view 坐标
     */
    private Rect toViewCoordinate(Rect rect)
    {
        if (rect == null) {
            return new Rect();
        }

        int left = rect.left - mViewBitmapRect.left;
        int right = left + rect.width();
        int top = rect.top - mViewBitmapRect.top;
        int bottom = top + rect.height();

        return new Rect(left, top, right, bottom);
    }

    private RectF toViewCoordinate(RectF rect)
    {
        if (rect == null) {
            return new RectF();
        }

        float left = rect.left - mViewBitmapRect.left;
        float right = left + rect.width();
        float top = rect.top - mViewBitmapRect.top;
        float bottom = top + rect.height();

        return new RectF(left, top, right, bottom);
    }

    /**
     * view 坐标转换为 bitmap 坐标
     */
    private Rect toBitmapCoordinate(Rect rect)
    {
        if (rect == null) {
            return new Rect();
        }

        int left = rect.left + mViewBitmapRect.left;
        int right = left + rect.width();
        int top = rect.top + mViewBitmapRect.top;
        int bottom = top + rect.height();

        return new Rect(left, top, right, bottom);
    }

    /**
     * 计算出可见区域的显示出来的bitmap rect, 相对 bitmap 坐标系
     */
    private Rect getVisibleShowBitmapRect()
    {
        int left = (int) Math.max(mShowBitmapRect.left, mViewBitmapRect.left);
        int right = (int) Math.min(mShowBitmapRect.right, mViewBitmapRect.right);
        int top = (int) Math.max(mShowBitmapRect.top, mViewBitmapRect.top);
        int bottom = (int) Math.min(mShowBitmapRect.bottom, mViewBitmapRect.bottom);

        return new Rect(left, top, right, bottom);
    }

    /**
     * height / width
     */
    private float getImageRatio()
    {
        return mImageRect.height() * 1f / mImageRect.width();
    }

    /**
     * 从解码出一块bitmap
     */
    private Bitmap decodeRectBitmap(Rect rect, int sampleSize)
    {
        if (rect == null || !mImageRect.contains(rect)) {
            return null;
        }

        synchronized (mBitmapLock) {
            if (mSrcBitmap != null) {
                return Bitmap.createBitmap(mSrcBitmap, rect.left, rect.top, rect.width(), rect.height());
            }
            else if (mDecoder != null) {
                BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
                tmpOptions.inPreferredConfig = mBitmapConfig;
                tmpOptions.inSampleSize = sampleSize;
                tmpOptions.inJustDecodeBounds = false;

                return mDecoder.decodeRegion(rect, tmpOptions);
            }
        }

        return null;
    }

    /*****************************************************************/

    /**
     * 一个 Bitmap 单元， 每一个bitmap 单元都会有个缩略图的bitmap
     * 这个缩略图是在一开始就已经生成的，并一直存在，只有在最后才会被释放
     * 另外一个 bitmap 就当前需要显示的正常的bitmap，当只需要显示缩略图或者屏幕中不可见时，这个bitmap会被释放掉
     *
     * 如果整个图片就是一个 Bitmap 时，缩略图的bitmap就是正常的 bitmap,
     */
    private class BitmapUnit
    {
        /**
         * 是否正在加载bitmap
         */
        private boolean mIsLoading = false;

        /**
         * 当前bitmap 的SampleSize,
         * 如果此时的SampleSize 和全局的SampleSize不相等，则需要重新decode一次
         */
        public int mCurSampleSize = 0;

        /**
         * 目前的 mBitmap
         */
        public Bitmap mBitmap = null;

        /**
         * 缩略图的bitmap
         */
        public Bitmap mThumbBitmap = null;

        /**
         * 这里回收所有的bitmap
         */
        private void recycleAll()
        {
            mBitmap = null;
            mThumbBitmap = null;

            mCurSampleSize = 0;
        }

        /**
         * 这里只回收正常的bitmap, 不回收缩略图的bitmap
         */
        private void recycle()
        {
            mBitmap = null;
            mCurSampleSize = mThumbSampleSize;
        }
    }


    /**
     * 策略：
     * 一开始就将原图以 view的尺寸分割为 grid
     * 在将初始化缩略图等比分割为 n x m, 并保存在单元格里面
     * 在缩放时候， 如果实际的bitmap改变了， 只需要重新分割，
     * 然后更新显示区域的 bitmap 单元格
     * 拖动时不断更新显示区域的 bitmap 单元格
     */
    private class BitmapGrid
    {
        /**
         * 总共的单元格数
         */
        private int mN = 0;
        private int mM = 0;

        /**
         * 所有的单元格
         */
        private BitmapUnit [][] mGrids = null;

        private void initializeBitmapGrid()
        {
            if (mGrids != null) {
                recycleAllGrids();
            }

            int vw = mViewRect.width();
            int vh = mViewRect.height();

            int iw = mImageRect.width();
            int ih = mImageRect.height();

            mN = ih / vh + (ih % vh == 0 ? 0 : 1);
            mM = iw / vw + (iw % vw == 0 ? 0 : 1);

            mGrids = new BitmapUnit[mN][mM];
            for (int i = 0; i < mN; ++i) {
                for (int j = 0; j < mM; ++j) {
                    mGrids[i][j] = new BitmapUnit();
                    mGrids[i][j].mCurSampleSize = mSampleSize;
                }
            }

            /**
             * 异步加载缩略图
             */
            if (mLoadingThread.isAlive()) {
                mLoadingHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        decodeThumbUnitBitmap();
                        /**
                         * 设置完成
                         */
                        onSetImageFinished(true);
                    }
                });
            }
        }

        /**
         * 获取bitmap
         */
        private Bitmap getGridBitmap(final int n, final int m)
        {
            if (isValidGrid(n, m)) {
                BitmapUnit unit = mGrids[n][m];
                if (mSrcBitmap != null) {
                    return unit.mThumbBitmap;
                }

                if (mSampleSize == mThumbSampleSize) {
                    return unit.mThumbBitmap;
                }

                if (unit.mCurSampleSize != mSampleSize) {
                    loadUnitBitmap(n, m);
                }

                return (unit.mBitmap != null &&
                        !unit.mBitmap.isRecycled()) ? unit.mBitmap : unit.mThumbBitmap;
            }

            return null;
        }

        /**
         * 异步就加载单元格bitmap
         */
        private void loadUnitBitmap(final int n, final int m)
        {
            if (mSampleSize != mThumbSampleSize && isValidGrid(n, m)) {
                BitmapUnit unit = mGrids[n][m];
                if (unit.mIsLoading) {
                    return;
                }
                unit.mIsLoading = true;

                mLoadingHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (isValidGrid(n, m)) {
                            decodeVisibleUnitBitmap(n, m);
                            mGrids[n][m].mIsLoading = false;
                            if (mGrids[n][m].mCurSampleSize != mSampleSize) {
                                return;
                            }
                            postInvalidate();
                        }
                    }
                });
            }
        }

        /**
         * 回收所有的单元格
         */
        private void recycleAllGrids()
        {
            for(int i = 0; i < mN; ++i) {
                for (int j = 0; j < mM; ++j) {
                    mGrids[i][j].recycleAll();
                }
            }
        }

        /**
         * 判断是否为有效的单元格
         */
        private boolean isValidGrid(int n, int m)
        {
            return n >= 0 && n < mN && m >= 0 && m < mM;
        }

        /**
         * 得出原图的单元格
         */
        private Rect getUnitRect(int n, int m)
        {
            if (n < 0 || n >= mN || m < 0 || m >= mM) {
                return null;
            }

            int vw = mViewRect.width();
            int vh = mViewRect.height();

            int iw = mImageRect.width();
            int ih = mImageRect.height();

            int left = Math.min(iw, m * vw);
            int right = Math.min(iw, left + vw);

            int top = Math.min(ih, n * vh);
            int bottom = Math.min(ih, top + vh);

            if (left == right || top == bottom) {
                return null;
            }

            return new Rect(left, top, right, bottom);
        }

        /**
         * 获取显示的单元格rect
         */
        private Rect getShowBitmapUnit(int n, int m)
        {
            RectF vRect = rectMulti(mViewRect, getShowImageRatio());

            float vw = vRect.width();
            float vh = vRect.height();

            float sWidth = mShowBitmapRect.width();
            float sHeight = mShowBitmapRect.height();

            float left = Math.min(m * vw, sWidth);
            float right = Math.min(left + vw, sWidth);

            float top = Math.min(n*vh, sHeight);
            float bottom = Math.min(top + vh, sHeight);

            return new Rect((int)left, (int)top, (int)right, (int)bottom);
        }

        /**
         * 判断是否是可见的单元格
         */
        private boolean isVisibleUnit(int n, int m)
        {
            Rect v = getVisibleGrid();

            return n >= v.top && n <= v.bottom && m >= v.left && m <= v.right;
        }

        /**
         * 回收不可见区域的bitmap
         * @param visible 可见区域
         */
        private void recycleInvisibleGrids(Rect visible)
        {
            if (mGrids == null) {
                return;
            }

            int sn = visible.top;
            int sm = visible.left;
            int en = visible.bottom;
            int em = visible.right;

            /**
             * 如果上一次有不可见的，并距离可见区域 > 1 的，就释放掉
             * +--+--+--+--+--+
             * |XX|XX|11|11|XX|
             * +--+--+--+--+--+
             * |XX|XX|11|11|XX|
             * +--+--+--+--+--+
             * |XX|XX|XX|XX|XX|
             * +--+--+--+--+--+
             * XX 部分就是可以被释放掉的区域
             */
            int mn = 1;
            for (int i = 0; i < mN; ++i) {
                for (int j = 0; j < mM; ++j) {
                    if (sn - i >= mn || i - en >= mn
                            || sm - j >= mn || j - em >= mn) {
                        mGrids[i][j].recycle();
                    }
                }
            }
        }

        /**
         * 画出可见的几个格子
         */
        private boolean drawVisibleGrid(Canvas canvas)
        {
            if ((mSrcBitmap == null && mDecoder == null) ||
                    mGrids == null || mImageRect.width() <= 0 || mImageRect.height() <= 0) {
                return false;
            }

            Rect visible = getVisibleGrid();
            recycleInvisibleGrids(visible);

            int sn = visible.top;
            int sm = visible.left;
            int en = visible.bottom;
            int em = visible.right;

            for (int n = sn; n <= en; ++n) {
                for (int m = sm; m <= em; ++m) {
                    Rect rect = getShowBitmapUnit(n, m);
                    Bitmap bitmap = getGridBitmap(n, m);
                    if (bitmap != null) {
                        Rect vRect = toViewCoordinate(rect);
                        canvas.drawBitmap(bitmap, null, vRect, null);
                    }

//                    mPaint.setColor(Color.MAGENTA);
//                    mPaint.setStrokeWidth(2);
//                    canvas.drawRect(toViewCoordinate(rect), mPaint);
                }
            }

            return true;
        }

        /**
         * decode出一个可见单元的bitmap
         * 并保存这个bitmap的 sample size
         */
        private synchronized void decodeVisibleUnitBitmap(int n, int m)
        {
            if (isValidGrid(n, m) && isVisibleUnit(n, m)) {
                BitmapUnit unit = mGrids[n][m];

                // 防止二次decode
                if (unit.mCurSampleSize == mSampleSize) {
                    return;
                }

                unit.recycle();

                Rect rect = getUnitRect(n, m);
                unit.mCurSampleSize = mSampleSize;
                unit.mBitmap = decodeRectBitmap(rect, unit.mCurSampleSize);
            }
        }

        /**
         * 解码为缩略图的 bitmap
         */
        private void decodeThumbUnitBitmap()
        {
            for (int n = 0; n < mN; ++n) {
                for (int m = 0; m < mM; ++m) {
                    Rect rect = getUnitRect(n, m);
                    if (rect != null) {
                        mGrids[n][m].mCurSampleSize = mSampleSize;
                        mGrids[n][m].mThumbBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                    }
                }
            }
        }


        /**
         * 计算出可见的实际单元格
         * @return Rect (left=sm, top=sn, right=em, bottom=en)
         */
        private Rect getVisibleGrid()
        {
            /**
             * 将RealBitmap 也分割为 N x M 个单元格
             */
            /**
             * 把视图缩小和真实bitmap大小等比例
             */
            float vw = mViewRect.width()*1f / mSampleSize;
            float vh = mViewRect.height()*1f / mSampleSize;

            /**
             * 计算出可见区域的真实bitmap rect
             */
            RectF vBRect = rectMulti(getVisibleShowBitmapRect(), getRealShowRatio());

            /**
             * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
             */
            int sm = (int) (vBRect.left / vw);
            int sn = (int) (vBRect.top / vh);

            int em = (int) (sm + Math.ceil(vBRect.width() / vw));
            int en = (int) (sn + Math.ceil(vBRect.height() / vh));

            em = em > mM ? mM : em;
            en = en > mN ? mN : en;

            return new Rect(sm, sn, em, en);
        }
    }

    /**
     * rect 乘法
     */
    private RectF rectMulti(Rect r, float ratio)
    {
        return rectMulti(new RectF(r), ratio);
    }

    private RectF rectMulti(RectF r, float ratio)
    {
        float left = r.left * ratio;
        float top = r.top * ratio;
        float right = left + r.width() * ratio;
        float bottom = top + r.height() * ratio;

        return new RectF(left, top, right, bottom);
    }

    /**
     * 根据比率来获得合适的采样率, 因为采样率都是以 2^n 来定的
     */
    private int computeSampleSize(int size)
    {
        int sample = 1;
        while ((size / 2) != 0) {
            size /= 2;
            sample *= 2;
        }

        return sample;
    }

    /**
     * 检查矩形是否有效
     */
    private boolean isRectValid(RectF rect)
    {
        return rect.width() > 0 && rect.height() > 0;
    }


    /**
     * scale一个矩形
     */
    private RectF scaleRect(RectF rect, float scale)
    {
        float cx = rect.centerX();
        float cy = rect.centerY();

        float left = (rect.left - cx)*scale + cx;
        float top = (rect.top - cy)*scale + cy;

        float right = (rect.right - cx)*scale + cx;
        float bottom = (right - left) * (rect.height() / rect.width()) + top;

        return new RectF(left, top, right, bottom);
    }

    /**
     * 获取两个矩形的相交区域
     */
    private RectF getIntersectedRect(RectF rect1, RectF rect2)
    {
        if (!rect1.intersect(rect2)) {
            return new RectF();
        }

        float left = Math.max(rect2.left, rect1.left);
        float right = Math.min(rect2.right, rect1.right);

        float top = Math.max(rect2.top, rect1.top);
        float bottom = Math.min(rect2.bottom, rect1.bottom);

        return new RectF(left, top, right, bottom);
    }

    /**
     * Callback
     */
    public interface IManagerCallback
    {
        void onSetImageStart();
        void onSetImageFinished(BitmapManager bm, boolean success, Rect image);
    }
}
