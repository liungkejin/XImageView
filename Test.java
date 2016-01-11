/*
 * Copyright (c) 2015 Shanghai Sweetyi Information Technology Co. Ltd.  All Rights Reserved
 * 版权声明 (c) 2015 上海甜邑信息技术有限公司. 版权所有! 保留最终解释权.
 */

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
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/10
 */

/**
 * 能显示大长图
 * TODO: 1. 优化Loading Bitmap
 * TODO: 2. 双击
 * TODO: 3. 优化缩放
 * TODO: 4. 定义各种样式
 */
public class SuperImageView extends View
{
    public final static String TAG = "SuperImageView";

    /**
     * Gesture Detector
     */
    private SimpleGestureDetector mSimpleDetector = null;


    private BitmapManager mBitmapManager = null;



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

        mSimpleDetector = new SimpleGestureDetector(context, mGestureListener);
    }

    public void setInputStream(InputStream is)
    {
        mBitmapManager = new BitmapManager(is, null);
        invalidate();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        mSimpleDetector.onTouchEvent(event);

        /**
         * 到达边界时返回false
         */
        return true;
    }

    /**
     * 当前要Draw的状态，
     * 1. 显示完全（缩略图）
     * 2. 手指放大，拖动查看
     * 3. 放大或者缩小到适应屏幕，
     *
     * 如果状态为1, 双击后状态为3,
     * 如果状态为3，双击后状态为1，
     * 1,3状态都可以是 2 的初始状态
     * 在2状态双击会回到状态1
     */
    private int mDrawState = STATE_THUMB;

    private final static int STATE_THUMB    = 0x11;
    private final static int STATE_NORMAL   = 0x12;
    private final static int STATE_FULL     = 0x13;

    @Override
    protected void onDraw(Canvas canvas)
    {
        if (mBitmapManager == null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        mBitmapManager.setViewRect(width, height);

        mBitmapManager.drawVisibleBitmap(canvas);

        switch (mDrawState) {
            case STATE_THUMB:
                drawFullState(canvas);
                break;

            case STATE_NORMAL:
                drawNormalState(canvas);
                break;

            case STATE_FULL:
                break;

        }

    }


    /**
     * 画缩略图
     */
    private void drawFullState(Canvas canvas)
    {
    }


    private void drawNormalState(Canvas canvas)
    {
        //
    }

    /**
     * 手势监听回调
     */
    private SimpleGestureDetector.GestureListener
            mGestureListener = new SimpleGestureDetector.GestureListener()
    {
        @Override
        public void onTapped(int x, int y)
        {
            Log.e(TAG, "On Tapped: X: " + x + " Y: " + y);
        }

        @Override
        public void onDoubleClicked(int x, int y)
        {
            Log.e(TAG, "On Double Clicked: X: " + x + " Y: " + y);

            if (mBitmapManager == null) {
                return;
            }

            /**
             * TODO: 添加动画
             */
            switch (mDrawState) {
                case STATE_FULL:
                case STATE_NORMAL:
                    mDrawState = STATE_THUMB;
                    invalidate();
                    break;

                case STATE_THUMB:
                    mDrawState = STATE_FULL;
                    invalidate();
                    break;
            }
        }

        @Override
        public void onMoving(int preX, int preY, int x, int y, int dx, int dy)
        {
            if (mBitmapManager == null) {
                return;
            }

            mDrawState = STATE_NORMAL;

            mBitmapManager.offset(dx, dy);

            invalidate();
        }

        private int mFocusX = 0;
        private int mFocusY = 0;

        @Override
        public boolean onScale(ScaleGestureDetector detector, int state)
        {
            if (mBitmapManager == null) {
                return true;
            }

            mDrawState = STATE_NORMAL;
            /**
             * 始终以当前显示区域的中心点为中心进行缩放
             */
            float factor = detector.getScaleFactor();
            switch (state) {
                case STATE_BEG:
                    mFocusX = (int) detector.getFocusX();
                    mFocusY = (int) detector.getFocusY();
                    break;

                case STATE_ING:
                    mFocusX = (int) detector.getFocusX();
                    mFocusY = (int) detector.getFocusY();
                    mBitmapManager.scale(mFocusX, mFocusY, factor);
                    invalidate();
                    break;

                case STATE_END:
                    /**
                     * 更新bitmap
                     */
                    mBitmapManager.updateBitmap();
                    invalidate();
                    break;
            }

            return true;
        }
    };

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
    }


    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }


    public class BitmapManager
    {
        private final static String TAG = "BitmapManager";

        private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;

        private BitmapRegionDecoder mDecoder = null;

        private InputStream mInputStream = null;


        /**
         * 当前图片的的采样率
         */
        private int mSampleSize = 0;

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
         * 实际bitmap的宽高, 这个宽高就是 原图设置采样率后的宽高
         */
        private Rect mRealBitmapRect = new Rect();


        /**
         * @param file 图片文件
         */
        public BitmapManager(File file, Bitmap.Config config)
        {
            mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
            setImageFromFile(file, config);
        }

        public BitmapManager(Bitmap bitmap, Bitmap.CompressFormat format, Bitmap.Config config, boolean needCache)
        {
            mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
            setImageFromBitmap(bitmap, format, config, needCache);
        }

        public BitmapManager(InputStream inputStream, Bitmap.Config config)
        {
            mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
            setImageFromInputStream(inputStream, config);
        }


        /**
         * 相对移动 view
         */
        public void offset(int dx, int dy)
        {
            offsetShowBitmap(dx, dy);
        }

        /**
         * 以 (cx, cy) 为中心缩放
         */
        public void scale(int cx, int cy, float sc)
        {
            sc *= (sc < 1) ? 0.985f : 1.015f;
            scaleShowBitmap(cx, cy, sc);
        }

        /**
         * 当缩放结束后，需要重新解码最新的bitmap
         */
        public void updateBitmap()
        {
            updateRealBitmapRect();
        }

        /**
         * 设置视图的宽和高
         * 每次都重新初始化一次
         */
        public void setViewRect(int w, int h)
        {
            Log.e(TAG, "Before Time: " + System.currentTimeMillis());
            setView(w, h);
//        debug();
            Log.e(TAG, "After Time: " + System.currentTimeMillis());
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

            drawRegionBitmap(canvas);
        }


        private BitmapGrid mBitmapGrid = new BitmapGrid();

        private class BitmapUnit
        {
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
                if (mBitmap != null) {
                    mBitmap.recycle();
                }
                mBitmap = null;
                mCurSampleSize = 0;
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

            private LoadingThread mLoadingThread = new LoadingThread();

            /**
             * 缩略图时的 SampleSize
             */
            private int mThumbSampleSize = 0;


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
             * 获取bitmap
             */
            private Bitmap getGridBitmap(int n, int m)
            {
                if (isValidGrid(n, m)) {

                    if (mGrids[n][m].mCurSampleSize != mSampleSize) {
                        mLoadingThread.loadUnitBitmap(n, m);
                    }

                    if (mSampleSize == mThumbSampleSize) {
                        return mGrids[n][m].mThumbBitmap;
                    }

                    return mGrids[n][m].mBitmap != null &&
                            !mGrids[n][m].mBitmap.isRecycled() ?
                            mGrids[n][m].mBitmap : mGrids[n][m].mThumbBitmap;
                }

                return null;
            }

            private void initializeBitmapGrid()
            {
                if (mGrids != null) {
                    for (BitmapUnit [] rows : mGrids) {
                        if (rows == null) {
                            continue;
                        }
                        for (BitmapUnit unit : rows) {
                            if (unit != null) {
                                unit.recycleAll();
                            }
                        }
                    }
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
                 * 初始化缩略图
                 * 保存缩略图时的Sample值
                 */
                mThumbSampleSize = mSampleSize;
                /**
                 * 异步加载缩略图
                 */
                mLoadingThread.loadThumbBitmap();
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
             * @param n
             * @param m
             * @return
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
             * 获取显示的单元格
             * @param n
             * @param m
             * @return
             */
            private Rect getShowBitmapUnit(int n, int m)
            {
                if (!isValidGrid(n, m)) {
                    return null;
                }

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
                for (int i = 0; i < mN; ++i) {
                    for (int j = 0; j < mM; ++j) {
                        if (sn - i >= 1 || i - en >= 1
                                || sm - j >= 1 || j - em >= 1) {
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
                Log.e(TAG, "Before Drawing: " + System.currentTimeMillis());
                if (mGrids == null) {
                    return;
                }

                Rect visible = getVisibleGrid();
                Log.e(TAG, "Before Recycler: " + System.currentTimeMillis());
                recycleInvisibleGrids(visible);
                Log.e(TAG, "After Recycler: " + System.currentTimeMillis());

                int sn = visible.top;
                int sm = visible.left;
                int en = visible.bottom;
                int em = visible.right;

                for (int n = sn; n <= en; ++n) {
                    for (int m = sm; m <= em; ++m) {
                        Rect rect = getShowBitmapUnit(n, m);
                        Bitmap bitmap = getGridBitmap(n, m);
                        if (rect != null && bitmap != null) {
                            Rect vRect = toViewCoordinate(rect);
                            canvas.drawBitmap(bitmap, null, vRect, null);

                        mPaint.setColor(Color.MAGENTA);
                        mPaint.setStrokeWidth(2);
                        canvas.drawRect(vRect, mPaint);
                        }
                    }
                }

                Log.e(TAG, "After Drawing: " + System.currentTimeMillis());
            }


            /**
             * 管理所有的单元格的 bitmap 加载
             */
            private class LoadingThread implements Runnable
            {
                /**
                 * 当前加载的动作
                 * 0: 没有动作
                 * 1: 进行解码队列中的单元格
                 * 2: 进行解码所有的单元格的缩略图
                 */
                private int mLoadAction = 0;
                private final static int ACTION_NONE = 0;
                private final static int ACTION_LOAD = 1;
                private final static int ACTION_INIT = 2;

                private Deque<Point> mLoadingQue = new ArrayDeque<>();

                public synchronized void loadUnitBitmap(int n, int m)
                {
                    Log.e(TAG, "Add Task " + n + " , " + m);
                    synchronized (this) {
                        boolean canAdd = true;
                        /**
                         * 判断这个单元格是否已经在队里中
                         */
                        for (Point p : mLoadingQue) {
                            if (p.equals(n, m)) {
                                canAdd = false;
                                break;
                            }
                        }
                        if (canAdd) {
                            mLoadingQue.addLast(new Point(n, m));

                            Log.e(TAG, "add success: Que Size " + mLoadingQue.size());
                        }
                    }

                    /**
                     * 如果当前没有在解码缩略图，而且没有在解码队列中的单元格，就开启线程
                     */
                    if (mLoadAction == ACTION_NONE &&
                            !mLoadingQue.isEmpty()) {
                        startAction(ACTION_LOAD);
                    }
                }

                public synchronized void loadThumbBitmap()
                {
                    startAction(ACTION_INIT);
                }

                private synchronized void startAction(int action)
                {
                    if (action == ACTION_NONE) {
                        stopThread();
                        return;
                    }

                    if (mLoadAction != ACTION_NONE) {
                        mLoadAction = action;
                        return;
                    }

                    mLoadAction = action;
                    new Thread(this).start();
                }

                private synchronized void stopThread()
                {
                    mLoadAction = ACTION_NONE;
                }

                @Override
                public void run()
                {
                    while (mLoadAction != ACTION_NONE) {
                        switch (mLoadAction) {
                            case ACTION_INIT:
                                decodeThumbUnitBitmap();
                                postInvalidate();
                                break;

                            case ACTION_LOAD:
                                while (!mLoadingQue.isEmpty()) {
                                    synchronized (this) {
                                        Point point = mLoadingQue.poll();
                                        decodeVisibleUnitBitmap(point.x, point.y);
                                    }
                                }
                                Log.e(TAG, "add success: Que Size " + mLoadingQue.size());
                                postInvalidate();
                                break;

                            default:
                                //
                                break;
                        }

                        try {
                            Thread.sleep(50);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    mLoadAction = ACTION_NONE;

                }
            }

            /**
             * decode出一个可见单元的bitmap
             * 并保存这个bitmap的 sample size
             */
            private void decodeVisibleUnitBitmap(int n, int m)
            {
                if (isValidGrid(n, m) && isVisibleUnit(n, m)) {
                    Rect rect = getUnitRect(n, m);
                    if (mGrids[n][m].mBitmap != null) {
                        mGrids[n][m].recycle();
                    }
                    mGrids[n][m].mCurSampleSize = mSampleSize;

                    if (mSampleSize != mThumbSampleSize) {
                        mGrids[n][m].mBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                    }
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
            return mRealBitmapRect.height() * 1f / mImageRect.height();
        }

        /**
         * 计算真实bitmap和显示的bitmap之间的比例
         */
        private float getRealShowRatio()
        {
            return mRealBitmapRect.height() * 1f / mShowBitmapRect.height();
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
            Log.e(TAG, "Virtual View Rect: " + vRect);

            int vw = vRect.width();
            int vh = vRect.height();

            Rect visibleBitmapRect = getVisibleRealBitmapRect();
            Log.e(TAG, "Visible Real Bitmap Rect: " + visibleBitmapRect);

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

        /**
         *
         */
        private void drawRegionBitmap(Canvas canvas)
        {
            mBitmapGrid.drawVisibleGrid(canvas);

            /**
             * For Debug
             */
//        debug(canvas);
        }

        /**
         * 管理视窗 和 bitmap (显示的和真实的)
         */
        /*****************************************************************/

        /**
         ***************************************************************
         **/

        private void setView(int vw, int vh)
        {
            if (mDecoder == null) {
                return;
            }

            if (mImageRect.width() > 0 && mImageRect.height() > 0 &&
                    vw > 0 && vh > 0 && vw == mViewRect.width() && vh == mViewRect.height()) {
                return;
            }

            mViewRect.set(0, 0, vw, vh);
            Log.e(TAG, "ViewRect: " + mViewRect);

            /**
             * 计算要缩放的比例
             */
            int iw = mImageRect.width();
            int ih = mImageRect.height();
            int width = (int) (iw * 1.0f / ih * vh);
            float ratio =  (width > vw) ?  (iw * 1f / vw) : (ih * 1f / vh);

            Log.e(TAG, "ratio: " + ratio);

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

            Log.e(TAG, "ShowBitmap: " + mShowBitmapRect);

            /**
             * 设置为正中间
             */
            int left = (mShowBitmapRect.width() - mViewRect.width())/2;
            int right = left + mViewRect.width();
            int top = (mShowBitmapRect.height() - mViewRect.height())/2;
            int bottom = top + mViewRect.height();
            mViewBitmapRect.set(left, top, right, bottom);

            updateRealBitmapRect();

            /**
             * 初始化
             */
            mBitmapGrid.initializeBitmapGrid();
        }

        /**
         * 当缩放结束后，需要重新解码最新的bitmap
         */
        private void updateRealBitmapRect()
        {
            int iw = mImageRect.width();
            int ih = mImageRect.height();
            int bw = mShowBitmapRect.width();
            int bh = mShowBitmapRect.height();

            if (mDecoder == null ||
                    iw == 0 || ih == 0 || bw <= 0 || bh <= 0) {
                return;
            }

            /**
             * 以 bitmap 的宽高为标准
             * 分别以 宽高为标准，计算对应的的宽高
             * 如果是宽图, 则以View的宽为标准
             * 否则为高图， 以view的高为标准
             * 求出 SampleSize
             */
            int width = (int) (iw * 1.0f / ih * bh);
            int sampleSize = (width > bw)? getSampleSize(iw / bw) : getSampleSize(ih / bh);
            if (sampleSize < 1) {
                sampleSize = 1;
            }

            if (sampleSize == mSampleSize) {
                return;
            }
            mSampleSize = sampleSize;

            /**
             * 获取 这个sampleSize 时的真实的 bitmap的宽高
             */
            Log.e(TAG, "Before Just Bounds: " + System.currentTimeMillis());
            BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
            tmpOptions.inPreferredConfig = mBitmapConfig;
            tmpOptions.inSampleSize = mSampleSize;
            tmpOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeStream(mInputStream, null, tmpOptions);

            mRealBitmapRect.set(0, 0, tmpOptions.outWidth, tmpOptions.outHeight);
            Log.e(TAG, "After Just Bounds: " + System.currentTimeMillis());
            Log.e(TAG, "RealBitmapRect: " + mRealBitmapRect);
            Log.e(TAG, "-----------------------------------------------");
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

            Log.e(TAG, "Center (" + cx + " , " + cy + ")");
            Log.e(TAG, "oRect: " + oRect);

            /**
             * 以cx, cy缩放
             */
            float left = (cx - Math.abs(cx - oRect.left) * sc);
            float right = (cx + Math.abs(oRect.right - cx) * sc);

            float top = (cy - Math.abs(cy - oRect.top) * sc);
            float bottom = ((right - left) * getImageRatio() + top);

            RectF nRect = new RectF(left, top, right, bottom);
            Log.e(TAG, "nRect: " + nRect);

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
            mShowBitmapRect.set(0, 0, (int)rect.width(), (int) rect.height());
        }

        /**
         * 移动,
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

            if (rx == Integer.MAX_VALUE) {
                return 1;
            }
            if (ry == Integer.MAX_VALUE) {
                return 2;
            }

            return 0;
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
         * 获取真实bitmap 和原图之间宽的比例
         */
        private float getImageWidthRatio()
        {
            return mImageRect.width() * 1f / mRealBitmapRect.width();
        }

        /**
         * 获取真实 bitmap 和原图的高的比例
         */
        private float getImageHeightRatio()
        {
            return mImageRect.height() * 1f / mRealBitmapRect.height();
        }

        /**
         * height / width
         */
        private float getImageRatio()
        {
            return mImageRect.height() * 1f / mImageRect.width();
        }

        private void debug(Canvas canvas)
        {
            mPaint.setColor(Color.WHITE);
            canvas.drawRect(toViewCoordinate(mViewBitmapRect), mPaint);
            mPaint.setColor(Color.RED);
            canvas.drawRect(toViewCoordinate(mShowBitmapRect), mPaint);
            mPaint.setColor(Color.GREEN);
            canvas.drawRect(toViewCoordinate(mRealBitmapRect), mPaint);

//            mPaint.setColor(Color.YELLOW);
//            canvas.drawRect(toViewCoordinate(getVisibleShowBitmapRect()), mPaint);

//            mPaint.setColor(Color.WHITE);
//            canvas.drawRect(toViewCoordinate(getVisibleRealBitmapRect()), mPaint);
        }

        /*****************************************************************/
        /**
         * 从解码出一块bitmap
         */
        public Bitmap decodeRectBitmap(Rect rect, int sampleSize)
        {
            if (rect == null) {
                return null;
            }

            Log.e(TAG, "Before Decode: "  + System.currentTimeMillis());
            BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
            tmpOptions.inPreferredConfig = mBitmapConfig;
            tmpOptions.inSampleSize = sampleSize;
            tmpOptions.inJustDecodeBounds = false;

            Bitmap bitmap = mDecoder.decodeRegion(rect, tmpOptions);

            Log.e(TAG, "Rect: " + rect);
            Log.e(TAG, "Bitmap: W( " + bitmap.getWidth() + ", " + bitmap.getHeight()+ ")");
            Log.e(TAG, "After Decode: "  + System.currentTimeMillis());

            return bitmap;
        }

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
        private int getSampleSize(int size)
        {
            int sample = 1;
            while ((size / 2) != 0) {
                size /= 2;
                sample *= 2;
            }

            return sample;
        }


        /**
         * Bitmap 转换为 InputStream, 使用 BitmapRegionDecoder 管理
         * TODO：实现 cache
         * @param bitmap 图片
         * @param format 解码的格式
         * @param needCache 是否需要进行文件缓存（这样如果bitmap太大，可以节约内存）
         */
        public void setImageFromBitmap(Bitmap bitmap,
                                       Bitmap.CompressFormat format,
                                       Bitmap.Config config, boolean needCache)
        {
            if (bitmap == null) {
                return;
            }
            format = format == null ? Bitmap.CompressFormat.PNG : format;

            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                bitmap.compress(format, 100, os);
                ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());

                setImageFromInputStream(is, config);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void setImageFromFile(File file, Bitmap.Config config)
        {
            if (file == null || !file.exists()) {
                return;
            }

            try {
                FileInputStream fis = new FileInputStream(file);
                setImageFromInputStream(fis, config);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void setImageFromInputStream(InputStream is, Bitmap.Config config)
        {
            if (is == null) {
                return;
            }

            try {
                mDecoder = BitmapRegionDecoder.newInstance(is, false);

                BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
                tmpOptions.inPreferredConfig = config == null ? Bitmap.Config.RGB_565 : config;
                tmpOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, tmpOptions);

                mImageRect.set(0, 0, tmpOptions.outWidth, tmpOptions.outHeight);

                mInputStream = is;
                Log.e(TAG, "ImageRect: " + mImageRect);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
