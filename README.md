# XImageView

XImageView 可以显示超大尺寸的图片, 并支持缩放，双击放大， 拖动，单击，长按等手势操作, 支持惯性滑动, 支持ViewPager

欢迎使用

![demo](https://github.com/liungkejin/XImageView/blob/master/images/demo.gif)

这是一张 **21250 x 7500 （1亿6千万像素）像素(121 MB)**, 加载需要30s 左右

<img src="https://github.com/liungkejin/XImageView/blob/master/images/S60130-003746.jpg" width=200/>


**这里可以下载这些大图 http://pan.baidu.com/s/1mhw6Qko**


## Usage
```gradle
dependencies {
    compile 'cn.kejin.ximageview:XImageView:1.2.2'
}
```

```xml
<cn.kejin.ximageview.XImageView
	android:id="@+id/xImageView"
	android:layout_height="match_parent"
	android:layout_width="match_parent"
    app:initType="fitViewMinImageMin"
    app:doubleType="fitImageMinViewMax"/>
<!-- initFitView 属性是当图片小于 view的尺寸时，是否需要在初始化时使用view的尺寸 -->
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


| 属性 | 值 | 说明 |
| --- | --- | --- |
| `initType` | `fitViewMin` | **默认为此类型** 总是缩放适应View的最短的边长 |
| | `fitViewMinImageMin` | 当图片的尺寸小于 view 的尺寸时, 不进行放大, 否则缩小至适应view的最短的边长 |
| | `fitViewMax` | **TODO: 尚未实现** 总是缩放适应View的最长的边长 |
| | `fitImage`    | **TODO: 尚未实现** 图片有多大显示多大 |
| |
| `doubleType` | `fitViewMinViewMax` | **默认为此种类型**  缩小至 ViewMin, 放大至 ViewMax |
| | `fitImageMinViewMax` | 缩小至 Min(viewMin, imageMin), 放大至 viewMax |
| | `fitViewMinImageMax` | **TODO: 尚未实现** 缩小至 viewMin, 放大至 Max(imageMax, Min(3 x imageMax, viewMax)) |
| | `fitImageMinImageMax` | **TODO: 尚未实现** 缩小至 Min(viewMin, imageMin), 放大至 Max(imageMax, Min(3 x imageMax, viewMax)) |

```java

/**
 * 初始化状态类型
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
    FIT_VIEW_MIN_IMAGE_MIN(8)
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
}
```

XImageView 支持使用`FilePath, File, InputStream, Bitmap` 来设置图片, `FilePath, File` 会转换为 `InputStream`,
在使用 Bitmap 设置图片时，要注意内存的消耗(因为内部会使用一个副本),
可以使用 `setImage(bitmap, cache)`， 这个方法会把 Bitmap 转换为InputStream，再设置图片,不过这样会比较耗时！

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

| 接口 |  说明 |
| :------------- | :------------- |
| `void setInitType(InitType type)` | 设置初始缩放类型 |
| `void setDoubleTapScaleType(DoubleType type)` | 设置双击缩放的缩放方式 |
| `void scaleImage(float dest, boolean smooth, int smoothTime);` |  以View的中心点为中心缩放, 缩放的目标倍数是以当前的显示的尺寸来计算的(比如 dest=1.1, 则会在当前的显示的基础上放大0.1倍) |
| `void scaleImage(int cx, int cy, float dest, boolean smooth, int smoothTime)` | 以一点为中心缩放图片, (cx,cy) 中心点, dest 缩放的目标倍数，以当前的倍数来计算，smooth 是否使用动画, smoothTime 动画时间 |
| `void scaleToMaxFitView(int cx, int cy, boolean smooth, int smoothTime)` | 缩放到最大适应View（就是图片宽高 >= View的宽高）|
| `void scaleToMinFitView(int cx, int cy, boolean smooth, int smoothTime)` | 缩放到最小适应View (就是图片宽高 <= View的宽高) |
| `int scrollImage(int dx, int dy)` | 滑动图片, 返回当前已经到达的边界`BitmapManager.NONE` `LEFT` `RIGHT` `TOP` `BOTTOM`, (dx, dy) 相对滑动的像素, 当滑动这个图片，如果已经到达了左边的边界， 已经不能再继续向右滑动，则 `scrollImage()` 会 `LEFT`, 如果这个图片左边和上边都已经不能继续滑动，则会返回 `LEFT | RIGHT` |
| `float getScaleFactor()` |  获取当前图片的缩放的倍数,相对图片的原始图片的尺寸来说的 |
| `Rect getRealImageRect()` | 获取真实图片的尺寸，注意最好在 onSetImageFinished() 之后获取这个值 |
| `Rect getShowImageRect()` | 获取当前图片显示出来的的尺寸 |
| `boolean isSettingImage()` | 判断是否正在设置图片 |

监听单击, 双击， 长按, 和 设置完成 事件, 如果需要监听`onSetImageFinished()`, 需要在`setImage()`之前设置这个监听

```java
// 监听单击, 双击， 长按, 和 设置完成 事件
mXImageView.setActionListener(new XImageView.OnActionListener()
{
    @Override
    public void onSingleTapped(XImageView view, MotionEvent event, boolean onImage)
    {
    }

    @Override
    public boolean onDoubleTapped(XImageView view, MotionEvent event)
    {
        return false; // 返回 true 表示已经处理了双击事件， 不会进行缩放操作
    }

    @Override
    public void onLongPressed(XImageView view, MotionEvent event)
    {
    }

    @Override
    public void onSetImageFinished(XImageView view, boolean success, Rect image)
    {
    }
});

// 如果不想监听这么多, 可以使用 XImageView.SimpleActionListener
```
