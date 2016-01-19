package cn.xoracle.library;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import cn.kejin.android.views.SuperImageView;

public class MainActivity extends AppCompatActivity
{

    private SuperImageView mSuperImageView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewPager);
        viewPager.setAdapter(new PagerAdapter()
        {
            @Override
            public int getCount()
            {
                return 3;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position)
            {
                View view = View.inflate(MainActivity.this, R.layout.layout_page, null);
                SuperImageView imageView = (SuperImageView) view.findViewById(R.id.superImageView);

                switch (position) {
                    case 2:
                        File file = new File(Environment.getExternalStorageDirectory(), "World.jpg");
                        imageView.setImage(file);
                        break;

                    case 1:
                        try {
                            Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("b.jpg"));
                            imageView.setImage(bitmap);
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
//                        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a);
//                        imageView.setImage(bitmap);
                        break;

                    case 0:
                        Bitmap bitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.b);
                        imageView.setImage(bitmap2);
                        break;
                }

                container.addView(view, 0);
                return view;
            }


            @Override
            public void destroyItem(ViewGroup container, int position, Object object)
            {
                container.removeView((View) object);
            }

            @Override
            public boolean isViewFromObject(View view, Object object)
            {
                return view == object;
            }
        });
    }
}
