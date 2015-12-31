package cn.kejin.android.library.views;

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
import java.io.IOException;
import java.io.InputStream;

/**
 * Bitmap 管理，
 * 将Bitmap 以屏幕的尺寸进行分割成二维数组
 * +--+--+--+--+--+--+
 * |  |  |  |  |  |  |
 * +--+--+--+--+--+--+
 * |  |  |  |  |  |  |
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
        public Rect mRect = new Rect();
        public Bitmap mBitmap = null;
    }

    /**
     * 视图的宽高
     */
    private int mViewWidth = 0;
    private int mViewHeight = 0;

    /**
     * 当前图片的的采样率
     */
    private int mSampleSize = 1;

    /**
     * 原图的宽高
     */
    private Rect mImageRect = new Rect();

    /**
     * 目前bitmap的宽高, 这个宽高就是 原图设置采样率后的的宽高
     */
    private Rect mBitmapRect = new Rect();

    /**
     * 视图的宽高
     */
    private Rect mViewRect = new Rect();


    private BitmapRegionDecoder mDecoder = null;


    private int BN = 0;
    private int BM = 0;

    private SubBitmap [][] mSubs = new SubBitmap[0][0];

    /**
     * @param file 图片文件
     */
    public BitmapMatrix(File file, Bitmap.Config config)
    {
    }


    public BitmapMatrix(Bitmap bitmap,
                        Bitmap.CompressFormat format,
                        Bitmap.Config config, boolean needCache)
    {
        setImageFromBitmap(bitmap, format, config, needCache);
    }

    public BitmapMatrix(InputStream inputStream, Bitmap.Config config)
    {
        setImageFromInputStream(inputStream, config);
    }


    public BitmapMatrix()
    {
        //
    }

    /**
     *
     * @param vw   view的宽
     * @param vh   view的高
     * @param bw   bitmap的宽
     * @param bh   bitmap的高
     */
    public void initializeMatrix(int vw, int vh, int bw, int bh)
    {

    }

    /**
     * 分割图片 [N][M]
     */
    private void initializeBitmapMatrix()
    {
        if (mViewHeight == 0 || mViewWidth == 0) {
            Log.e(TAG, "View Width Or Height == 0");
            return;
        }

        /**
         * 求二维数组的
         */
        BN = mBitmapHeight / mViewHeight + (mBitmapHeight % mViewHeight == 0 ? 0 : 1);
        BM = mBitmapWidth / mViewWidth + (mBitmapWidth % mViewWidth == 0 ? 0 : 1);

        mSubs = new SubBitmap[BN][BM];

        // TODO; 检查是否包括边界
        for (int i = 0; i < BN; i++) {
            for(int j = 0; j < BM; ++j) {
                int left = i*mViewWidth;
                int right = Math.min(left + mViewWidth, mBitmapWidth);
                int top = i * mViewHeight;
                int bottom = Math.min(top + mViewHeight, mBitmapHeight);

                mSubs[i][j].mRect.set(left, top, right, bottom);
                mSubs[i][j].mBitmap = null;
            }
        }

    }

    /**
     * 画可见区域的的Bitmap
     * @param canvas 画布
     * @param bRect view 窗口相对 Bitmap 坐标系的 坐标矩形
     */
    public void drawVisibleBitmap(Canvas canvas, Rect bRect)
    {
        if (bRect == null || bRect.width() != mViewWidth || bRect.height() != mViewHeight) {
            Log.e(TAG, "View Width Or Height error: ");
            return;
        }
        /**
         * 根据 view的坐标，求出 可见的bitmap 相对 view 窗口的坐标系的坐标矩形
         */
        int left = -bRect.left <= 0 ? 0 : -bRect.left;
        int right = Math.min(left + bRect.right, left + mBitmapWidth);
        int top = -bRect.top <= 0 ? 0 : -bRect.top;
        int bottom = Math.min(top + bRect.bottom, top + mBitmapHeight);

        Rect vRect = new Rect(left, top, right, bottom);

        /**
         * 找出实际的bitmap 区域
         */
        left = Math.max(bRect.left, 0); // : bRect.left;
        right = Math.min(bRect.right, mBitmapWidth);
        top = Math.max(bRect.top, 0);
        bottom = Math.min(bRect.bottom, mBitmapHeight);

        Rect rRect = new Rect(left, top, right, bottom);


    }


    /**
     * Bitmap 转换为 InputStream, 使用 BitmapRegionDecoder 管理
     * TODO：实现 cache
     * @param bitmap 图片
     * @param format 解码的格式
     * @param needCache 是否需要进行文件缓存（这样如果bitmap太大，可以节约内存）
     */
    public void setImageFromBitmap(Bitmap bitmap, Bitmap.CompressFormat format, Bitmap.Config config, boolean needCache)
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
