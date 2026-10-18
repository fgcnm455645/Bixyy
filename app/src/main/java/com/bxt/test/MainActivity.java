package com.bxt.test;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    TextView status;
    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Notch Bixby");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(button("Open accessibility settings", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        root.addView(button("App info (Allow restricted settings)", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));

        TextView l1 = new TextView(this);
        l1.setText("Action on swipe");
        l1.setPadding(0, dp(16), 0, 0);
        root.addView(l1);
        final RadioGroup rg = new RadioGroup(this);
        final RadioButton rbT = new RadioButton(this);
        rbT.setText("Type a command");
        rbT.setId(View.generateViewId());
        final RadioButton rbV = new RadioButton(this);
        rbV.setText("Say your command");
        rbV.setId(View.generateViewId());
        rg.addView(rbT);
        rg.addView(rbV);
        rg.check("voice".equals(sp.getString("mode", "type")) ? rbV.getId() : rbT.getId());
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                sp.edit().putString("mode", id == rbV.getId() ? "voice" : "type").apply();
            }
        });
        root.addView(rg);

        CheckBox cb = new CheckBox(this);
        cb.setText("Show zone (red) for testing");
        cb.setChecked(sp.getBoolean("show_zone", false));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton c, boolean on) {
                sp.edit().putBoolean("show_zone", on).apply();
            }
        });
        root.addView(cb);

        seek(root, "Zone extra width (dp)", "pad_x", 30, 120);
        seek(root, "Zone extra height (dp)", "pad_y", 14, 80);

        root.addView(button("Test: launch Ask Bixby now", new View.OnClickListener() {
            public void onClick(View v) {
                BixbyLauncher.launch(MainActivity.this, "voice".equals(sp.getString("mode", "type")));
            }
        }));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(isServiceOn()
                ? "Service: ON - swipe down from the camera cutout"
                : "Service: OFF - enable \"Notch Bixby\" in accessibility settings");
    }

    boolean isServiceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName cn = new ComponentName(this, NotchService.class);
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    Button button(String text, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        return bt;
    }

    void seek(LinearLayout root, final String name, final String key, int def, int max) {
        final TextView t = new TextView(this);
        int val = sp.getInt(key, def);
        t.setText(name + ": " + val);
        t.setPadding(0, dp(12), 0, 0);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(val);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                t.setText(name + ": " + p);
                sp.edit().putInt(key, p).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        root.addView(t);
        root.addView(s);
    }

    public static class NotchService extends AccessibilityService {
        WindowManager wm;
        View zone;
        SharedPreferences sp;
        final SharedPreferences.OnSharedPreferenceChangeListener lis =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                        rebuild();
                    }
                };

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(lis);
            rebuild();
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent e) {}

        @Override
        public void onInterrupt() {}

        @Override
        public void onConfigurationChanged(Configuration c) {
            super.onConfigurationChanged(c);
            rebuild();
        }

        @Override
        public boolean onUnbind(Intent i) {
            remove();
            if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(lis);
            return super.onUnbind(i);
        }

        void remove() {
            if (zone != null && wm != null) {
                try { wm.removeView(zone); } catch (Throwable t) {}
            }
            zone = null;
        }

        Rect cutoutRect() {
            float d = getResources().getDisplayMetrics().density;
            int sw = getResources().getDisplayMetrics().widthPixels;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                    Display disp = dm.getDisplay(Display.DEFAULT_DISPLAY);
                    DisplayCutout dc = disp.getCutout();
                    if (dc != null) {
                        for (Rect r : dc.getBoundingRects()) {
                            if (r.top <= 0 && r.width() < sw / 2) return new Rect(r);
                        }
                    }
                } catch (Throwable t) {}
            }
            int w = (int) (26 * d), h = (int) (26 * d);
            return new Rect(sw / 2 - w / 2, 0, sw / 2 + w / 2, h);
        }

        void rebuild() {
            remove();
            if (wm == null || sp == null) return;
            if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) return;
            float d = getResources().getDisplayMetrics().density;
            int padX = (int) (sp.getInt("pad_x", 30) * d);
            int padY = (int) (sp.getInt("pad_y", 14) * d);
            Rect r = cutoutRect();
            final float thr = 28 * d;

            View v = new View(this);
            v.setBackgroundColor(sp.getBoolean("show_zone", false) ? 0x66FF0000 : Color.TRANSPARENT);
            v.setOnTouchListener(new View.OnTouchListener() {
                float downY;
                boolean fired;
                public boolean onTouch(View view, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downY = e.getRawY();
                            fired = false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (!fired && e.getRawY() - downY > thr) {
                                fired = true;
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                BixbyLauncher.launch(NotchService.this,
                                        "voice".equals(sp.getString("mode", "type")));
                            }
                            break;
                    }
                    return true;
                }
            });

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    r.width() + 2 * padX,
                    r.bottom + padY,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.START;
            p.x = r.left - padX;
            p.y = 0;
            if (Build.VERSION.SDK_INT >= 28) {
                p.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try {
                wm.addView(v, p);
                zone = v;
            } catch (Throwable t) {
                zone = null;
            }
        }
    }
}

class BixbyLauncher {
    static void launch(Context c, boolean voice) {
        Intent i = new Intent("com.samsung.android.bixby.assistanthome.SHORTCUT");
        i.setComponent(new ComponentName("com.samsung.android.bixby.agent",
                "com.samsung.android.bixby.assistanthome.AssistantHomeLauncherActivity"));
        i.putExtra("launch_type", voice ? "LAUNCH_TYPE_VOICE" : "LAUNCH_TYPE_TYPING");
        i.putExtra("entry_point", "ENTRY_POINT_SHORTCUT");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(c, "Failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
