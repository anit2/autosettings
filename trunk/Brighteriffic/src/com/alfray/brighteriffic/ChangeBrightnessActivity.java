/*
 * (c) ralfoide gmail com, 2009
 * Project: Brighteriffic
 * License gpl v3
 */

package com.alfray.brighteriffic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IHardwareService;
import android.os.Message;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

public class ChangeBrightnessActivity extends Activity {

    private static final String TAG = "ChangeBrightness";

    public static final String INTENT_SET_BRIGHTNESS = "set";
    public static final String INTENT_TOGGLE_BRIGHTNESS = "toggle";

    private Handler mHandler;

    public ChangeBrightnessActivity() {
        mHandler = new Handler() {
          @Override
            public void handleMessage(Message msg) {
                if (msg.what == 42) {
                    ChangeBrightnessActivity.this.finish();
                }
                super.handleMessage(msg);
            }
        };
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Have the system blur any windows behind this one.
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        //                     WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        setContentView(R.layout.empty);

        Intent i = getIntent();
        float f = i.getFloatExtra(INTENT_SET_BRIGHTNESS, -1);

        float result = -1;

        if (f >= 0) {
            result = setCurrentBrightness(f);

        } else if (i.getBooleanExtra(INTENT_TOGGLE_BRIGHTNESS, false)) {

            PrefsValues prefValues = new PrefsValues(this);
            float _min = prefValues.getMinBrightness() / 100.0f;
            float _max = prefValues.getMaxBrightness() / 100.0f;
            if (_min > _max) {
                float t = _min;
                _min = _max;
                _max = t;
            }

            float median = (_min + _max) / 2;

            if (getCurrentBrightness() > median) {
                result = setCurrentBrightness(_min);
            } else {
                result = setCurrentBrightness(_max);
            }

        }

        Message msg = mHandler.obtainMessage(42);
        mHandler.sendMessageDelayed(msg, 1000); // this makes it all work

        if (result >= 0) {
            Toast
                .makeText(this,
                    getResources().getString(R.string.brightness_changed_toast, (int)(100*result)),
                    Toast.LENGTH_SHORT)
                .show();
        }
    }

    /** Sets the actual brightness. Enforce that you never set it to zero.
     * Returns float > 0 if actually managed to change the brightness */
    private float setCurrentBrightness(float f) {

        int v = (int) (255 * f);
        if (v < 10) {
            // never set backlight too dark
            v = 10;
            f = v / 255.f;
        }

        Settings.System.putInt(this.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                v);

        int sdk = Integer.parseInt(Build.VERSION.SDK);
        if (sdk >= 3) {

            try {
                Window win = getWindow();
                LayoutParams attr = win.getAttributes();
                Field field = attr.getClass().getField("screenBrightness");
                field.setFloat(attr, f);

                win.setAttributes(attr);

                Log.i(TAG, String.format("Changed brightness to %.2f [SDK 3+]", f));

                return f;

            } catch (Throwable t) {
                Log.e(TAG, String.format("Failed to set brightness to %.2f [SDK 3+]", f), t);
            }

        } else {
            // Older SDKs
            try {
                IHardwareService hs = IHardwareService.Stub.asInterface(
                        ServiceManager.getService("hardware"));
                if (hs != null) {
                    Method m = hs.getClass().getMethod("setScreenBacklight", new Class[] { int.class });
                    if (m != null) {
                        m.invoke(hs, new Object[] { v });
                        Log.i(TAG, String.format("Changed brightness to %d [SDK<3]", v));
                        return f;
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, String.format("Failed to set brightness to %d [SDK<3]", v), t);
            }
        }

        return -1;
    }

    /**
     * Returns screen brightness in range 0..1%.
     */
    public float getCurrentBrightness() {
        try {
            int v = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);

            return v / 255.0f;
        } catch (SettingNotFoundException e) {
            // If not found, return some default
            return 0.75f;
        }
    }

}
