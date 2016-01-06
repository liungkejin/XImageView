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
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

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
     * 重新初始化整个 Matrix, 重新分割Bitmap
     * @param vw   view的宽
     * @param vh   view的高
     * @param bw   bitmap的宽
     * @param bh   bitmap的高
     */
    public void initializeMatrix(int vw, int vh, int bw, int bh)
    {
        int iw = mImageRect.width();
        int ih = mImageRect.height();
        if (mDecoder == null || iw == 0 || ih == 0) {
            return;
        }

        /**
         * 以 bitmap 的宽高为标准
         * 分别以 宽高为标准， 计算对应的的宽高
         * 如果是宽图, 则以View的宽为标准
         * 否则为高图， 以view的高为标准
         * 求出 SampleSize
         */
        int height = (int) (ih * 1.0f / iw * bw);
        int width = (int) (iw * 1.0f / ih * bh);
        int sampleSize = 1;
        if (width > bw) {
            sampleSize = getSampleSize(iw / bw);
        }
        else {
            sampleSize = getSampleSize(ih / bh);
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

//        mViewAndBitmap.setViewAndBitmap(vw, vh, bw, bh, tmpOptions.outWidth, tmpOptions.outHeight);

//        debug();
    }


    private void debug()
    {
        Log.e(TAG, "ImageRect: " + mImageRect);
        Log.e(TAG, "SampleSize: " + mSampleSize);

//        mViewAndBitmap.debug();
    }

    /**
     * 相对移动 view
     */
    public void offset(int dx, int dy)
    {
        mViewAndBitmap.offsetShowBitmap(dx, dy);
    }

    /**
     * 以 (cx, cy) 为中心缩放
     */
    public void scale(int cx, int cy, float sc)
    {
        mViewAndBitmap.scaleShowBitmap(cx, cy, sc);
//        debug();
    }

    /**
     * 当缩放结束后，需要重新解码最新的bitmap
     */
    public void updateBitmap()
    {
        mViewAndBitmap.updateBitmap();
    }

    /**
     * 设置视图的宽和高
     * 每次都重新初始化一次
     */
    public void setViewRect(int w, int h)
    {
        mViewAndBitmap.setView(w, h);
//        debug();
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



    private ViewAndBitmap mViewAndBitmap = new ViewAndBitmap();

//    private RegionBitmap mRegionBitmap = new RegionBitmap();


    private int mN = 0;
    private int mM = 0;
    private BitmapUnit[][] mMatrix = null;

    private final int [][] NINE_INDEX = {
            {1, 0},
            {0, 0},
            {0, 1},
            {0, -1},
            {-1, 0},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };

    private class BitmapUnit
    {
        public Bitmap mBitmap = null;
    }


    /**
     * 检查
     */
    private void drawRegionBitmap(Canvas canvas)
    {
        Rect virtualViewRect = mViewAndBitmap.getVirtualViewRect();
        Log.e(TAG, "Virtual View Rect: " + virtualViewRect);

        int vw = virtualViewRect.width();
        int vh = virtualViewRect.height();

        Rect visibleBitmapRect = mViewAndBitmap.getVisibleRealBitmapRect();
        Log.e(TAG, "Visible Bitmap Rect: " + visibleBitmapRect);

        /**
         * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
         */
        int sm = visibleBitmapRect.left / vw;
        int sn = visibleBitmapRect.top / vh;

        int em = visibleBitmapRect.right / vw;
        int en = visibleBitmapRect.bottom / vh;

        Log.e(TAG, "S(" + sn + ", " + sm + ")" + " E(" + en + ", " + em +")");

        /**
         * 第一次以第一个可见的单元格为主格
         * 检查主单元格是否在当前可见的单元格里面，如果不在，则需要重新设置主单元格
         */
        if (mSubBitmaps[1][1] == null ||
                !(mN >= sn && mN <= en
                        && mM >= sm && mM <= em)) {
            reAdjustRegion(sn, sm, en, em);
        }

        for (int n = sn; n <= en; ++n) {
            for (int m = sm; m <= em; ++m) {
                int i = n - mN + 1;
                int j = m - mM + 1;
                if (i < 0 || i >=3 || j < 0 || j>=3) {
                    continue;
                }
                /**
                 * 计算出相对 view 坐标系
                 */
                Rect rect = mViewAndBitmap.getShowUnitRect(n, m);
                if (rect != null && mSubBitmaps[i][j] != null) {
                    Rect vRect = mViewAndBitmap.toViewCoordinate(rect);

                    canvas.drawBitmap(mSubBitmaps[i][j], null, vRect, null);

                    mPaint.setColor(Color.BLUE);
                    canvas.drawRect(vRect, mPaint);
                }
            }
        }

        /**
         * For Debug
         */
        mViewAndBitmap.debug(canvas);
    }


    private void reAdjustRegion(int sn, int sm , int en, int em)
    {
        if (mSubBitmaps[1][1] == null) {

            mN = sn;
            mM = sm;

            Point point = mViewAndBitmap.getRealBitmapNM();
            int sumN = point.x;
            int sumM = point.y;

            /**
             * 以[1][1]为中心点
             */
            for (int[] aMSubIndex : NINE_INDEX) {
                int dn = aMSubIndex[0];
                int dm = aMSubIndex[1];

                int i = dn + 1;
                int j = dm + 1;

                int n = mN + dn;
                int m = mM + dm;

                if (n >= 0 && n <= sumN && m >= 0 && m < sumM) {
                    Rect rect = mViewAndBitmap.getImageUnitRect(n, m);
                    mSubBitmaps[i][j] = decodeRectBitmap(rect);
                }
                else {
                    mSubBitmaps[i][j] = null;
                }
            }
        }
        else {
            /**
             * 首先找到新区域的主单元格
             */
            int osn = mN - 1;
            int osm = mM - 1;

            int oen = mN + 1;
            int oem = mM + 1;

            int newN = sn;
            int newM = sm;

            for (int n = sn; n <= en; ++n) {
                for (int m = sm; m <= em; ++m) {
                    if (n >= osn && n <= oen && m >= osm && m <= oem) {
                        if (Math.abs(n- mN)+Math.abs(m- mM) == 1) {
                            newN = n;
                            newM = m;
                            break;
                        }
                    }
                }
            }

            Bitmap [][] bitmaps = new Bitmap[3][3];

            /**
             * 将还可用的bitmap 移到正确的单元格内，不再需要的 bitmap 进行释放
             */
            for (int[] aMSubIndex : NINE_INDEX) {
                int dn = aMSubIndex[0];
                int dm = aMSubIndex[1];

                // [3][3]
                int i = dn + 1;
                int j = dm + 1;

                // 当前最新的单元格
                int nn = newN + dn;
                int nm = newM + dm;

                /**
                 *
                 */
                if (nn >= osn && nn <= oen && nm >= osm && nm <= oem) {

                    int oi = nn - mN + 1;
                    int oj = nm - mM + 1;
                    Log.e(TAG, "OI: " + oi + "  OJ: " + oj);
                    bitmaps[i][j] = mSubBitmaps[oi][oj];
                    mSubBitmaps[oi][oj] = null;
                }

                if (bitmaps[i][j] == null) {
                    Rect rect = mViewAndBitmap.getImageUnitRect(nn, nm);
                    bitmaps[i][j] = decodeRectBitmap(rect);
                }
            }

            /**
             * 释放并重新赋值
             */
            for (int i = 0; i < 3; i++) {
                for (int j =0; j < 3; ++j) {
                    if (mSubBitmaps[i][j] != null) {
                        mSubBitmaps[i][j].recycle();
                    }
                    mSubBitmaps[i][j] = bitmaps[i][j];
                }
            }

            mN = sn;
            mM = sm;
        }
    }

//    /**
//     * 表示真实Bitmap的一部分
//     */
//    private class RegionBitmap
//    {
//
//        private Bitmap [][] mSubBitmaps = new Bitmap[3][3];
//
//
//    }

    /**
     * 判断是否为高图，长 > 宽
     * @return
     */
    public boolean isHeightImage()
    {
        return true;
    }

    /**
     * 管理视窗 和 bitmap (显示的和真实的)
     */
    private class ViewAndBitmap
    {
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

            if (vw == mViewRect.width() && vh == mViewRect.height()) {
                return;
            }

            mViewRect.set(0, 0, vw, vh);
            Log.e(TAG, "ViewRect: " + mViewRect);

            int iw = mImageRect.width();
            int ih = mImageRect.height();
            int height = (int) (ih * 1.0f / iw * vw);
            int width = (int) (iw * 1.0f / ih * vh);
            float ratio = 1f;
            if (width > vw) {
                ratio = iw * 1f / vw;
            }
            else {
                ratio = ih * 1f / vh;
            }

            Log.e(TAG, "ratio: " + ratio);
            ratio = ratio < 1 ? 1f : ratio;

            mShowBitmapRect.set(0, 0, (int) (iw / ratio), (int) (ih / ratio));
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

            updateBitmap();
        }

        /**
         * 当缩放结束后，需要重新解码最新的bitmap
         */
        private void updateBitmap()
        {
            int iw = mImageRect.width();
            int ih = mImageRect.height();
            if (mDecoder == null || iw == 0 || ih == 0) {
                return;
            }

            int bw = mShowBitmapRect.width();
            int bh = mShowBitmapRect.height();
            if (bw <= 0 || bh <= 0) {
                return;
            }

            /**
             * 以 bitmap 的宽高为标准
             * 分别以 宽高为标准， 计算对应的的宽高
             * 如果是宽图, 则以View的宽为标准
             * 否则为高图， 以view的高为标准
             * 求出 SampleSize
             */
            int height = (int) (ih * 1.0f / iw * bw);
            int width = (int) (iw * 1.0f / ih * bh);
            int sampleSize = 1;
            if (width > bw) {
                sampleSize = getSampleSize(iw / bw);
            }
            else {
                sampleSize = getSampleSize(ih / bh);
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
        }

        /**
         * 重置为初始化状态
         */
        private void reInitShowBitmapRect()
        {
            mShowBitmapRect.set(mInitShowBitmapRect);

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
            Rect oRect = toViewCoordinate(mShowBitmapRect);

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
            int left = (int) Math.floor(cx - (cx - oRect.left) * sc);
            int right = (int) (left + (oRect.width() * sc) + 0.5f);

            int top = (int) Math.floor(cy - (cy - oRect.top) * sc);
            int bottom = (int) ((right - left) * getImageRatio() + top);

            Rect nRect = new Rect(left, top, right, bottom);

            if (nRect.width() < mInitShowBitmapRect.width() || nRect.height() < mInitShowBitmapRect.height()) {
                reInitShowBitmapRect();
                return;
            }

            /**
             * 转换坐标系
             */
            updateViewBitmapRect(nRect);

            /**
             * 如果还是小于视图宽度，则需要移动到中线
             */
            int nx = 0;
            int ny = 0;
            Rect aRect = toViewCoordinate(mShowBitmapRect);
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


        }

        /**
         * 更新ViewBitmapRect
         * @param rect ShowBitmapRect相对view坐标系的rect
         */
        private void updateViewBitmapRect(Rect rect)
        {
            Rect vRect = new Rect(0, 0, mViewRect.width(), mViewRect.height());
            vRect.left -= rect.left;
            vRect.right = vRect.left + mViewRect.width();
            vRect.top -= rect.top;
            vRect.bottom = vRect.top + mViewRect.height();

            mViewBitmapRect.set(vRect);
            mShowBitmapRect.set(new Rect(0, 0, rect.width(), rect.height()));
        }

        /**
         * 移动, TODO: 添加标志位，表示那个方向已经不能滑动
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
            float ratio = getBitmapRatio();
            return rectMulti(mViewRect, ratio);
        }


        /**
         * 获取 两个 bitmap 之间的比例
         */
        private float getBitmapRatio()
        {
            return mRealBitmapRect.width() * 1f / mShowBitmapRect.width();
        }


        /**
         * 将显示的 rect 转换为实际bitmap 的 rect
         */
        private Rect toRealBitmapRect(Rect r)
        {
            float ratio = getBitmapRatio();
            return rectMulti(r, ratio);
        }

        /**
         * 将实际的rect 转换为 显示的bitmap rect
         * @param r
         * @return
         */
        private Rect toShowBitmapRect(Rect r)
        {
            float ratio = getBitmapRatio();
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
            float ratio = getBitmapRatio();

            return rectMulti(getVisibleShowBitmapRect(), ratio);
        }

        /**
         * 分割实际的bitmap 为 N x M
         * X: N
         * Y: M
         */
        private Point getRealBitmapNM()
        {
            Rect virtualViewRect = getVirtualViewRect();

            int vw = virtualViewRect.width();
            int vh = virtualViewRect.height();

            int rw = mRealBitmapRect.width();
            int rh = mRealBitmapRect.height();

            int n = rw / vw + (rw % vw == 0 ? 0 : 1);
            int m = rh / vh + (rh % vh == 0 ? 0 : 1);

            return new Point(n, m);
        }


        /**
         * 获取显示的一个单元格的rect区域
         * @param n 行
         * @param m 列
         * @return 如果返回 null 表示没有这个区域
         */
        public Rect getShowUnitRect(int n, int m)
        {
            n = n < 0 ? 0 : n;
            m = m < 0 ? 0 : m;

            int vw = mViewRect.width();
            int vh = mViewRect.height();

            int bw = mShowBitmapRect.width();
            int bh = mShowBitmapRect.height();

            int left = Math.min(bw, m * vw);
            int right = Math.min(bw, left + vw);

            int top = Math.min(bh, n * vh);
            int bottom = Math.min(bh, top + vh);

            if (left == right || top == bottom) {
                return null;
            }

            return new Rect(left, top, right, bottom);
        }

        /**
         * 获取实际的 bitmap 单元
         * @param n row
         * @param m col
         * @return 返回 null 表示这个区域为空
         */
        public Rect getRealUnitRect(int n, int m)
        {
            Rect rect = getShowUnitRect(n, m);
            if (rect == null) {
                return null;
            }

            return rectMulti(rect, getBitmapRatio());
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

        /**
         * 获取原图的单元格
         */
        private Rect getImageUnitRect(int n, int m)
        {
            return toImageRect(getRealUnitRect(n, m));
        }

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
    }

    /**
     * 从解码出一块bitmap
     */
    public Bitmap decodeRectBitmap(Rect rect)
    {
        if (rect == null) {
            return null;
        }

        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
        tmpOptions.inPreferredConfig = mBitmapConfig;
        tmpOptions.inSampleSize = mSampleSize;
        tmpOptions.inJustDecodeBounds = false;

        Bitmap bitmap = mDecoder.decodeRegion(rect, tmpOptions);

        Log.e(TAG, "Rect: " + rect);
        Log.e(TAG, "Bitmap: W( " + bitmap.getWidth() + ", " + bitmap.getHeight()+ ")");

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
