package cn.kejin.android.views;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/30
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
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
    private int mSampleSize = 1;

    /**
     * 原图的宽高
     */
    private Rect mImageRect = new Rect();
//
//    /**
//     * Show 映射到 Real上的比例
//     */
//    private float mWRatio = 1f;
//    private float mHRatio = 1f;
//
//    /**
//     * 需要显示的bitmap的宽高, 就是真实需要显示的宽高，
//     * 从 mDecoder中出来的 bitmap 需要 缩放为 此宽高
//     * 在计算的时候，需要缩放比例来
//     */
//    private Rect mShowBitmapRect = new Rect();
//
//    /**
//     * 实际bitmap的宽高, 这个宽高就是 原图设置采样率后的宽高
//     */
//    private Rect mRealBitmapRect = new Rect();
//
//    /**
//     * 视图的宽高
//     */
//    private Rect mViewRect = new Rect();
//
//    /**
//     * 视图相对bitmap（mShowBitmapRect）坐标系的 rect
//     */
//    private Rect mViewBitmapRect = new Rect();
//
//
//    /**
//     * 维护一个3 x 3 的数组,
//     */
//    private SubBitmap [][] mSubBitmaps = new SubBitmap[3][3];
//
//    /**
//     * 3 x 3 的中心的sub bitmap, 这个bitmap只有在完全没有显示了才会换位置
//     */
//    private SubBitmap mMainSub = mSubBitmaps[1][1];


    private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;


    private BitmapRegionDecoder mDecoder = null;

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
    public void configMatrix(int vw, int vh, int bw, int bh)
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
        if (width > bw) {
            mSampleSize = getSampleSize(iw / bw);
        }
        else {
            mSampleSize = getSampleSize(ih / bh);
        }

        /**
         * 获取 这个sampleSize 时的真实的 bitmap的宽高
         */
        BitmapFactory.Options tmpOptions = new BitmapFactory.Options();
        tmpOptions.inPreferredConfig = mBitmapConfig;
        tmpOptions.inSampleSize = mSampleSize;
        tmpOptions.inJustDecodeBounds = true;

        mDecoder.decodeRegion(mImageRect, tmpOptions);

        mViewAndBitmap.setViewAndBitmap(vw, vh, bw, bh, tmpOptions.outWidth, tmpOptions.outHeight);

        debug();
    }


    private void debug()
    {
        Log.e(TAG, "ImageRect: " + mImageRect);
        Log.e(TAG, "SampleSize: " + mSampleSize);

        mViewAndBitmap.debug();
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
     * 相对移动 view
     */
    public void offset(int dx, int dy)
    {
        mViewAndBitmap.mViewBitmapRect.offset(-dx, -dy);
    }

    /**
     * 以 (cx, cy) 为中心缩放
     */
    public void scale(int cx, int cy, float sc)
    {
        /**
         * 相对 view 坐标系的 bitmap rect
         */
        Rect rect = mViewAndBitmap.toViewCoordinate(mViewAndBitmap.mShowBitmapRect);

        /**
         * 缩放
         */
        float left = (rect.left - cx) * sc;
        float top = (rect.top - cy) * sc;
        float right = (rect.right - cx) * sc;
        float bottom = (rect.bottom - cy) * sc;

        left += cx;
        right += cx;
        top += cy;
        bottom += cy;

        rect.set((int)left, (int)top, (int)right, (int)bottom);

        /**
         * TODO:
         */
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
//
//        if (width != mViewRect.width() || height != mViewRect.height()) {
//            mViewRect.set(0, 0, width, height);
//        }
//
//        int l = mViewBitmapRect.left;
//        int r = mViewBitmapRect.right;
//        int t = mViewBitmapRect.top;
//        int b = mViewBitmapRect.bottom;
//
//        /**
//         * 根据 view的坐标，求出 Image 相对 view 窗口的坐标系的坐标矩形
//         */
//        int left = mShowBitmapRect.left - l;
//        int right = left + mShowBitmapRect.width();
//        int top = mShowBitmapRect.top - t;
//        int bottom = top + mShowBitmapRect.height();
//
//        Rect iRect = new Rect(left, top, right, bottom);
//
//        Log.e(TAG, "ImageRect: " + iRect);
//
//        /**
//         * 找出实际需要显示的bitmap 区域
//         */
//        left = Math.max(iRect.left, 0);
//        right = Math.min(iRect.right, r);
//        top = Math.max(iRect.top, 0);
//        bottom = Math.min(iRect.bottom, b);
//
//        Rect bRect = new Rect(left, top, right, bottom);
//
//        Log.e(TAG, "VisibleBitmapRect: " + bRect);
//
//        /**
//         * 映射到真实的 bitmap 上, 相对view的坐标系
//         */
//        float wr = mRealBitmapRect.width() * 1.0f / mShowBitmapRect.width();
//        float hr = mRealBitmapRect.height() * 1.0f / mShowBitmapRect.height();
//
//        left = (int) ((bRect.left - iRect.left) * wr);
//        top  = (int) ((bRect.top - iRect.top) * hr);
//        right = (int) ((bRect.right - iRect.right) * wr);
//        bottom = (int) ((bRect.bottom - iRect.bottom) * hr);
//
//        Rect rRect = new Rect(left, top, right, bottom);
//
//        Log.e(TAG, "RealBitmapRect: " + rRect);
//
//        /**
//         * 转换为 bitmap的坐标
//         */
//        left -= iRect.left;
//        right -= iRect.left;
//        top -= iRect.top;
//        bottom -= iRect.bottom;
//
//        rRect.set(left, top, right, bottom);
//        Log.e(TAG, "RealBitmapRect2: " + rRect);
//
//        /**
//         * 把窗口也缩放到和真实的bitmap 同等级
//         */
//        int vw = (int) (mViewRect.width() * wr);
//        int vh = (int) (mViewRect.height() * hr);
//
//        /**
//         * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
//         */
//        int sn = rRect.left / vw;
//        int sm = rRect.top / vh;
//
//        int en = rRect.right / vw;
//        int em = rRect.bottom / vh;
//
//        Rect uRect = new Rect(sm, sn, em, en);
//        Log.e(TAG, "S(" + sn + ", " + sm + ")" + " E(" + en + ", " + em +")");
//        if (mMainSub.mBitmap == null) {
//            /**
//             * 第一次以第一个可见的单元格为主格
//             */
//            mMainSub.mN = sn;
//            mMainSub.mM = sm;
//        }
//
//        /**
//         * 检查主单元格是否在当前可见的单元格里面，如果不在，则需要重新设置主单元格
//         *
//         */
//        if (!uRect.contains(mMainSub.mN, mMainSub.mM)) {
//            mMainSub.mN = sn;
//            mMainSub.mM = sm;
//        }

        mRegionBitmap.drawRegionBitmap(canvas);
    }



    private ViewAndBitmap mViewAndBitmap = new ViewAndBitmap();

    private RegionBitmap mRegionBitmap = new RegionBitmap();


    /**
     * 表示真实Bitmap的一部分
     */
    private class RegionBitmap
    {
        private int mMainN = 0;
        private int mMainM = 0;

        private Bitmap [][] mSubBitmaps = new Bitmap[3][3];

        private int [][] mSubIndex = {
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

        /**
         * 检查
         */
        private void drawRegionBitmap(Canvas canvas)
        {
            Rect virtualViewRect = mViewAndBitmap.getVirtualViewRect();

            int vw = virtualViewRect.width();
            int vh = virtualViewRect.height();

            Rect visibleBitmapRect = mViewAndBitmap.getVisibleRealBitmapRect();

            /**
             * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
             */
            int sn = visibleBitmapRect.left / vw;
            int sm = visibleBitmapRect.top / vh;

            int en = visibleBitmapRect.right / vw;
            int em = visibleBitmapRect.bottom / vh;

            /**
             * 第一次以第一个可见的单元格为主格
             * 检查主单元格是否在当前可见的单元格里面，如果不在，则需要重新设置主单元格
             */
            if (mSubBitmaps[1][1] == null ||
                    !(mMainN>= sn && mMainN<= en
                            && mMainM >= sm && mMainM <= em)) {
                reAdjustRegion(sn, sm, en, em);
            }

            for (int n = sn; n <= en; ++n) {
                for (int m = sm; m <= em; ++m) {
                    int i = n - mMainN + 1;
                    int j = m - mMainM + 1;
                    /**
                     * 计算出相对 view 坐标系
                     */
                    Rect rect = mViewAndBitmap.getShowUnitRect(n, m);
                    if (rect != null) {
                        Rect vRect = mViewAndBitmap.toViewCoordinate(rect);

                        canvas.drawBitmap(mSubBitmaps[i][j], vRect.left, vRect.top, null);
                    }
                }
            }
        }


        private void reAdjustRegion(int sn, int sm , int en, int em)
        {
            Log.e(TAG, "S(" + sn + ", " + sm + ")" + " E(" + en + ", " + em +")");

            if (mSubBitmaps[1][1] == null) {

                mMainN = sn;
                mMainM = sm;

                Point point = mViewAndBitmap.getRealBitmapNM();
                int sumN = point.x;
                int sumM = point.y;

                /**
                 * 以[1][1]为中心点
                 */
                for (int[] aMSubIndex : mSubIndex) {
                    int dn = aMSubIndex[0];
                    int dm = aMSubIndex[1];

                    int i = dn + 1;
                    int j = dm + 1;

                    int n = mMainN + dn;
                    int m = mMainM + dm;

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
                int osn = mMainN - 1;
                int osm = mMainM - 1;

                int oen = mMainN + 1;
                int oem = mMainM + 1;

                int newN = sn;
                int newM = sm;

                for (int n = sn; n <= en; ++n) {
                    for (int m = sm; m <= em; ++m) {
                        if (n >= osn && n <= oen && m >= osm && m <= oem) {
                            if (Math.abs(n-mMainN)+Math.abs(m-mMainM) == 1) {
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
                for (int[] aMSubIndex : mSubIndex) {
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

                        int oi = nn - mMainN + 1;
                        int oj = nm - mMainM + 1;
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

                mMainN = sn;
                mMainM = sm;
            }
        }
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


        /**
         ***************************************************************
         **/

        private void setViewAndBitmap(
                int vw, int vh, int sw, int sh, int rw, int rh)
        {
            mViewRect.set(0, 0, vw, vh);
            mShowBitmapRect.set(0, 0, sw, sh);
            mRealBitmapRect.set(0, 0, rw, rh);
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
        private Point toBitmapCoordinate(Point p)
        {
            if (p == null) {
                return new Point();
            }

            return new Point();
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
         * 计算出可见区域的显示出来的bitmap rect
         */
        private Rect getVisibleShowBitmapRect()
        {
            int l = mViewBitmapRect.left;
            int r = mViewBitmapRect.right;
            int t = mViewBitmapRect.top;
            int b = mViewBitmapRect.bottom;

            /**
             * 根据 view的坐标，求出 Image 相对 view 窗口的坐标系的坐标矩形
             */
            int left = mShowBitmapRect.left - l;
            int right = left + mShowBitmapRect.width();
            int top = mShowBitmapRect.top - t;
            int bottom = top + mShowBitmapRect.height();

            Rect iRect = new Rect(left, top, right, bottom);

            Log.e(TAG, "ImageRect: " + iRect);

            /**
             * 找出实际需要显示的bitmap 区域
             */
            left = Math.max(iRect.left, 0);
            right = Math.min(iRect.right, r);
            top = Math.max(iRect.top, 0);
            bottom = Math.min(iRect.bottom, b);

            return new Rect(left, top, right, bottom);
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
         * 将真实的bitmap 转换为 原图的 rect
         */
        private Rect toImageRect(Rect rect)
        {
            if (rect == null) {
                return null;
            }

            float wr = getImageWidthRatio();
            float hr = getImageHeightRatio();

            Log.e(TAG, "WRatio: " + wr + "  HRatio: " + hr);

            return rectMulti(rect, wr);
        }

        /**
         * 获取原图的单元格
         */
        private Rect getImageUnitRect(int n, int m)
        {
            return toImageRect(getRealUnitRect(n, m));
        }

        private void debug()
        {
            Log.e(TAG, "ViewRect: " + mViewRect);
            Log.e(TAG, "BitmapRect: " + mShowBitmapRect);
            Log.e(TAG, "RealBitmapRect: " + mRealBitmapRect);
            Log.e(TAG, "BitmapRatio: " + getBitmapRatio());
            Log.e(TAG, "ImageRatio: " + getImageWidthRatio() + " H: " + getImageHeightRatio());
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
