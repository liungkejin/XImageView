package cn.xoracle.library;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import cn.kejin.android.views.XImageView;

public class MainActivity extends AppCompatActivity
{

    private XImageView mXImageView = null;

    private AlertDialog mDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ProgressBar bar = new ProgressBar(this);
        mDialog = new ProgressDialog.Builder(this).setCancelable(true).setView(bar).create();
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        mXImageView = (XImageView) findViewById(R.id.xImageView);
        mXImageView.setActionListener(new XImageView.OnActionListener()
        {
            @Override
            public void onSingleTapped(MotionEvent event, boolean onImage)
            {
                toastShort("X: " + event.getX() + "  Y: " + event.getY() + " TapOnImage: " + onImage);
                mXImageView.scaleToMinFitView((int) event.getX(), (int) event.getY(), true, 1000);
            }

            @Override
            public void onDoubleTapped(MotionEvent event)
            {
                toastShort("Double Tapped: " + event.getX() + ", " + event.getY());
            }

            @Override
            public void onLongPressed(MotionEvent event)
            {
                toastShort("onLongPressed..." + event.getX() + ", " + event.getY());
                mXImageView.scaleToMaxFitView((int) event.getX(), (int) event.getY(), true, 1000);
            }

            @Override
            public void onSetImageFinished(boolean success, Rect image)
            {
                toastShort("OnSetImageFinished: Success : " + success + " Image: " + image);
                mDialog.dismiss();
            }
        });

        try {
            mXImageView.setImage(getAssets().open("b.jpg"));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        View btn = findViewById(R.id.buttonSwitch);
        btn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mDialog.show();
                if (v.getTag() == null) {
                    mXImageView.setImage(new File(Environment.getExternalStorageDirectory(), "Manor.jpg"));
                    v.setTag(2);
                }
                else {
                    try {
                        mXImageView.setImage(getAssets().open("b.jpg"));
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    v.setTag(null);
                }
            }
        });

        btn.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                mXImageView.scrollImage(100, 100);
                return true;
            }
        });
    }

    private void toastShort(String msg)
    {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
