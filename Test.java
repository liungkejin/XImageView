package cn.kejin.android.views;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2015/12/30
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
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

    private class SubBitmap
    {
        /**
         * 位置
         */
        public int mN = 0;
        public int mM = 0;

        public Bitmap mBitmap = null;
    }

    /**
     * 当前图片的的采样率
     */
    private int mSampleSize = 1;

    /**
     * 原图的宽高
     */
    private Rect mImageRect = new Rect();

    /**
     * Show 映射到 Real上的比例
     */
    private float mWRatio = 1f;
    private float mHRatio = 1f;

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
     * 视图的宽高
     */
    private Rect mViewRect = new Rect();

    /**
     * 视图相对bitmap（mShowBitmapRect）坐标系的 rect
     */
    private Rect mViewBitmapRect = new Rect();


    /**
     * 维护一个3 x 3 的数组,
     */
    private SubBitmap [][] mSubBitmaps = new SubBitmap[3][3];

    /**
     * 3 x 3 的中心的sub bitmap, 这个bitmap只有在完全没有显示了才会换位置
     */
    private SubBitmap mMainSub = mSubBitmaps[1][1];


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

        mViewRect.set(0, 0, vw, vh);
        mShowBitmapRect.set(0, 0, bw, bh);

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
        mRealBitmapRect.set(0, 0, tmpOptions.outWidth, tmpOptions.outHeight);

        mWRatio = mRealBitmapRect.width() * 1.0f / mShowBitmapRect.width();
        mHRatio = mRealBitmapRect.height() * 1.0f / mShowBitmapRect.height();

        debug();
    }


    private void debug()
    {
        Log.e(TAG, "ImageRect: " + mImageRect);
        Log.e(TAG, "ViewRect: " + mViewRect);
        Log.e(TAG, "BitmapRect: " + mShowBitmapRect);
        Log.e(TAG, "RealBitmapRect: " + mRealBitmapRect);
        Log.e(TAG, "WRatio: " + mWRatio + "  HRatio: " + mHRatio);
        Log.e(TAG, "SampleSize: " + mSampleSize);
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


    public void offset(int dx, int dy)
    {
        mViewBitmapRect.offset(-dx, -dy);
    }

    public void scale(float sc)
    {
        //
    }

    /**
     * 画可见区域的的Bitmap
     * 1.
     * @param canvas 画布
     */
    public void drawVisibleBitmap(Canvas canvas, int width, int height)
    {
        if (mDecoder == null) {
            return;
        }

        if (width != mViewRect.width() || height != mViewRect.height()) {
            mViewRect.set(0, 0, width, height);
        }

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

        Rect bRect = new Rect(left, top, right, bottom);

        Log.e(TAG, "VisibleBitmapRect: " + bRect);

        /**
         * 映射到真实的 bitmap 上, 相对view的坐标系
         */
        float wr = mRealBitmapRect.width() * 1.0f / mShowBitmapRect.width();
        float hr = mRealBitmapRect.height() * 1.0f / mShowBitmapRect.height();

        left = (int) ((bRect.left - iRect.left) * wr);
        top  = (int) ((bRect.top - iRect.top) * hr);
        right = (int) ((bRect.right - iRect.right) * wr);
        bottom = (int) ((bRect.bottom - iRect.bottom) * hr);

        Rect rRect = new Rect(left, top, right, bottom);

        Log.e(TAG, "RealBitmapRect: " + rRect);

        /**
         * 转换为 bitmap的坐标
         */
        left -= iRect.left;
        right -= iRect.left;
        top -= iRect.top;
        bottom -= iRect.bottom;

        rRect.set(left, top, right, bottom);
        Log.e(TAG, "RealBitmapRect2: " + rRect);

        /**
         * 把窗口也缩放到和真实的bitmap 同等级
         */
        int vw = (int) (mViewRect.width() * wr);
        int vh = (int) (mViewRect.height() * hr);

        /**
         * 根据这个rect , 算出所涉及到的单元格，并判断是否需要重新分配
         */
        int sn = rRect.left / vw;
        int sm = rRect.top / vh;

        int en = rRect.right / vw;
        int em = rRect.bottom / vh;

        Rect uRect = new Rect(sm, sn, em, en);
        Log.e(TAG, "S(" + sn + ", " + sm + ")" + " E(" + en + ", " + em +")");
        if (mMainSub.mBitmap == null) {
            /**
             * 第一次以第一个可见的单元格为主格
             */
            mMainSub.mN = sn;
            mMainSub.mM = sm;
        }

        /**
         * 检查主单元格是否在当前可见的单元格里面，如果不在，则需要重新设置主单元格
         *
         */
        if (!uRect.contains(mMainSub.mN, mMainSub.mM)) {
            mMainSub.mN = sn;
            mMainSub.mM = sm;
        }

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
        n = n < 0 ? 0 : n;
        m = m < 0 ? 0 : m;

        /**
         * 把窗口缩放到和真实的bitmap 同等级
         */
        int vw = (int) (mViewRect.width() * mWRatio);
        int vh = (int) (mViewRect.height() * mHRatio);

        int bw = mRealBitmapRect.width();
        int bh = mRealBitmapRect.height();

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
     * 表示Bitmap的一部分
     */
    private class RegionBitmap
    {
        /**
         * 显示的区域, 相对Bitmap坐标系的
         */
        public Rect mRect = new Rect();

        private SubBitmap [][] mSubBitmaps = new SubBitmap[3][3];

        private SubBitmap getMainSub()
        {
            return mSubBitmaps[1][1];
        }

        /**
         *
         * @param m
         * @param n
         */
        private void checkOrChangeMainSub(int sn, int sm, int en, int em)
        {
            //
        }
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
