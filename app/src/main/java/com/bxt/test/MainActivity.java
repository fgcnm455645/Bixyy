package com.bxt.test;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
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
import android.view.animation.LinearInterpolator;
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

        TextView l2 = new TextView(this);
        l2.setText("Glow effect");
        l2.setTextSize(18);
        l2.setPadding(0, dp(24), 0, 0);
        root.addView(l2);

        CheckBox cbg = new CheckBox(this);
        cbg.setText("Show glow on swipe");
        cbg.setChecked(sp.getBoolean("glow_on", true));
        cbg.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton c, boolean on) {
                sp.edit().putBoolean("glow_on", on).apply();
            }
        });
        root.addView(cbg);

        final RadioGroup rg2 = new RadioGroup(this);
        final RadioButton rbB = new RadioButton(this);
        rbB.setText("Bixby colors");
        rbB.setId(View.generateViewId());
        final RadioButton rbG = new RadioButton(this);
        rbG.setText("Gemini colors");
        rbG.setId(View.generateViewId());
        rg2.addView(rbB);
        rg2.addView(rbG);
        rg2.check("gemini".equals(sp.getString("glow_style", "bixby")) ? rbG.getId() : rbB.getId());
        rg2.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                sp.edit().putString("glow_style", id == rbG.getId() ? "gemini" : "bixby").apply();
            }
        });
        root.addView(rg2);

        seek(root, "Glow depth (dp)", "glow_depth2", 10, 40);
        seek(root, "Screen corner radius (dp)", "glow_corner", 36, 80);
        seek(root, "Hold time (ms)", "glow_hold2", 500, 2000);

        root.addView(button("Test glow", new View.OnClickListener() {
            public void onClick(View v) {
                sp.edit().putLong("glow_test", System.currentTimeMillis()).apply();
            }
        }));

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
        View glow;
        ValueAnimator anim;
        SharedPreferences sp;
        final SharedPreferences.OnSharedPreferenceChangeListener lis =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                        if ("glow_test".equals(k)) {
                            showGlow();
                        } else if ("pad_x".equals(k) || "pad_y".equals(k) || "show_zone".equals(k)) {
                            rebuild();
                        }
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
            hideGlow();
            if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(lis);
            return super.onUnbind(i);
        }

        void remove() {
            if (zone != null && wm != null) {
                try { wm.removeView(zone); } catch (Throwable t) {}
            }
            zone = null;
        }

        void hideGlow() {
            if (anim != null) {
                anim.removeAllListeners();
                anim.cancel();
                anim = null;
            }
            if (glow != null && wm != null) {
                try { wm.removeView(glow); } catch (Throwable t) {}
            }
            glow = null;
        }

        static int[] palette(String style) {
            if ("gemini".equals(style)) {
                return new int[]{0xFF4285F4, 0xFF9B72CB, 0xFFD96570, 0xFFF9AB00, 0xFF34A853, 0xFF4285F4};
            }
            return new int[]{0xFF2F7BFF, 0xFF4A4BEF, 0xFF7B3FE4, 0xFFB9A8FF, 0xFF7B3FE4, 0xFF4A4BEF, 0xFF2F7BFF};
        }

        void showGlow() {
            if (wm == null || sp == null || !sp.getBoolean("glow_on", true)) return;
            hideGlow();
            float d = getResources().getDisplayMetrics().density;
            final GlowView g = new GlowView(this,
                    (int) (sp.getInt("glow_depth2", 10) * d),
                    sp.getInt("glow_corner", 36) * d,
                    palette(sp.getString("glow_style", "bixby")));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            if (Build.VERSION.SDK_INT >= 28) {
                p.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try {
                wm.addView(g, p);
            } catch (Throwable t) {
                return;
            }
            glow = g;
            final int in = 300, out = 500;
            final int hold = Math.max(100, sp.getInt("glow_hold2", 500));
            final int total = in + hold + out;
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(total);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    float t = a.getAnimatedFraction();
                    float ms = t * total;
                    float k;
                    if (ms < in) k = ms / in;
                    else if (ms < in + hold) k = 1f;
                    else k = 1f - (ms - in - hold) / out;
                    k = Math.max(0f, Math.min(1f, k));
                    k = k * k * (3f - 2f * k);
                    g.set(k, 360f * 0.7f * t);
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    hideGlow();
                }
            });
            anim.start();
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
                                showGlow();
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

class GlowView extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final int[] colors;
    final int depth;
    final float corner;
    final Matrix mat = new Matrix();
    final RectF rect = new RectF();
    SweepGradient sg;
    float k, angle;

    GlowView(Context c, int depth, float corner, int[] colors) {
        super(c);
        this.depth = depth;
        this.corner = corner;
        this.colors = colors;
        paint.setStyle(Paint.Style.STROKE);
    }

    void set(float k, float angle) {
        this.k = k;
        this.angle = angle;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sg = new SweepGradient(w / 2f, h / 2f, colors, null);
        rect.set(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sg == null || k <= 0f) return;
        mat.setRotate(angle, getWidth() / 2f, getHeight() / 2f);
        sg.setLocalMatrix(mat);
        paint.setShader(sg);
        paint.setAlpha((int) (255 * k * 0.075f));
        int layers = 16;
        for (int i = layers; i >= 1; i--) {
            paint.setStrokeWidth(2f * depth * i / layers);
            c.drawRoundRect(rect, corner, corner, paint);
        }
    }
            }
