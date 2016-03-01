package cn.xoracle.library;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.nostra13.universalimageloader.cache.disc.impl.LimitedAgeDiskCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.io.File;

import cn.kejin.android.views.XImageView;

public class ImageActivity extends AppCompatActivity
{

    private final static String TAG = "ImageActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        testXImage();
    }


    private void testXImage()
    {
        final String imageUrl = "http://i3.hoopchina.com.cn/user/112/2752112/13349232980.jpg";

        final XImageView xImageView = (XImageView) findViewById(R.id.ximage);
        xImageView.setActionListener(new XImageView.SimpleActionListener()
        {
            @Override
            public void onSetImageStart(XImageView view)
            {
                Log.e(TAG, "onSetImageStart");
            }

            @Override
            public void onSetImageFinished(XImageView view, boolean success, Rect image)
            {
                Log.e(TAG, "onSetImageFinished: " + success + " Rect: " + image);
            }
        });

        ImageLoader imageLoader = ImageLoader.getInstance();

        if (!imageLoader.isInited()) {
            ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(this)
                    .diskCache(new LimitedAgeDiskCache(getCacheDir(), 1000 * 60 * 10))
                    .defaultDisplayImageOptions(new DisplayImageOptions.Builder().cacheOnDisk(true).build())
                    .build();
            imageLoader.init(config);
        }

        File cacheFile = ImageLoader.getInstance().getDiskCache().get(imageUrl);
        if (cacheFile != null && cacheFile.exists()) {
            toastShort("CacheFile: " + cacheFile);
            ImageView iv = (ImageView) findViewById(R.id.image);
            iv.setImageURI(Uri.fromFile(cacheFile));
            xImageView.setImage(cacheFile, Bitmap.Config.ARGB_8888);
        }
        else {
            ImageLoader.getInstance().loadImage(imageUrl, new SimpleImageLoadingListener()
            {
                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage)
                {
                    xImageView.setImage(loadedImage);
                }
            });
        }
    }


    private void toastShort(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
