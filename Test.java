package cn.kejin.android.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/10
 */

/**
 * 能显示大长图
 * TODO: 双击操作
 * TODO: 限制缩放等级
 * TODO: 到边界返回false
 * TODO: 自定义初始化状态
 * TODO: 滑动惯性
 * TODO: 优化代码
 * TODO: 显示正在加载， 并增加接口
 */
public class SuperImageView extends View
{
    public final static String TAG = "SuperImageView";

    private final static String THREAD_NAME = "SuperImageLoad";

    private final Object mQueueLock = new Object();

    /**
     * Gesture Detector
     */
    private SimpleGestureDetector mSimpleDetector = null;


    private BitmapManager mBitmapManager = new BitmapManager();

    /**
     * 当前的处理动作, 为了保证能获取到正确的 view Width, Height
     * 将处理过程都放在到 onDraw() 中
     */
    private int mProcessAction = PA_NONE;

    private static final int PA_NONE = 237;

    /**
     * 初始化，当设置了新的图片后，执行此动作，重新初始化
     */
    private static final int PA_SET_VIEW = 435;

    /**
     * 刷新一次动作
     */
    private static final int PA_FRESH = 336;

    /**
     * 异步处理图片的解码
     */
    private Handler mLoadingHandler = null;
    private HandlerThread mLoadingThread = null;

    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }

    public SuperImageView(Context context)
    {
        super(context);
        initialize();
    }

    public SuperImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initialize();
    }

    public SuperImageView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize()
    {
        Context context = getContext();

        mSimpleDetector = new SimpleGestureDetector(context, mBitmapManager);

        mLoadingThread = new HandlerThread(THREAD_NAME);
        mLoadingThread.start();

        mLoadingHandler = new Handler(mLoadingThread.getLooper());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event)
    {

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        mSimpleDetector.onTouchEvent(event);
        /**
         * TODO: 到达边界时返回false
         */
        return true; //(event.getPointerCount() == 1 && mBitmapManager.isReachedBorder());
    }

    @Override
    protected void onDraw(Canvas canvas)
    {

        mBitmapManager.setView(getWidth(), getHeight());
        mBitmapManager.drawVisibleBitmap(canvas);
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
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();

        mBitmapManager.recycleAll();
    }


    public void setImage(Bitmap bitmap,
                         Bitmap.CompressFormat format)
    {
        setImage(bitmap, format, Bitmap.Config.RGB_565);
    }

    /**
     * Bitmap 转换为 InputStream, 使用 BitmapRegionDecoder 管理
     * @param bitmap 图片
     * @param format 解码的格式
     */
    public void setImage(Bitmap bitmap,
                         Bitmap.CompressFormat format,
                         Bitmap.Config config)
    {
        if (bitmap == null) {
            return;
        }
        format = format == null ? Bitmap.CompressFormat.PNG : format;

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            bitmap.compress(format, 100, os);
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());

            setImage(is, config);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
        if (is == null) {
            return;
        }

        mBitmapManager.setImage(is, config);
    }


    private class BitmapManager implements SimpleGestureDetector.GestureListener
    {
        private final static String TAG = "BitmapManager";

        private BitmapRegionDecoder mDecoder = null;

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
         */
        private Rect mImageRect = new Rect();

        /**
         * 视图的宽高
         */
        private Rect mViewRect = new Rect();

        /**
         * 视图相对bitmap（mShowBitmapRect）坐标系的 rect
         */
        private Rect mViewBitmapRect = new Rect();

        /**
         * 需要显示的bitmap的宽高, 就是真实需要显示的宽高，
         * 从 mDecoder中出来的 bitmap 需要 缩放为 此宽高
         * 在计算的时候，需要缩放比例来
         */
        private Rect mShowBitmapRect = new Rect();

        /**
         * 用于缩放的 rect
         */
        private RectF mShowBitmapRectF = new RectF();

        /**
         * 缩略图是的 rect
         */
        private Rect mThumbShowBitmapRect = new Rect();

        /**
         * Bitmap 网格
         */
        private BitmapGrid mBitmapGrid = new BitmapGrid();


        /**
         * 实例化 BitmapRegionDecoder, 并拿到原图的宽高
         */
        private void setImage(final InputStream is, Bitmap.Config config)
        {
            if (is == null) {
                return;
            }

            mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;

            long time = System.currentTimeMillis();

            mLoadingHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        mDecoder = BitmapRegionDecoder.newInstance(is, false);
                        mImageRect.set(0, 0, mDecoder.getWidth(), mDecoder.getHeight());

                        Log.e(TAG, "ImageRect: " + mImageRect);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        mDecoder = null;
                        mImageRect.set(0, 0, 0, 0);
                    }

                    if (mDecoder != null) {
                        postInvalidate();
                    }
                }
            });

            Log.e(TAG, "Instance Time: " + (System.currentTimeMillis() - time));
        }

        /**
         * 设置视图的尺寸, 并初始化其他相关尺寸
         * @param vw view width
         * @param vh view height
         */
        private void setView(int vw, int vh)
        {
            if (mDecoder == null ||
                    (vw == mViewRect.width() && vh == mViewRect.height())) {
                return;
            }

            mViewRect.set(0, 0, vw, vh);

            /**
             * 计算要缩放的比例
             */
            int iw = mImageRect.width();
            int ih = mImageRect.height();
            int width = (int) (iw * 1.0f / ih * vh);
            float ratio =  (width > vw) ? (iw * 1f / vw) : (ih * 1f / vh);

            /**
             * 如果要放大显示，就不用缩放了
             */
            ratio = ratio < 1 ? 1f : ratio;
            mShowBitmapRect.set(0, 0, (int) (iw / ratio), (int) (ih / ratio));
            mShowBitmapRectF.set(mShowBitmapRect);

            /**
             * 保存初始大小
             */
            mThumbShowBitmapRect.set(mShowBitmapRect);

            /**
             * 设置为正中间
             */
            int left = (mShowBitmapRect.width() - mViewRect.width())/2;
            int right = left + mViewRect.width();
            int top = (mShowBitmapRect.height() - mViewRect.height())/2;
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
         * 移动, 相对移动 view
         * @param dx
         * @param dy
         */
        private int offsetShowBitmap(int dx, int dy)
        {
            Rect oRect = toViewCoordinate(mShowBitmapRect);

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
            invalidate();

            if (rx == Integer.MAX_VALUE) {
                return 1;
            }
            if (ry == Integer.MAX_VALUE) {
                return 2;
            }

            return 0;
        }

        /**
         * 缩放显示的 bitmap,
         * @param cx 中心点的 x
         * @param cy 中心点的 y
         * @param sc 缩放系数
         */
        private void scaleShowBitmap(int cx, int cy, float sc)
        {
            /**
             * 如果图片的长或宽，全在视图内，则以中线进行缩放
             */
            RectF oRect = toViewCoordinate(mShowBitmapRectF);

            /**
             * 如果宽全在视图内
             */
            if (oRect.left > 0 && oRect.right < mViewRect.right) {
                cx = mViewRect.centerX();
            }

            /**
             * 如果高全在视图内
             */
            if (oRect.top > 0 && oRect.bottom < mViewRect.bottom) {
                cy = mViewRect.centerY();
            }

            /**
             * 以cx, cy缩放
             */
            float left = (cx - Math.abs(cx - oRect.left) * sc);
            float right = (cx + Math.abs(oRect.right - cx) * sc);

            float top = (cy - Math.abs(cy - oRect.top) * sc);
            float bottom = ((right - left) * getImageRatio() + top);

            RectF nRect = new RectF(left, top, right, bottom);

            if (nRect.width() < mThumbShowBitmapRect.width() ||
                    nRect.height() < mThumbShowBitmapRect.height()) {
                resetShowBitmapRect();
                return;
            }

            /**
             * 更新ViewBitmapRect坐标, 并更新显示的bitmap rect 大小
             */
            updateViewBitmapRect(nRect);

            /**
             * 如果还是小于视图宽度，则需要移动到正正中间
             */
            float nx = 0;
            float ny = 0;
            RectF aRect = toViewCoordinate(mShowBitmapRectF);
            if (aRect.width() < mViewRect.width()) {
                nx = mViewRect.centerX() - aRect.centerX();
            }
            else {
                if (aRect.left > 0) {
                    nx = -aRect.left;
                }
                else if (aRect.right < mViewRect.width()) {
                    nx = mViewRect.width() - aRect.right;
                }
            }

            if (aRect.height() < mViewRect.height()) {
                ny = mViewRect.centerY() - aRect.centerY();
            }
            else {
                if (aRect.top > 0) {
                    ny = -aRect.top;
                }
                else if (aRect.bottom < mViewRect.height()) {
                    ny = mViewRect.height() - aRect.bottom;
                }
            }

            aRect.offset(nx, ny);
            updateViewBitmapRect(aRect);

            Log.e(TAG, "After Scale Rect: " + nRect + " Show: " + mShowBitmapRect);
            Log.e(TAG, "Visible Rect: " + mViewBitmapRect);

            invalidate();
        }

        /**
         * 重置为初始化状态
         */
        private void resetShowBitmapRect()
        {
            if (mShowBitmapRect == mThumbShowBitmapRect) {
                return;
            }

            mShowBitmapRect.set(mThumbShowBitmapRect);
            mShowBitmapRectF.set(mShowBitmapRect);

            int left = (mShowBitmapRect.width() - mViewRect.width())/2;
            int right = left + mViewRect.width();
            int top = (mShowBitmapRect.height() - mViewRect.height())/2;
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
            vRect.left -= rect.left;
            vRect.right = vRect.left + mViewRect.width();
            vRect.top -= rect.top;
            vRect.bottom = vRect.top + mViewRect.height();

            mViewBitmapRect.set(vRect);
            mShowBitmapRectF.set(0, 0, rect.width(), rect.height());
            mShowBitmapRect.set(0, 0, (int) rect.width(), (int) rect.height());
        }

        /**
         * 判断是否已经在边界, 这也可以判断是那个方向到达了边界
         */
        private boolean isReachedBorder()
        {
            return mViewBitmapRect.left == 0 || mViewBitmapRect.top == 0 ||
                    mViewBitmapRect.right == mShowBitmapRect.right ||
                    mViewBitmapRect.bottom == mShowBitmapRect.bottom;
        }

        /**
         * 画可见区域的的Bitmap
         * @param canvas 画布
         */
        public void drawVisibleBitmap(Canvas canvas)
        {
            if (mDecoder == null) {
                return;
            }

            mBitmapGrid.drawVisibleGrid(canvas);
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
        public void recycleAll()
        {
            mLoadingThread.quit();
            mBitmapGrid.recycleAllGrids();
            if (mDecoder != null) {
                mDecoder.recycle();
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
            int bw = mShowBitmapRect.width();
            int bh = mShowBitmapRect.height();

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
        private void updateSampleSize()
        {
            int sampleSize = getCurSampleSize();
            if (sampleSize == mSampleSize) {
                return;
            }
            mSampleSize = sampleSize;

            Log.e(TAG, "Current Sample Size: " + mSampleSize);
        }

        /**
         * 真实的 Bitmap 的尺寸就是 原图的 1/sampleSize
         */
        private Rect getRealBitmapRect()
        {
            return rectMulti(mImageRect, 1f/mSampleSize);
        }

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
            int left = Math.max(mShowBitmapRect.left, mViewBitmapRect.left);
            int right = Math.min(mShowBitmapRect.right, mViewBitmapRect.right);
            int top = Math.max(mShowBitmapRect.top, mViewBitmapRect.top);
            int bottom = Math.min(mShowBitmapRect.bottom, mViewBitmapRect.bottom);

            Rect rect = new Rect(left, top, right, bottom);
            Log.e(TAG, "VisibleShowBitmapRect: " + rect);

            return rect;
        }

        /**
         * 计算出可见区域的真实bitmap rect
         */
        private Rect getVisibleRealBitmapRect()
        {
            float ratio = getRealShowRatio();

            return rectMulti(getVisibleShowBitmapRect(), ratio);
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
        public Bitmap decodeRectBitmap(Rect rect, int sampleSize)
        {
            if (mDecoder == null ||
                    rect == null || !mImageRect.contains(rect)) {
                return null;
            }

            BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
            tmpOptions.inPreferredConfig = mBitmapConfig;
            tmpOptions.inSampleSize = sampleSize;
            tmpOptions.inJustDecodeBounds = false;

            return mDecoder.decodeRegion(rect, tmpOptions);
        }

        /********************** Gesture Listener ******************************/

        @Override
        public void onTapped(int x, int y)
        {
            Log.e(TAG, "On Tapped: X: " + x + " Y: " + y);
        }

        @Override
        public void onDoubleClicked(int x, int y)
        {
            Log.e(TAG, "On Double Clicked X: " + x + " Y: " + y);
        }

        @Override
        public void onMoving(int preX, int preY, int x, int y, int dx, int dy)
        {
            offsetShowBitmap(dx, dy);
            invalidate();
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector, int state)
        {
            float factor = detector.getScaleFactor();
            switch (state) {
                case STATE_BEG:
//                    mFocusX = (int) detector.getFocusX();
//                    mFocusY = (int) detector.getFocusY();
                    break;

                case STATE_ING:
                    mBitmapManager.scaleShowBitmap((int) detector.getFocusX(), (int) detector.getFocusY(), factor);
                    invalidate();
                    break;

                case STATE_END:
                    /**
                     * 当缩放结束后，计算最新的的SampleSize, 需要重新解码最新的bitmap
                     */
                    mBitmapManager.updateSampleSize();
                    invalidate();
                    break;
            }

            return true;
        }

        /*****************************************************************/

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

            private void recycleAll()
            {
                if (mBitmap != null) {
                    mBitmap.recycle();
                }

                if (mThumbBitmap != null) {
                    mThumbBitmap.recycle();
                }

                mBitmap = null;
                mThumbBitmap = null;

                mCurSampleSize = 0;
            }

            private void recycle()
            {
                synchronized (this) {
                    if (mBitmap != null) {
                        mBitmap.recycle();
                    }
                    mBitmap = null;
                    mCurSampleSize = mThumbSampleSize;
                }
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

            /**
             * 是否初始化完成了
             */
            private boolean mInitializeFinished = false;


            private synchronized void initializeBitmapGrid()
            {
                mInitializeFinished = false;

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
                mLoadingHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        decodeThumbUnitBitmap();
                        mInitializeFinished = true;
                        postInvalidate();
                    }
                });
            }

            /**
             * 获取bitmap
             */
            private Bitmap getGridBitmap(final int n, final int m)
            {
                if (isValidGrid(n, m)) {

                    if (mSampleSize == mThumbSampleSize) {
                        return mGrids[n][m].mThumbBitmap;
                    }

                    if (mGrids[n][m].mCurSampleSize != mSampleSize) {
                        loadUnitBitmap(n, m);
                    }

                    return mGrids[n][m].mBitmap != null &&
                            !mGrids[n][m].mBitmap.isRecycled() ?
                            mGrids[n][m].mBitmap : mGrids[n][m].mThumbBitmap;
                }

                return null;
            }

            /**
             * 异步就加载单元格bitmap
             */
            private void loadUnitBitmap(final int n, final int m)
            {
                if (mSampleSize != mThumbSampleSize && isValidGrid(n, m)) {
                    synchronized (mQueueLock) {
                        /**
                         * 判断这个单元格是否已经正在加载
                         */
                        if (mGrids[n][m].mIsLoading) {
                            return;
                        }
                        mGrids[n][m].mIsLoading = true;

                        mLoadingHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                synchronized (mQueueLock) {
                                    if (isValidGrid(n, m)) {
                                        mGrids[n][m].mIsLoading = false;
                                    }
                                }

                                decodeVisibleUnitBitmap(n, m);
                                postInvalidate();
                            }
                        });
                    }
                }
            }

            private void recycleBitmap()
            {
                for(int i = 0; i < mN; ++i) {
                    for (int j = 0; j < mM; ++j) {
                        mGrids[i][j].recycle();
                    }
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

                mGrids = null;
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
                Rect vRect = rectMulti(mViewRect, getShowImageRatio());

                int vw = vRect.width();
                int vh = vRect.height();

                int sWidth = mShowBitmapRect.width();
                int sHeight = mShowBitmapRect.height();

                int left = Math.min(m * vw, sWidth);
                int right = Math.min(left + vw, sWidth);

                int top = Math.min(n*vh, sHeight);
                int bottom = Math.min(top + vh, sHeight);

                return new Rect(left, top, right, bottom);
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
            private void drawVisibleGrid(Canvas canvas)
            {
                if (mGrids == null || !mInitializeFinished ||
                        mImageRect.width() <= 0 || mImageRect.height() <= 0) {
                    return;
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
                        synchronized (mGrids[n][m]) {
                            Bitmap bitmap = getGridBitmap(n, m);
                            if (bitmap != null) {
                                Rect vRect = toViewCoordinate(rect);
                                canvas.drawBitmap(bitmap, null, vRect, null);

                                mPaint.setColor(Color.MAGENTA);
                                mPaint.setStrokeWidth(2);
                                canvas.drawRect(vRect, mPaint);
                            }
                        }
                    }
                }
            }

            /**
             * decode出一个可见单元的bitmap
             * 并保存这个bitmap的 sample size
             */
            private synchronized void decodeVisibleUnitBitmap(int n, int m)
            {
                if (isValidGrid(n, m) && isVisibleUnit(n, m)) {
                    mGrids[n][m].recycle();

                    // 防止二次decode
                    if (mGrids[n][m].mCurSampleSize == mSampleSize) {
                        return;
                    }

                    Rect rect = getUnitRect(n, m);
                    mGrids[n][m].mCurSampleSize = mSampleSize;
                    mGrids[n][m].mBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                }
            }

            /**
             * 解码为缩略图的 bitmap
             */
            private void decodeThumbUnitBitmap()
            {
                long before = System.currentTimeMillis();
                for (int n = 0; n < mN; ++n) {
                    for (int m = 0; m < mM; ++m) {
                        Rect rect = getUnitRect(n, m);
                        if (rect != null) {
                            mGrids[n][m].mCurSampleSize = mSampleSize;
                            mGrids[n][m].mThumbBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                        }
                    }
                }
                Log.e(TAG, "Decode Thumb Spend Time: " + (System.currentTimeMillis() - before));
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
                Rect vRect = rectMulti(mViewRect, getRealImageRatio());

                int vw = vRect.width();
                int vh = vRect.height();

                Rect visibleBitmapRect = getVisibleRealBitmapRect();

                /**
                 * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
                 */
                int sm = visibleBitmapRect.left / vw;
                int sn = visibleBitmapRect.top / vh;

                int em = visibleBitmapRect.right / vw;
                int en = visibleBitmapRect.bottom / vh;

                Log.e(TAG, "S(" + sn + ", " + sm + ")" + " E(" + en + ", " + em +")");

                return new Rect(sm, sn, em, en);
            }
        }
    }

    /*****************************************************************/


    /**
     * rect 乘法
     */
    private Rect rectMulti(Rect r, float ratio)
    {
        return new Rect((int)(r.left*ratio), (int)(r.top*ratio),
                        (int)(r.right*ratio), (int) (r.bottom*ratio));
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
         * setImage 之后， 回调此函数, 如果图片很大，则会初始化一段时间
         */
        void onInitializing();

        /**
         * 初始化完成，图片已经显示
         */
        void onInitialzeFinished();
    }
}
