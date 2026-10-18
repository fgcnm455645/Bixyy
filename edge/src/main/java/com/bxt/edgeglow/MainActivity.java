package com.bxt.edgeglow;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        title.setText("Edge Glow");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        root.addView(button("1) Open accessibility settings", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        root.addView(button("2) Grant notification access", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }));
        root.addView(button("App info (Allow restricted settings)", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));

        TextView l1 = new TextView(this);
        l1.setText("Notifications");
        l1.setTextSize(18);
        l1.setPadding(0, dp(24), 0, 0);
        root.addView(l1);
        check(root, "Glow on notifications", "notif_on", true);
        check(root, "Use each app's color", "notif_appcolor", true);
        check(root, "Skip silent notifications", "notif_skip_silent", true);
        check(root, "Skip when Do Not Disturb is on", "notif_skip_dnd", true);

        final RadioGroup rg3 = new RadioGroup(this);
        final RadioButton rbAll = new RadioButton(this);
        rbAll.setText("All apps");
        rbAll.setId(View.generateViewId());
        final RadioButton rbSel = new RadioButton(this);
        rbSel.setText("Selected apps only");
        rbSel.setId(View.generateViewId());
        rg3.addView(rbAll);
        rg3.addView(rbSel);
        rg3.check("selected".equals(sp.getString("notif_mode", "all")) ? rbSel.getId() : rbAll.getId());
        rg3.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                sp.edit().putString("notif_mode", id == rbSel.getId() ? "selected" : "all").apply();
            }
        });
        root.addView(rg3);
        root.addView(button("Choose apps...", new View.OnClickListener() {
            public void onClick(View v) {
                showAppPicker();
            }
        }));

        TextView l2 = new TextView(this);
        l2.setText("Glow style");
        l2.setTextSize(18);
        l2.setPadding(0, dp(24), 0, 0);
        root.addView(l2);

        final RadioGroup rg2 = new RadioGroup(this);
        final RadioButton rbB = new RadioButton(this);
        rbB.setText("Blue-purple (when not using app colors)");
        rbB.setId(View.generateViewId());
        final RadioButton rbG = new RadioButton(this);
        rbG.setText("Rainbow");
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

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText((isServiceOn()
                ? "Accessibility service: ON"
                : "Accessibility service: OFF - enable \"Edge Glow\"")
                + "\n" + (notifAccessOn()
                ? "Notification access: ON"
                : "Notification access: OFF - enable \"Edge Glow\""));
    }

    boolean isServiceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName cn = new ComponentName(this, GlowService.class);
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
    }

    boolean notifAccessOn() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    void check(LinearLayout root, String text, final String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setChecked(sp.getBoolean(key, def));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean(key, on).apply();
            }
        });
        root.addView(c);
    }

    void showAppPicker() {
        PackageManager pm = getPackageManager();
        Intent li = new Intent(Intent.ACTION_MAIN);
        li.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(li, 0);
        List<String[]> apps = new ArrayList<String[]>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (seen.add(pkg)) apps.add(new String[]{ri.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                return a[0].compareToIgnoreCase(b[0]);
            }
        });
        final Set<String> sel = new HashSet<String>(sp.getStringSet("notif_apps", new HashSet<String>()));
        final String[] pkgs = new String[apps.size()];
        String[] names = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i)[0];
            pkgs[i] = apps.get(i)[1];
            checked[i] = sel.contains(pkgs[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Apps that show the glow")
                .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface d, int which, boolean on) {
                        if (on) sel.add(pkgs[which]);
                        else sel.remove(pkgs[which]);
                    }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        sp.edit().putStringSet("notif_apps", new HashSet<String>(sel)).apply();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    public static class NotifService extends NotificationListenerService {
        long last;

        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            try {
                handle(sbn);
            } catch (Throwable t) {}
        }

        void handle(StatusBarNotification sbn) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (!sp.getBoolean("notif_on", true)) return;
            String pkg = sbn.getPackageName();
            if (pkg.equals(getPackageName())) return;
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if (sp.getBoolean("notif_skip_silent", true)) {
                RankingMap rm = getCurrentRanking();
                Ranking r = new Ranking();
                if (rm != null && rm.getRanking(sbn.getKey(), r)
                        && r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) return;
            }
            if (sp.getBoolean("notif_skip_dnd", true) && getCurrentInterruptionFilter() > 1) return;
            if ("selected".equals(sp.getString("notif_mode", "all"))) {
                Set<String> allowed = sp.getStringSet("notif_apps", new HashSet<String>());
                if (!allowed.contains(pkg)) return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - last < 1500) return;
            last = now;
            int color = sp.getBoolean("notif_appcolor", true) ? dominant(pkg) : 0;
            sp.edit().putInt("notif_color", color)
                    .putLong("notif_glow", System.currentTimeMillis()).apply();
        }

        int dominant(String pkg) {
            try {
                Drawable d = getPackageManager().getApplicationIcon(pkg);
                Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                d.setBounds(0, 0, 32, 32);
                d.draw(c);
                float[] score = new float[12];
                float[] hsv = new float[3];
                for (int x = 0; x < 32; x++) {
                    for (int y = 0; y < 32; y++) {
                        int p = b.getPixel(x, y);
                        if (Color.alpha(p) < 200) continue;
                        Color.colorToHSV(p, hsv);
                        score[Math.min(11, (int) (hsv[0] / 30f))] += hsv[1] * hsv[2];
                    }
                }
                int best = 0;
                for (int i = 1; i < 12; i++) if (score[i] > score[best]) best = i;
                if (score[best] < 12f) return 0;
                return Color.HSVToColor(new float[]{best * 30f + 15f, 0.85f, 1f});
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    public static class GlowService extends AccessibilityService {
        WindowManager wm;
        View glow;
        ValueAnimator anim;
        SharedPreferences sp;
        final SharedPreferences.OnSharedPreferenceChangeListener lis =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                        if ("glow_test".equals(k)) {
                            showGlow(0);
                        } else if ("notif_glow".equals(k)) {
                            showGlow(sp.getInt("notif_color", 0));
                        }
                    }
                };

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.registerOnSharedPreferenceChangeListener(lis);
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent e) {}

        @Override
        public void onInterrupt() {}

        @Override
        public boolean onUnbind(Intent i) {
            hideGlow();
            if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(lis);
            return super.onUnbind(i);
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

        static int mix(int c, int t, float f) {
            return Color.rgb((int) (Color.red(c) * (1 - f) + Color.red(t) * f),
                    (int) (Color.green(c) * (1 - f) + Color.green(t) * f),
                    (int) (Color.blue(c) * (1 - f) + Color.blue(t) * f));
        }

        void showGlow(int accent) {
            if (wm == null || sp == null) return;
            hideGlow();
            float d = getResources().getDisplayMetrics().density;
            final GlowView g = new GlowView(this,
                    (int) (sp.getInt("glow_depth2", 10) * d),
                    sp.getInt("glow_corner", 36) * d,
                    accent != 0
                            ? new int[]{accent, mix(accent, 0xFFFFFFFF, 0.55f), accent}
                            : palette(sp.getString("glow_style", "bixby")));
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
