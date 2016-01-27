package cn.kejin.android.views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Scroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/10
 */

/**
 * 能显示大长图
 * TODO: 自定义初始化状态
 * TODO: 优化代码
 * TODO: 显示正在加载， 并增加接口
 * TODO: 不能连续设置Image
 * TODO: 解决ViewPager的冲突, 重写自己的ViewPager
 */
public class XImageView extends View
{
    public final static String TAG = "SuperImageView";

    private final static String THREAD_NAME = "SuperImageLoad";

    private final Object mQueueLock = new Object();

    /**
     * 默认双击放大的时间
     */
    private final static int DOUBLE_SCALE_TIME = 400;

    /**
     * 表示那一边到达了边界
     */
    private static final int LEFT   = 0x01;
    private static final int RIGHT  = 0x02;
    private static final int TOP    = 0x04;
    private static final int BOTTOM = 0x08;

    /**
     * Gesture Detector
     */
    private SimpleGestureDetector mSimpleDetector = null;

    /**
     * bitmap 的管理器
     */
    private BitmapManager mBitmapManager = null;

    /**
     * 异步处理图片的解码
     */
    private Handler mLoadingHandler = null;
    private HandlerThread mLoadingThread = null;

    /**
     * 判断是否需要进行刷新（重新设置Bitmap和viewRect）
     */

    /**
     * 判断是否正在设置图片
     */
    private boolean mIsSettingImage = false;

    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }

    public XImageView(Context context)
    {
        super(context);
        initialize();
    }

    public XImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initialize();
    }

    public XImageView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize()
    {
        Context context = getContext();

        mSimpleDetector = new SimpleGestureDetector(context, mGestureListener);

        super.setOnLongClickListener(new OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                if (mActionListener != null) {
                    mActionListener.onLongClicked();
                }
                return false;
            }
        });
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener listener)
    {
        // nothing
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        //if (mIsReachedBorder) {
        //    return false;
        //}
        return super.onTouchEvent(event); //(event.getPointerCount() == 1 && mBitmapManager.isReachedBorder());
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        if (mBitmapManager != null) {
            boolean show = mBitmapManager.drawVisibleBitmap(canvas, getWidth(), getHeight());

            if (!show) {
                //TODO: 显示正在加载
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();
        Log.e(TAG, "OnAttachedToWindow");
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();

        Log.e(TAG, "onDetachedFromWindow");
        if (mBitmapManager != null) {
            mBitmapManager.onDestroy();
        }
    }

    /**
     * 劫持输入事件, ViewPager
     * @param intercept
     */
    private void interceptParentTouchEvent(boolean intercept)
    {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(intercept);
        }
    }

    public void setImage(Bitmap bitmap)
    {
        setImage(bitmap, false);
    }

    /**
     * Bitmap 转换为 InputStream, 使用 BitmapRegionDecoder 管理
     * @param bitmap 图片
     * @param cache 是否需要将bitmap 保存为文件再 转换为InputStream
     */
    public void setImage(final Bitmap bitmap, boolean cache)
    {
        long before = System.currentTimeMillis();
        if (mBitmapManager != null) {
            mBitmapManager.onDestroy();
        }

        mBitmapManager = new BitmapManager(this, bitmap, cache, mManagerCallback);
        Log.e(TAG, "Set Image Time: " + (System.currentTimeMillis() - before));
    }


    /**
     * @param path path
     */
    public void setImage(String path)
    {
        setImage(new File(path), Bitmap.Config.RGB_565);
    }

    public void setImage(String path, Bitmap.Config config)
    {
        setImage(new File(path), config);
    }

    /**
     * @param file File
     */
    public void setImage(File file)
    {
        setImage(file, Bitmap.Config.RGB_565);
    }

    public void setImage(File file, Bitmap.Config config)
    {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            setImage(fis, config);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param inputStream 输入流
     */
    public void setImage(InputStream inputStream)
    {
        setImage(inputStream, Bitmap.Config.RGB_565);
    }

    public void setImage(InputStream is, Bitmap.Config config)
    {
        long before = System.currentTimeMillis();
        if (mBitmapManager != null) {
            mBitmapManager.onDestroy();
        }

        mBitmapManager = new BitmapManager(this, is, config, mManagerCallback);

        Log.e(TAG, "Set Image Time: " + (System.currentTimeMillis() - before));
    }

    /**
     * 缩放到指定的大小, 起始是以当前的大小为准
     * 并且以屏幕中心进行缩放
     */
    public void scaleTo(float dest, boolean smooth, int smoothTime)
    {
        mBitmapManager.scaleFromCenterTo(dest, smooth, smoothTime);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event)
    {
        mSimpleDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.e(TAG, "intercept....");
                interceptParentTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                break;

            case MotionEvent.ACTION_UP:
                Log.e(TAG, "cancel intercept...");
                interceptParentTouchEvent(false);
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    private BitmapManager.IManagerCallback
            mManagerCallback = new BitmapManager.IManagerCallback()
    {
        @Override
        public void onSetImageFinished(boolean success)
        {
            if (mActionListener != null) {
                mActionListener.onSetImageFinished();
            }
        }
    };

    /********************* Gesture Listener *******************************/
    private SimpleGestureDetector.GestureListener
            mGestureListener = new SimpleGestureDetector.GestureListener()
    {
        @Override
        public void onTapped(int x, int y)
        {
            Log.e(TAG, "On Tapped: X: " + x + " Y: " + y);
            if (mActionListener != null) {
                // TODO: 检测点击是否在图片上
                mActionListener.onTapped(false);
            }
        }

        @Override
        public void onDoubleClicked(int x, int y)
        {
            Log.e(TAG, "On Double Clicked X: " + x + " Y: " + y);
            mBitmapManager.scaleToFitView(x, y, true, DOUBLE_SCALE_TIME);
            if (mActionListener != null) {
                mActionListener.onDoubleClicked();
            }
        }

        @Override
        public boolean onMoving(int movingState, int dx, int dy)
        {
            Log.e(TAG, "MovingState: " + movingState);
            int state = mBitmapManager.offsetShowBitmap(dx, dy);

            if ((state & LEFT) == LEFT || (state & RIGHT) == RIGHT) {
                Log.e(TAG, "dis intercept...");
                interceptParentTouchEvent(false);
            }

            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector, int state)
        {
            float factor = detector.getScaleFactor();
            switch (state) {
                case STATE_BEG:
                    break;

                case STATE_ING:
                    mBitmapManager.scaleShowBitmap(
                            (int) detector.getFocusX(), (int) detector.getFocusY(), factor);
                    break;

                case STATE_END:
                    /**
                     * 当缩放结束后，计算最新的的SampleSize, 如果SampleSize改变了，则重新解码最新的bitmap
                     */
                    mBitmapManager.updateSampleSize();
                    break;
            }

            return true;
        }
    };


    private OnActionListener mActionListener = null;

    public void setActionListener(OnActionListener listener)
    {
        mActionListener = listener;
    }

    public interface OnActionListener
    {
        /**
         * 在View上点击了一次（而且没有双击）
         * @param onImage 是否点击在了有效的图片上
         */
        void onTapped(boolean onImage);

        /**
         * 双击了
         */
        void onDoubleClicked();

        /**
         * 长按了
         */
        void onLongClicked();

        /**
         * 初始化完成，图片已经显示
         */
        void onSetImageFinished();
    }
}