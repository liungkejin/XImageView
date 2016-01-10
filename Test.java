package cn.kejin.android.views;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/30
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Message;
import android.util.Log;

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
 * Bitmap 管理，
 * 将Bitmap 以屏幕的尺寸进行分割成二维数组
 * +--+--+--+--+--+--+
 * |  |  |  |  |  |  |
 * +--+--+--+--+--+--+
 * |  |  |##|  |  |  |
 * +--+--+--+--+--+--+
 * |  |  |  |  |  |  |
 * +--+--+--+--+--+--+
 * |  |  |  |  |  |  |
 * +--+--+--+--+--+--+
 * 每个单元代表Bitmap的一个矩形区域, 为了避免内存占用过大和图片太大超过GL渲染的尺寸
 * 每次只会初始化一个屏幕 和其周围 8 个屏幕的 bitmap, 所以最多一次会渲染 4 个屏幕尺寸的 bitmap
 * 如果一个单元的 bitmap 已经不再需要维护就会主动释放掉，（比如，从[2][1] 滑动到了 [2][2] 单元,
 * 则 [2][0] 单元就可以不再维护，
 */
public class BitmapMatrix
{

    private final static String TAG = "BitmapMatrix";

    /**
     * 当前图片的的采样率
     */
    private int mSampleSize = 0;

    /**
     * 原图的宽高
     */
    private Rect mImageRect = new Rect();

    private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;

    private BitmapRegionDecoder mDecoder = null;

    private final static Paint mPaint = new Paint();
    static {
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
    }


    /**
     * @param file 图片文件
     */
    public BitmapMatrix(File file, Bitmap.Config config)
    {
        mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
        setImageFromFile(file, config);
    }

    public BitmapMatrix(Bitmap bitmap,
                        Bitmap.CompressFormat format,
                        Bitmap.Config config, boolean needCache)
    {
        mBitmapConfig = config == null ? Bitmap.Config.RGB_565 : config;
        setImageFromBitmap(bitmap, format, config, needCache);
    }

