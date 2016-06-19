package cn.xoracle.library;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;

import cn.kejin.ximageview.XImageView;

public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupViewPager();
    }

    private void setupViewPager()
    {
        ViewPager pager = (ViewPager) findViewById(R.id.viewPager);

        pager.setAdapter(new PagerAdapter()
        {
            @Override
            public int getCount()
            {
                return 10;
            }

            @Override
            public boolean isViewFromObject(View view, Object object)
            {
                return view == object;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position)
            {
                View view = View.inflate(MainActivity.this, R.layout.layout_page, null);
                view.findViewById(R.id.progress).setVisibility(View.VISIBLE);
                setupXImageView((XImageView) view.findViewById(R.id.xImageView), (ProgressBar) view.findViewById(R.id.progress), position);
                container.addView(view);

                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object)
            {
                container.removeView((View) object);
            }
        });
    }

    private void setupXImageView(XImageView imageView, final ProgressBar progressBar,  int pos)
    {
        try {
            switch (pos % 6) {
                case 0:
                    imageView.setImage(getAssets().open("a.jpg"));
                    break;

                case 1:
                    imageView.setImage(getAssets().open("b.jpg"));
                    break;

                case 2:
                    imageView.setImage(new File(Environment.getExternalStorageDirectory(), "Manor.jpg"));
                    break;

                case 3:
                    imageView.setImage(getAssets().open("c.jpg"));
                    break;

                case 4:
//                    imageView.setDoubleTapScaleType(XImageView.TYPE_FIT.FIT_IMAGE);
                    imageView.setImage(getAssets().open("d.jpg"));
                    break;

                case 5:
                    imageView.setImage(getAssets().open("e.jpg"));
                    break;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        imageView.setActionListener(new XImageView.OnActionListener()
        {
            @Override
            public void onSingleTapped(XImageView view, MotionEvent event, boolean onImage)
            {
//                toastShort("X: " + event.getX() + "  Y: " + event.getY() + " TapOnImage: " + onImage);
            }

            @Override
            public boolean onDoubleTapped(XImageView view, MotionEvent event)
            {
//                toastShort("Double Tapped: " + event.getX() + ", " + event.getY());
                return false;
            }

            @Override
            public void onLongPressed(XImageView view, MotionEvent event)
            {
//                toastShort("onLongPressed..." + event.getX() + ", " + event.getY());
                startActivity(new Intent(getApplicationContext(), ImageActivity.class));
            }

//            @Override
//            public void onSetImageStart(XImageView view)
//            {
////                toastShort("Start set Image..");
//                progressBar.setVisibility(View.VISIBLE);
//            }

            @Override
            public void onSetImageFinished(XImageView view, boolean success, Rect image)
            {
//                toastShort("OnSetImageFinished: Success : " + success + " Image: " + image);
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void toastShort(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
