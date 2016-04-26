package cn.kejin.ximageview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;


/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/10
 */

/**
 * 能显示大长图
 */
public class XImageView extends View implements IXImageView
{
    public final static String TAG = "XImageView";

    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }


    /**
     * Gesture Detector
     */
    private GestureManager mGestureManager = null;

    /**
     * Action Listener
     */
    private OnActionListener mActionListener = null;

    /**
     * bitmap 的管理器
     */
    private IBitmapManager mBM = null;


    /**
     * *************Config*****************
     */
//    private boolean mCacheBitmap = false;
//
//    private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;

    private InitType mInitType = InitType.FIT_VIEW_MIN;

    private DoubleType mDoubleType = DoubleType.FIT_VIEW_MIN_VIEW_MAX;
//
//    /**
//     * 初始化状态时，是否需要适应 view
//     */
//    private boolean mInitFitView = false;
//
//    /**
//     * 双击放大的方式
//     *
//     * 当图片的最大放大尺寸都小于 view 的尺寸时， 强制为 fitImage
//     * fitView: 不管图片的尺寸多少， 双击缩放总是放大到 最大适应view 或者 缩小到 最小适应view
//     * fitImage: 双击缩放的时候为 放大到 Min(最大适应view, 最大放大尺寸) 或者 缩小到 Min(最小适应view, 图片的尺寸)
//     */
//    public enum TYPE_FIT
//    {
//        FIT_VIEW (0),
//        FIT_IMAGE (1);
//
//        final int mType;
//        TYPE_FIT(int t) { mType = t; }
//    }
//
//    private TYPE_FIT mDoubleTapScaleType = TYPE_FIT.FIT_VIEW;


    public XImageView(Context context)
    {
        this(context, null, 0);
    }

    public XImageView(Context context, InitType type)
    {
        this(context, null, 0);
        mInitType = type;
    }

    public XImageView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public XImageView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs)
    {
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.XImageView);

            int initType = ta.getInt(R.styleable.XImageView_initType, mInitType.value);
            mInitType = InitType.valueOf(initType);

            int doubleType = ta.getInt(R.styleable.XImageView_doubleType, mDoubleType.value);
            mDoubleType = DoubleType.valueOf(doubleType);

            ta.recycle();
        }

        mBM = new BitmapManager(this);
        mGestureManager = new GestureManager(this, mBM);

        super.setOnLongClickListener(new OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
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
    public boolean dispatchTouchEvent(MotionEvent event)
    {
        mGestureManager.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mBM != null && !mBM.isNotAvailable()) {
                    interceptParentTouchEvent(true);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                break;

            case MotionEvent.ACTION_UP:
                interceptParentTouchEvent(false);
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        int width = getWidth();
        int height = getHeight();
        if (mBM != null) {
            boolean show = mBM.draw(canvas, width, height);
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

        if(DEBUG) {
            Log.e(TAG, "OnAttachedToWindow");
        }
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();

        mBM.destroy();

        if (DEBUG) {
            Log.e(TAG, "onDetachedFromWindow");
        }
    }

    /**
     * 清除bitmap 和 config
     */
//    private void clearBitmapAndConfig()
//    {
//        if (mBM != null) {
//            mBM.destroy();
//        }
//        mCacheBitmap = false;
//
//        mInitType = InitType.FIT_VIEW_MIN;
//
//        mDoubleType = DoubleType.FIT_VIEW_MIN_VIEW_MAX;
//    }

    public void setImage(Bitmap bitmap)
    {
        setImage(bitmap, false);
    }

    /**
     * Bitmap 转换为 InputStream, 使用 BitmapRegionDecoder 管理
     * @param bitmap 图片
     * @param cache 是否需要将bitmap 保存为文件再 转换为InputStream
     */
    public void setImage(Bitmap bitmap, boolean cache)
    {
        mBM.setBitmap(bitmap, cache);
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
            setImage((InputStream) null, config);
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            setImage(fis, config);
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            onSetImageFinished(null, false, new Rect());
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
        mBM.setInputStream(is, config);
    }

    /**
     * 设置监听
     * @param listener action listener
     */
    public void setActionListener(OnActionListener listener)
    {
        mActionListener = listener;
        mGestureManager.setActionListener(listener);
    }

    /**
     * 缩放到指定的大小, 起始是以当前的大小为准
     * 并且以屏幕中心进行缩放
     * @param dest 目标缩放大小
     * @param smooth  是否动画
     * @param smoothTime 动画时间
     */
    public void scaleImage(float dest, boolean smooth, int smoothTime)
    {
        if (mBM != null) {
            mBM.scaleTo(dest, smooth, smoothTime);
        }
    }

    /**
     * 以一点为中心缩放图片
     * @param cx 中心点
     * @param cy 中心点
     * @param dest 缩放的目标倍数， 这是以当前的放大倍数来计算的
     * @param smooth 是否使用动画
     * @param smoothTime 动画时间
     */
    public void scaleImage(int cx, int cy, float dest, boolean smooth, int smoothTime)
    {
        if (mBM != null) {
            mBM.scaleTo(cx, cy, dest, smooth, smoothTime);
        }
    }

    /**
     * 放大到最大适应View（就是图片宽高 &gt;= View的宽高）
     * @param cx 中心点
     * @param cy 中心点
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    public void scaleToFitViewMax(int cx, int cy, boolean smooth, int smoothTime)
    {
        if (mBM != null) {
            mBM.scaleToFitViewMax(cx, cy, smooth, smoothTime);
        }
    }

    /**
     * 放大到最小适应View (就是图片宽高 &lt;= View的宽高)
     * @param cx 中心点
     * @param cy 中心点
     * @param smooth 动画
     * @param smoothTime 动画时间
     */
    public void scaleToFitViewMin(int cx, int cy, boolean smooth, int smoothTime)
    {
        if (mBM != null) {
            mBM.scaleToFitViewMin(cx, cy, smooth, smoothTime);
        }
    }

    /**
     * 滑动图片, 返回当前已经到达的边界
     * LEFT
     * RIGHT
     * TOP
     * BOTTOM
     * @param dx x轴滑动的像素
     * @param dy y轴滑动的像素
     * @return 当已经到达的边界的 与 值
     */
    public int scrollImage(int dx, int dy)
    {
        return (mBM != null) ? mBM.move(dx, dy) : BitmapManager.NONE;
    }


    /**
     * 获取当前图片的缩放的倍数
     * @return 放大的倍数, 相对图片的原始图片的尺寸来说的
     */
    public float getScaleFactor()
    {
        return (mBM != null) ? mBM.getCurScaleFactor() : 0f;
    }

    /**
     * 获取真实图片的尺寸，注意最好在 onSetImageFinished() 之后获取这个值
     * @return Rect
     */
    public Rect getRealImageRect()
    {
        return (mBM != null) ? mBM.getRealImageRect() : new Rect();
    }

    /**
     * 获取当前显示出来的图片的尺寸
     * @return Rect
     */
    public Rect getCurImageRect()
    {
        return (mBM != null) ? mBM.getCurImageRect() : new Rect();
    }

    /**
     * 判断是否正在设置图片
     * @return Rect
     */
    public boolean isSettingImage()
    {
        return (mBM != null) && mBM.isSettingImage();
    }

    /**
     * 设置初始化类型
     * @param type init type
     */
    public void setInitType(InitType type)
    {
        mInitType = type == null ? InitType.FIT_VIEW_MIN : type;
    }

    /**
     * 设置双击缩放的缩放方式, 默认为 fitView
     * @param type fit type
     */
    public void setDoubleTapScaleType(DoubleType type)
    {
        mDoubleType = (type == null) ? DoubleType.FIT_VIEW_MIN_VIEW_MAX : type;
    }

    @Override
    public XImageView getInstance()
    {
        return this;
    }

    /**
     * 是否劫持输入事件, ViewPager
     */
    @Override
    public void interceptParentTouchEvent(boolean intercept)
    {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(intercept);
        }
    }

    @NonNull
    @Override
    public File getCacheDir()
    {
        return getContext().getCacheDir();
    }

    @Override
    public void callInvalidate()
    {
        invalidate();
    }

    @Override
    public void callPostInvalidate()
    {
        postInvalidate();
    }

    @Override
    public void callPost(Runnable runnable)
    {
        removeCallbacks(runnable);
        post(runnable);
    }

    @Override
    public Bitmap.Config getBitmapConfig()
    {
        return Bitmap.Config.RGB_565;
    }

    @Override
    public boolean enableCache()
    {
        return false;
    }

    @Override
    public boolean initAnimation()
    {
        return false; // TODO
    }

    @Override
    public InitType getInitType()
    {
        return mInitType;
    }

    @Override
    public DoubleType getDoubleType()
    {
        return mDoubleType;
    }

    @Override
    public boolean enableScaleOver()
    {
        return false; // TODO
    }

    @Override
    public void onSetImageFinished(BitmapManager bm, boolean success, Rect image)
    {
        if (mActionListener != null && bm == mBM) {
            mActionListener.onSetImageFinished(this, success, image);
        }
    }


    public interface OnActionListener
    {
        /**
         * 在View上点击了一次（而且没有双击）
         * @param view XImageView
         * @param event motion event
         * @param onImage 是否点击在了有效的图片上
         */
        void onSingleTapped(XImageView view, MotionEvent event, boolean onImage);

        /**
         * 双击
         * @param view XImageView
         * @param event motion event
         * @return boolean 是否已经进行了处理
         */
        boolean onDoubleTapped(XImageView view, MotionEvent event);

        /**
         * 长按
         * @param view XImageView
         * @param event motion event
         */
        void onLongPressed(XImageView view, MotionEvent event);

//        /**
//         * 当开始设置图片时或者当转屏或者view尺寸发生变化时
//         * （即需要重新设置图片时）回调此方法
//         * @param view XImageView
//         */
//        void onSetImageStart(XImageView view);

        /**
         * 初始化完成，图片已经显示
         * 返回是否成功，并返回图片的尺寸
         * @param view XImageView
         * @param success 是否设置成功
         * @param image 图片的实际大小
         */
        void onSetImageFinished(XImageView view, boolean success, Rect image);
    }

    public static class SimpleActionListener implements OnActionListener
    {
        @Override
        public void onSingleTapped(XImageView view, MotionEvent event, boolean onImage)
        {

        }

        @Override
        public boolean onDoubleTapped(XImageView view, MotionEvent event)
        {
            return false;
        }

        @Override
        public void onLongPressed(XImageView view, MotionEvent event)
        {

        }
//
//        @Override
//        public void onSetImageStart(XImageView view)
//        {
//
//        }

        @Override
        public void onSetImageFinished(XImageView view, boolean success, Rect image)
        {

        }
    }


//    private int dpToPx(float dp)
//    {
//        return (int) (dp * mDisplayDensity + 0.5f);
//    }
//
//    private int pxToDp(float px)
//    {
//        return (int) (px / mDisplayDensity + 0.5f);
//    }

}