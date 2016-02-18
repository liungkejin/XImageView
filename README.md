# XImageView

XImageView 可以显示超大尺寸的图片, 并支持缩放，双击放大， 拖动，单击，长按等手势操作, 支持惯性滑动, 支持ViewPager

不会出现 OOM 的情况! 欢迎使用

<img src="https://github.com/liungkejin/XImageView/blob/master/images/S60129-005846.jpg" width=400/>

这是一张 17 M 的世界地图

<img src="https://github.com/liungkejin/XImageView/blob/master/images/S60129-010516.jpg" width=400/>

这是一张 **21250 x 7500 （1亿6千万像素）像素(121 MB)**, 加载需要30s 左右

<img src="https://github.com/liungkejin/XImageView/blob/master/images/S60130-003746.jpg" width=400/>


**这里可以下载这些大图 http://pan.baidu.com/s/1mhw6Qko**


## Usage

```xml
<cn.kejin.android.views.XImageView
	android:id="@+id/xImageView"
	android:layout_height="match_parent"
	android:layout_width="match_parent" />

```

```java
try {
    mXImageView.setImage(getAssets().open("b.jpg"));
}
catch (IOException e) {
	e.printStackTrace();
}
```

## Details
XImageView 支持使用FilePath, File, InputStream, Bitmap 来设置图片, FilePath, File 会转换为 InputStream,
在使用 Bitmap 设置图片时，要注意内存的消耗(因为内部会使用一个副本),
可以使用 setImage(bitmap, cache)， 这个方法会把 Bitmap 转换为InputStream，再设置图片,
不过这样会比较耗时！
```java
void setImage(Bitmap bitmap)
void setImage(Bitmap bitmap, boolean cache);

// 默认的 Config 为 Bitmap.Config.RGB_565
void setImage(String path);
void setImage(String path, Bitmap.Config config);

void setImage(File file);
void setImage(File file, Bitmap.Config config);

void setImage(InputStream inputStream);
void setImage(InputStream is, Bitmap.Config config);

```

监听单击, 双击， 长按, 和 设置完成 事件, 如果需要监听`onSetImageFinished()`, 需要在`setImage()`之前设置这个监听
```java
// 监听单击, 双击， 长按, 和 设置完成 事件
mXImageView.setActionListener(new XImageView.OnActionListener()
{
    @Override
    public void onSingleTapped(MotionEvent event, boolean onImage)
    {
        // 单击事件
    }

    @Override
    public void onDoubleTapped(MotionEvent event)
    {
        // 双击事件
    }

    @Override
    public void onLongPressed(MotionEvent event)
    {
        // 长按事件
    }

    @Override
    public void onSetImageFinished(boolean success, Rect image)
    {
        // 当设置图片完成之后，回调回来，并带回图片的尺寸
    }
});
```

XImageView 提供了缩放接口 和 滑动接口
```java
// 以View的中心点为中心缩放,
// 缩放的目标倍数是以当前的倍数来计算的
void scaleImage(float dest, boolean smooth, int smoothTime);

/**
 * 以一点为中心缩放图片
 * @param cx 中心点
 * @param cy 中心点
 * @param dest 缩放的目标倍数， 这是以当前的倍数来计算的
 * @param smooth 是否使用动画
 * @param smoothTime 动画时间
 */
void scaleImage(int cx, int cy, float dest, boolean smooth, int smoothTime)

 /**
 * 放大到最大适应View（就是图片宽高 >= View的宽高）
 */
void scaleToMaxFitView(int cx, int cy, boolean smooth, int smoothTime);

/**
 * 放大到最小适应View (就是图片宽高 <= View的宽高)
 */
void scaleToMinFitView(int cx, int cy, boolean smooth, int smoothTime);

/**
 * 获取当前图片的缩放的倍数
 * @return 放大的倍数, 相对图片的原始图片的尺寸来说的
 */
float getScaleFactor();
```

当滑动这个图片，如果已经到达了左边的边界， 已经不能再继续向右滑动，则 `scrollImage()` 会 `LEFT`,
如果这个图片左边和上边都已经不能继续滑动，则会返回 `LEFT | TOP`
```java
/**
 * 滑动图片, 返回当前已经到达的边界
 * BitmapManager.NONE
 * BitmapManager.LEFT
 * BitmapManager.RIGHT
 * BitmapManager.TOP
 * BitmapManager.BOTTOM
 * @param dx x轴滑动的像素
 * @param dy y轴滑动的像素
 * @return 当已经到达的边界的 与 值
 */
int scrollImage(int dx, int dy)；
```
