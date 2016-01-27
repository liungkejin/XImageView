# XImageView
==============
XImageView 可以显示超大尺寸的图片, 并支持缩放，双击放大， 拖动，单击，长按等手势操作,
并不会出现 OOM 的情况! 目前此控件还有一些小问题需要进行优化和改进，但是已经实现了基本的功能

## Issues
1. 滑动惯性不够流畅，需要优化
2. 不能很好的兼容 ViewPager


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