    public BitmapMatrix(InputStream inputStream, Bitmap.Config config)
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
        sc *= (sc < 1) ? 0.99f : 1.01f;
//        if (Math.abs(1 - sc) > 0.0008) {
            scaleShowBitmap(cx, cy, sc);
//        }
//        debug();
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
                    mLoadingThread.addTask(n, m);
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
                }
            }

            /**
             * 初始化缩略图
             */
            Log.e(TAG, "Before Thumb: " + System.currentTimeMillis());
            /**
             * 保存缩略图时的值
             */
            mThumbSampleSize = mSampleSize;
            for (int n = 0; n < mN; ++n) {
                for (int m = 0; m < mM; ++m) {
                    Rect rect = getUnitRect(n, m);
                    if (rect != null) {
                        mGrids[n][m].mCurSampleSize = mSampleSize;
                        mGrids[n][m].mThumbBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                    }
                }
            }
            Log.e(TAG, "After Thumb: " + System.currentTimeMillis());
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
         * 当缩放结束之后，如果 SampleSize 改变了， 则需要重新调整和解码出最新的bitmap
         * TODO: 使用异步
         */
        private void updateBitmapGrid()
        {
            if (mGrids == null) {
                return;
            }

            Rect visible = getVisibleGrid();

            int sn = Math.max(visible.top - 1, 0);
            int sm = Math.max(visible.left - 1, 0);
            int en = Math.min(visible.bottom + 1, mN);
            int em = Math.min(visible.right + 1, mM);

            recycleInvisibleGrids(visible);

            for (int n = sn; n <= en; ++n) {
                for (int m = sm; m <= em; ++m) {
//                    mLoadingThread.addTask(n, m);
//                    if (isValidGrid(n, m)) {
//                        Rect rect = getUnitRect(n, m);
//                        if (mGrids[n][m].mBitmap != null) {
//                            mGrids[n][m].recycle();
//                        }
//                        mGrids[n][m].mBitmap = decodeRectBitmap(rect);
//                    }
                }
            }
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
         * 调整可见区域的单元格，并释放掉不需要维护的单元格
         */
        private void adjustVisibleBitmapGrid()
        {
            if (mGrids == null) {
                return;
            }

            Rect visible = getVisibleGrid();

            int sn = Math.max(visible.top-1, 0);
            int sm = Math.max(visible.left-1, 0);
            int en = Math.min(visible.bottom+1, mN);
            int em = Math.min(visible.right+1, mM);

            recycleInvisibleGrids(visible);

            /**
             * 刷新可见区域的bitmap, 并进行预加载
             * 即加载这个单元格周围的 9 个单元格,
             */
            for (int n = sn; n <= en; ++n) {
                for (int m = sm; m <= em; ++m) {
                    if (isValidGrid(n, m) && mGrids[n][m].mBitmap == null) {
//                        Rect rect = getUnitRect(n, m);
//                        mGrids[n][m].mBitmap = decodeRectBitmap(rect);
//                        mLoadingThread.addTask(n, m);
                    }
                }
            }
        }

        /**
         * 画出可见的几个格子
         */
        private void drawVisibleGrid(Canvas canvas)
        {
            if (mGrids == null) {
                return;
            }

            Rect visible = getVisibleGrid();

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

//                        mPaint.setColor(Color.MAGENTA);
//                        mPaint.setStrokeWidth(2);
//                        canvas.drawRect(vRect, mPaint);
                    }
                }
            }
        }


        /**
         * 管理所有的单元格的 bitmap 加载
         */
        private class LoadingThread extends BaseThread
        {
            private Deque<Point> mLoadingQue = new ArrayDeque<>();

            public synchronized void addTask(int n, int m)
            {
                Log.e(TAG, "Add Task " + n + " , " + m);
                synchronized (this) {
                    boolean canAdd = true;
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

                if (!mIsRunning) {
                    startThread();
                }
            }

            @Override
            public void run()
            {
                while (mIsRunning) {
                    if (!mLoadingQue.isEmpty()) {
                        synchronized (this) {
                            Point point = mLoadingQue.poll();
                            int n = point.x;
                            int m = point.y;

                            if (isValidGrid(n, m) && isVisibleUnit(n, m)) {
                                Rect rect = getUnitRect(n, m);
                                if (mGrids[n][m].mBitmap != null) {
                                    mGrids[n][m].recycle();
                                }
                                mGrids[n][m].mCurSampleSize = mSampleSize;

                                if (mSampleSize != mThumbSampleSize) {
                                    mGrids[n][m].mBitmap = decodeRectBitmap(rect, mGrids[n][m].mCurSampleSize);
                                }

                                sendMessage(0);
                            }
                        }
                    }

                    try {
                        Thread.sleep(60);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void handleMessage(Message msg)
            {
                /**
                 * 已经load成功， 需要invalidate 一次
                 */
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
        mBitmapGrid.adjustVisibleBitmapGrid();

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
    private RectF mShowBitmapRectF = new RectF();

    /**
     * 实际bitmap的宽高, 这个宽高就是 原图设置采样率后的宽高
     */
    private Rect mRealBitmapRect = new Rect();

    private Rect mInitShowBitmapRect = new Rect();

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
        mInitShowBitmapRect.set(mShowBitmapRect);

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
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
        tmpOptions.inPreferredConfig = mBitmapConfig;
        tmpOptions.inSampleSize = mSampleSize;
        tmpOptions.inJustDecodeBounds = true;

        mDecoder.decodeRegion(mImageRect, tmpOptions);

        mRealBitmapRect.set(0, 0, tmpOptions.outWidth, tmpOptions.outHeight);
        Log.e(TAG, "RealBitmapRect: " + mRealBitmapRect);
        Log.e(TAG, "-----------------------------------------------");

        /**
         * 只有在 SampleSize 改变了之后才去刷新
         */
        mBitmapGrid.updateBitmapGrid();
    }

    /**
     * 重置为初始化状态
     */
    private void resetShowBitmapRect()
    {
        if (mShowBitmapRect == mInitShowBitmapRect) {
            return;
        }

        mShowBitmapRect.set(mInitShowBitmapRect);
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

        if (nRect.width() < mInitShowBitmapRect.width() ||
                nRect.height() < mInitShowBitmapRect.height()) {
            resetShowBitmapRect();
            return;
        }

        /**
         * 转换坐标系
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


    private Rect getVirtualViewRect()
    {
        float ratio = getRealImageRatio();
        return rectMulti(mViewRect, ratio);
    }


//    /**
//     * 获取 两个 bitmap 之间的比例
//     */
//    private float getBitmapRatio()
//    {
//        return mRealBitmapRect.width() * 1f / mImageRect.width();
//    }


    /**
     * 将显示的 rect 转换为实际bitmap 的 rect
     */
    private Rect toRealBitmapRect(Rect r)
    {
        float ratio = getRealImageRatio();
        return rectMulti(r, ratio);
    }

    /**
     * 将实际的rect 转换为 显示的bitmap rect
     * @param r
     * @return
     */
    private Rect toShowBitmapRect(Rect r)
    {
        float ratio = getRealImageRatio();
        return rectMulti(r, 1f/ratio);
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

//    /**
//     * 分割实际的bitmap 为 N x M
//     * X: N
//     * Y: M
//     */
//    private Point getBitmapGridNM()
//    {
//        Rect virtualViewRect = getVirtualViewRect();
//
//        int vw = virtualViewRect.width();
//        int vh = virtualViewRect.height();
//
//        int rw = mRealBitmapRect.width();
//        int rh = mRealBitmapRect.height();
//
//        int m = rw / vw + (rw % vw == 0 ? 0 : 1);
//        int n = rh / vh + (rh % vh == 0 ? 0 : 1);
//
//        return new Point(n, m);
//    }
//
//    /**
//     * 获取显示的一个单元格的rect区域
//     * @param n 行
//     * @param m 列
//     * @return 如果返回 null 表示没有这个区域
//     */
//    public Rect getShowUnitRect(int n, int m)
//    {
//        n = n < 0 ? 0 : n;
//        m = m < 0 ? 0 : m;
//
//        int vw = mViewRect.width();
//        int vh = mViewRect.height();
//
//        int bw = mShowBitmapRect.width();
//        int bh = mShowBitmapRect.height();
//
//        int left = Math.min(bw, m * vw);
//        int right = Math.min(bw, left + vw);
//
//        int top = Math.min(bh, n * vh);
//        int bottom = Math.min(bh, top + vh);
//
//        if (left == right || top == bottom) {
//            return null;
//        }
//
//        return new Rect(left, top, right, bottom);
//    }
//
//    /**
//     * 获取实际的 bitmap 单元
//     * @param n row
//     * @param m col
//     * @return 返回 null 表示这个区域为空
//     */
//    public Rect getRealUnitRect(int n, int m)
//    {
//        Rect rect = getShowUnitRect(n, m);
//        if (rect == null) {
//            return null;
//        }
//
//        return rectMulti(rect, getRealImageRatio());
//    }

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


    /**
     * 将真实的bitmap 转换为 原图的 rect
     */
    private Rect toImageRect(Rect rect)
    {
        if (rect == null) {
            return null;
        }

        float wr = getImageWidthRatio();
        float hr = getImageHeightRatio();

//            Log.e(TAG, "WRatio: " + wr + "  HRatio: " + hr);

        return rectMulti(rect, wr);
    }

//    /**
//     * 获取原图的单元格
//     */
//    private Rect getImageUnitRect(int n, int m)
//    {
//        return toImageRect(getRealUnitRect(n, m));
//    }

    private void debug(Canvas canvas)
    {
//            Log.e(TAG, "ViewRect: " + mViewRect);
//            Log.e(TAG, "BitmapRect: " + mShowBitmapRect);
//            Log.e(TAG, "RealBitmapRect: " + mRealBitmapRect);
//            Log.e(TAG, "BitmapRatio: " + getBitmapRatio());
//            Log.e(TAG, "ImageRatio: " + getImageWidthRatio() + " H: " + getImageHeightRatio());
//            Log.e(TAG, "ViewBitmapRect: " + mViewBitmapRect);

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
     * TODO: 是否需要比 size 高一档
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

            Log.e(TAG, "ImageRect: " + mImageRect);
        }
        catch (IOException e) {
            e.printStackTrace();
            is = null;
        }
        finally {
            try {
                if (is != null) {
                    is.close();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
