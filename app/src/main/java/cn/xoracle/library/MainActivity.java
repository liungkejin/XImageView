package cn.xoracle.library;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

import cn.kejin.android.views.XImageView;

public class MainActivity extends AppCompatActivity
{

    private XImageView mXImageView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mXImageView = (XImageView) findViewById(R.id.superImageView);
        mXImageView.setImage(new File(Environment.getExternalStorageDirectory(), "World.jpg"));

        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    mXImageView.setImage(getAssets().open("b.jpg"));
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 15000);
//        ViewPager viewPager = (ViewPager) findViewById(R.id.viewPager);
//        viewPager.setAdapter(new PagerAdapter()
//        {
//            @Override
//            public int getCount()
//            {
//                return 1;
//            }
//
//            @Override
//            public Object instantiateItem(ViewGroup container, int position)
//            {
//                View view = View.inflate(MainActivity.this, R.layout.layout_page, null);
//                SuperImageView imageView = (SuperImageView) view.findViewById(R.id.superImageView);
//
//                switch (position) {
//                    case 0:
//                        File file = new File(Environment.getExternalStorageDirectory(), "World.jpg");
//                        imageView.setImage(file);
//                        break;
//
//                    case 1:
////                        try {
////                            Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("b.jpg"));
////                            imageView.setImage(bitmap);
////                        }
////                        catch (IOException e) {
////                            e.printStackTrace();
////                        }
//                        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a);
//                        imageView.setImage(bitmap);
//                        break;
//
//                    case 2:
//                        Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.b);
//                        imageView.setImage(bitmap2);
//                        break;
//                }
//
//                container.addView(view, 0);
//                return view;
//            }
//
//
//            @Override
//            public void destroyItem(ViewGroup container, int position, Object object)
//            {
//                container.removeView((View) object);
//            }
//
//            @Override
//            public boolean isViewFromObject(View view, Object object)
//            {
//                return view == object;
//            }
//        });
    }
}
