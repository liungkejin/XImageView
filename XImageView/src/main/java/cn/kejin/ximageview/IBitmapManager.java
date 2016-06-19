package cn.kejin.ximageview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;

import java.io.InputStream;

/**
 * Author: Kejin ( Liang Ke Jin )
 * Date: 2016/4/25
 */
public interface IBitmapManager
{
    /**
     * 移动, 相对移动 view
     * Left   = 0x01;
     * Right  = 0x02;
     * Top    = 0x04;
     * Bottom = 0x08;
     */
    int NONE   = 0x00;
    int LEFT   = 0x01;
    int RIGHT  = 0x02;
    int TOP    = 0x04;
    int BOTTOM = 0x08;

    /**
     * @return 判断当前的 Bitmap 是否有效
     */
    boolean isNotAvailable();

    /**
     * 检测是否这个点在图片上
     *
     * @param x 坐标x
     * @param y 坐标Y
     * @return boolean
     */
    boolean isTapOnImage(int x, int y);

    /**
     * 检查是否正在设置图片
     *
     * @return boolean
     */
    boolean isSettingImage();


    /**
     * @param bitmap 设置 bitmap
     * @param cache 是否cache
     */
    void setBitmap(Bitmap bitmap, boolean cache);

    /**
     * @param is 设置输入流
     * @param config config
     */
    void setInputStream(InputStream is, Bitmap.Config config);


    /**
     * 获取图片真实的
     *
     * @return Rect
     */
    Rect getRealImageRect();

    /**
     * 获取当前显示出来的图片长宽
     *
     * @return Rect
     */
    Rect getCurImageRect();



    /**
     * 移动显示的 Bitmap
     * @param dx x 轴移动的差值
     * @param dy y 轴移动的差值
     * @return 返回到达的边界的与值
     */
    int move(int dx, int dy);

    /**
     * 缩放显示的 Bitmap
     * @param cx 缩放点x
     * @param cy 缩放点y
     * @param scale 缩放倍数
     */
    void scale(float cx, float cy, float scale);

    /**
     * 当View的size改变, 需要重新计算
     * @param width width
     * @param height height
     */
    void onViewSizeChanged(int width, int height);

    /**
     * 画出可见区域的bitmap
     * @param canvas canvas
     * @param width width
     * @param height height
     * @return boolean (true 表示画图片成功, false 表示正在处理图片或者没有图片， 没有真正画出)
     */
    boolean draw(@NonNull Canvas canvas, int width, int height);


    /**
     * 获取当前的缩放倍数, 相对原图的尺寸
     *
     * @return float
     */
    float getCurScaleFactor();

    /**
     * 缩放到指定的大小
     *
     * @param cx         中心点
     * @param cy         中心点
     * @param dest       目标缩放倍数
     * @param smooth     是否动画
     * @param smoothTime 动画时间
     */
    void scaleTo(final int cx, final int cy, float dest, boolean smooth, long smoothTime);

    /**
     * 以view的中心点缩放
     * @param dest 目标缩放倍数
     * @param smooth 是否动画
     * @param smoothTime 动画时间
     */
    void scaleTo(float dest, boolean smooth, long smoothTime);


    /**
     * 缩放到适应屏幕
     * 如果此时整个图片都在 视图可见区域中, 则放大到占满整个屏幕
     * 如果整个图片不再可见区域中， 则缩小到整个视图可见大小
     *
     * （最小适应屏幕） 一边和视图的一边想等，另外一边小于或等于
     * (最大适应屏幕) 一边和视图的一边相等, 另外一边大于对应的视图的边
     *
     * @param cx         中心点
     * @param cy         中心点
     * @param smooth     是否动画
     * @param smoothTime 动画时间
     */
    void doubleTapScale(int cx, int cy, boolean smooth, long smoothTime);

    /**
     * 以中心点缩放
     * @param smooth     是否动画
     * @param smoothTime 动画时间
     */
    void doubleTapScale(boolean smooth, long smoothTime);

    /**
     * 缩放到最大适应View
     *
     * @param cx         中心点
     * @param cy         中心点
     * @param smooth     是否动画
     * @param smoothTime 动画时间
     */
    void scaleToFitViewMax(int cx, int cy, boolean smooth, long smoothTime);

    /**
     * 缩放到最小适应View
     *
     * @param cx         中心点
     * @param cy         中心点
     * @param smooth     是否动画
     * @param smoothTime 动画时间
     */
    void scaleToFitViewMin(int cx, int cy, boolean smooth, long smoothTime);


    /**
     * 更新一次 bitmap 的 sample size
     */
    void updateSampleSize();

    /**
     * 当这个BitmapManager 被丢弃时，必须要执行这个 destroy(), 确保线程已经退出
     */
    void destroy();
}
