package cn.kejin.ximageview;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2016/4/25
 */

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.annotation.NonNull;

import java.io.File;

/**
 * 让BitmapManager回调 View 的方法
 */
public interface IXImageView
{
    boolean DEBUG = true;

    /**
     * 初始化状态类型
     * 此处和 attrs.xml: initType 的value 保持一致
     */
    enum InitType {

        /**
         * 总是缩放到适应 view 的最小一边
         * 默认为这种类型
         */
        FIT_VIEW_MIN(1),

        /**
         * 总是缩放到适应 view 的最大一边
         */
        FIT_VIEW_MAX(2),

        /**
         * 图片有多大就显示多大
         */
        FIT_IMAGE(4),

        /**
         * 当图片小于 view 的最小边的时候, 不进行缩放, 按照 image 的尺寸显示,
         * 如果大于view 的最小边就缩放至适应 view 的最小边
         */
        FIT_VIEW_MIN_IMAGE_MIN(8);

        public int value = 1;
        InitType(int v) { value = v; }

        public static InitType valueOf(int value) {
            if (value == FIT_VIEW_MIN.value) {
                return FIT_VIEW_MIN;
            }

            if (value == FIT_VIEW_MAX.value) {
                return FIT_VIEW_MAX;
            }

            if (value == FIT_IMAGE.value) {
                return FIT_IMAGE;
            }

            if (value == FIT_VIEW_MIN_IMAGE_MIN.value) {
                return FIT_VIEW_MIN_IMAGE_MIN;
            }

            return FIT_VIEW_MIN;
        }
    }

    /**
     * 双击缩放类型
     * 此处和 attrs.xml: doubleType 的value 保持一致
     */
    enum DoubleType {
        /**
         * （默认为这种）
         * 缩小至 viewMin
         * 放大至 viewMax
         */
        FIT_VIEW_MIN_VIEW_MAX(1),

        /**
         * 缩小至 Min(viewMin, imageMin)
         * 放大至 viewMax
         */
        FIT_IMAGE_MIN_VIEW_MAX(2),

        /**
         * 缩小至 viewMin
         * 放大至 Max(imageMax, Min(3 x imageMax, viewMax))
         */
        FIT_VIEW_MIN_IMAGE_MAX(3),

        /**
         * 缩小至 Min(viewMin, imageMin)
         * 放大至 Max(imageMax, Min(3 x imageMax, viewMax))
         */
        FIT_IMAGE_MIN_IMAGE_MAX(4);

        public int value = 1;
        DoubleType(int v) { value = v; }

        public static DoubleType valueOf(int value) {
            if (value == FIT_VIEW_MIN_VIEW_MAX.value) {
                return FIT_VIEW_MIN_VIEW_MAX;
            }

            if (value == FIT_VIEW_MIN_IMAGE_MAX.value) {
                return FIT_VIEW_MIN_IMAGE_MAX;
            }

            if (value == FIT_IMAGE_MIN_IMAGE_MAX.value) {
                return FIT_IMAGE_MIN_IMAGE_MAX;
            }

            if (value == FIT_IMAGE_MIN_VIEW_MAX.value) {
                return FIT_IMAGE_MIN_VIEW_MAX;
            }

            return FIT_VIEW_MIN_VIEW_MAX;
        }
    }

    /**
     * @return XImageView 的实例
     */
    XImageView getInstance();

    /**
     * 劫断输入
     * @param intercept 是否劫持
     */
    void interceptParentTouchEvent(boolean intercept);

    /**
     * @return view的宽
     */
    int getWidth();

    /**
     * @return view的高度
     */
    int getHeight();

    /**
     * @return 获取 cache dir
     */
    @NonNull File getCacheDir();

    /**
     * 在 view的UI线程中执行， 保证能获取到 view的宽高
     * @param runnable runnable
     */
    void callPost(Runnable runnable);

    /**
     * 调用 invalidate
     */
    void callInvalidate();

    /**
     * 调用 postInvalidate 方法
     */
    void callPostInvalidate();


    /**
     * @return 解码选项
     */
    Bitmap.Config getBitmapConfig();

    /**
     * @return 是否cache bitmap
     */
    boolean enableCache();

    /**
     * @return 是否有初始化显示动画
     */
    boolean initAnimation();


    /**
     * @return 初始化类型
     */
    InitType getInitType();

    /**
     * @return 双击缩放类型
     */
    DoubleType getDoubleType();

    /**
     * @return 是否允许弹性缩放
     */
    boolean enableScaleOver();



    /**
     * 当设置结束后进行回调
     * @param bm BitmapManager
     * @param success 是否成功
     * @param image 返回图片的实际宽高
     */
    void onSetImageFinished(BitmapManager bm, boolean success, Rect image);
}
