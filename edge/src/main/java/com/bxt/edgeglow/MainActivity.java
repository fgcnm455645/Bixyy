package com.bxt.edgeglow;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    static final String[] CKEYS = {"fx", "color_auto", "color_val", "color_two", "color_val2",
            "transp", "dur", "line_w", "glow_corner", "glow_depth2", "fx_edge"};
    static final int[] SWATCH = {0xFF0A84FF, 0xFFFF5A68, 0xFFE02B2B, 0xFFF07A12, 0xFFC1DF62,
            0xFF2ECC71, 0xFF00BFBF, 0xFF8AB4D6, 0xFF5B5BFF, 0xFFC27373, 0xFF9B2FAE};
    static final int GRAY = 0xFFB0B0B0;

    SharedPreferences sp;
    Map<String, Object> snap = new HashMap<String, Object>();
    FxView preview;
    LinearLayout content;
    TextView status;
    TextView[] tabs = new TextView[4];
    FxView[] cells;
    TextView[] labels;
    int tab = 0;

    @Override
    protected void onCreate(Bundle b) {
        setTheme(android.R.style.Theme_Material_NoActionBar);
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        snap.putAll(sp.getAll());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);
        preview = new FxView(this, sp, false, null);
        box.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView pill = tv("  How are you?  ", 16, Color.WHITE);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0xFF262626);
        pg.setCornerRadius(dp(24));
        pill.setBackground(pg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        plp.topMargin = dp(48);
        box.addView(pill, plp);
        root.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.9f));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(0xFF252525);
        float rr = dp(28);
        sg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        sheet.setBackground(sg);
        sheet.setPadding(dp(12), dp(14), dp(12), dp(6));

        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Effect", "Color", "Advanced", "More"};
        for (int i = 0; i < 4; i++) {
            TextView t = tv(names[i], 15, 0xFFDDDDDD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            final int idx = i;
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectTab(idx);
                }
            });
            tabBar.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tabs[i] = t;
        }
        sheet.addView(tabBar);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(14), dp(4), dp(8));
        sv.addView(content);
        sheet.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setTextColor(0xFF5B8CFF);
        cancel.setBackgroundColor(Color.TRANSPARENT);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                revert();
                finish();
            }
        });
        Button done = new Button(this);
        done.setText("Done");
        done.setAllCaps(false);
        done.setTextColor(0xFF5B8CFF);
        done.setBackgroundColor(Color.TRANSPARENT);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(cancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(done, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sheet.addView(bar);

        root.addView(sheet, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.3f));
        setContentView(root);
        selectTab(0);
    }

    void revert() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : CKEYS) {
            Object v = snap.get(k);
            if (v == null) e.remove(k);
            else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof String) e.putString(k, (String) v);
        }
        e.apply();
    }

    void selectTab(int idx) {
        tab = idx;
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(i == idx ? 0xFF3D3D3D : Color.TRANSPARENT);
            tabs[i].setBackground(g);
            tabs[i].setTextColor(i == idx ? Color.WHITE : GRAY);
        }
        content.removeAllViews();
        status = null;
        if (idx == 0) buildEffects();
        else if (idx == 1) buildColor();
        else if (idx == 2) buildAdvanced();
        else buildMore();
        if (preview != null) preview.restart();
    }

    void buildEffects() {
        String cur = sp.getString("fx", "basic");
        int n = Fx.IDS.length;
        cells = new FxView[n];
        labels = new TextView[n];
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(14);
                content.addView(row, rp);
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            FxView v = new FxView(this, sp, true, Fx.IDS[i]);
            v.setBackground(cellBg(Fx.IDS[i].equals(cur)));
            TextView l = tv(Fx.NAMES[i], 13, Fx.IDS[i].equals(cur) ? Color.WHITE : GRAY);
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, dp(6), 0, 0);
            cell.addView(v, new LinearLayout.LayoutParams(dp(76), dp(76)));
            cell.addView(l);
            final int idx = i;
            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    sp.edit().putString("fx", Fx.IDS[idx]).apply();
                    refreshCells();
                    preview.restart();
                }
            });
            cells[i] = v;
            labels[i] = l;
            row.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    GradientDrawable cellBg(boolean sel) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.BLACK);
        g.setCornerRadius(dp(16));
        if (sel) g.setStroke(dp(2), Color.WHITE);
        return g;
    }

    void refreshCells() {
        String cur = sp.getString("fx", "basic");
        for (int i = 0; i < Fx.IDS.length; i++) {
            boolean sel = Fx.IDS[i].equals(cur);
            cells[i].setBackground(cellBg(sel));
            labels[i].setTextColor(sel ? Color.WHITE : GRAY);
        }
    }

    void buildColor() {
        boolean auto = sp.getBoolean("color_auto", true);
        final RadioGroup g = new RadioGroup(this);
        final RadioButton ra = radioBtn("Auto (each app's color)");
        final RadioButton rc = radioBtn("Color");
        g.addView(ra);
        g.addView(rc);
        g.check(auto ? ra.getId() : rc.getId());
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup rg, int id) {
                sp.edit().putBoolean("color_auto", id == ra.getId()).apply();
                preview.restart();
            }
        });
        content.addView(g);
        swatches("color_val", 0xFF0A84FF);
        CheckBox two = new CheckBox(this);
        two.setText("Two colors (gradient line)");
        two.setTextColor(Color.WHITE);
        two.setChecked(sp.getBoolean("color_two", false));
        two.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean("color_two", on).apply();
                selectTab(1);
            }
        });
        content.addView(two);
        if (sp.getBoolean("color_two", false)) {
            TextView l = tv("Second color", 14, GRAY);
            l.setPadding(dp(4), dp(10), 0, dp(4));
            content.addView(l);
            swatches("color_val2", 0xFFB14CFF);
        }
        TextView note = tv("Multicolor and Glitter use their own colors.", 12, GRAY);
        note.setPadding(dp(4), dp(12), 0, 0);
        content.addView(note);
    }

    RadioButton radioBtn(String text) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setTextColor(Color.WHITE);
        r.setId(View.generateViewId());
        return r;
    }

    void swatches(final String key, final int def) {
        int cur = sp.getInt(key, def);
        int n = SWATCH.length + 1;
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(row);
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setGravity(Gravity.CENTER);
            View sw = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            if (i < SWATCH.length) {
                gd.setColor(SWATCH[i]);
                if (SWATCH[i] == cur) gd.setStroke(dp(3), Color.WHITE);
            } else {
                gd.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                gd.setColors(new int[]{0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF22D3EE,
                        0xFF0A84FF, 0xFFB14CFF, 0xFFFF3B30});
            }
            sw.setBackground(gd);
            final int idx = i;
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (idx < SWATCH.length) {
                        sp.edit().putInt(key, SWATCH[idx]).putBoolean("color_auto", false).apply();
                        selectTab(1);
                    } else {
                        customColor(key, def);
                    }
                }
            });
            cell.addView(sw, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
            row.addView(cell, lp);
        }
    }

    void customColor(final String key, int def) {
        final float[] hsv = new float[3];
        Color.colorToHSV(sp.getInt(key, def), hsv);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));
        final View prev = new View(this);
        prev.setBackgroundColor(Color.HSVToColor(hsv));
        box.addView(prev, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        final SeekBar sh = new SeekBar(this);
        sh.setMax(360);
        sh.setProgress((int) hsv[0]);
        final SeekBar ss = new SeekBar(this);
        ss.setMax(100);
        ss.setProgress((int) (hsv[1] * 100));
        final SeekBar sv = new SeekBar(this);
        sv.setMax(100);
        sv.setProgress((int) (hsv[2] * 100));
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                hsv[0] = sh.getProgress();
                hsv[1] = ss.getProgress() / 100f;
                hsv[2] = sv.getProgress() / 100f;
                prev.setBackgroundColor(Color.HSVToColor(hsv));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
        sh.setOnSeekBarChangeListener(l);
        ss.setOnSeekBarChangeListener(l);
        sv.setOnSeekBarChangeListener(l);
        box.addView(tv("Hue", 13, GRAY));
        box.addView(sh);
        box.addView(tv("Saturation", 13, GRAY));
        box.addView(ss);
        box.addView(tv("Brightness", 13, GRAY));
        box.addView(sv);
        new AlertDialog.Builder(this)
                .setTitle("Custom color")
                .setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        sp.edit().putInt(key, Color.HSVToColor(hsv) | 0xFF000000)
                                .putBoolean("color_auto", false).apply();
                        selectTab(1);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void buildAdvanced() {
        slider("Transparency", "Low", "High", "transp", 0);
        slider("Duration", "Short", "Long", "dur", 20);
        TextView h = tv("Fine tuning", 16, Color.WHITE);
        h.setPadding(dp(4), dp(22), 0, 0);
        content.addView(h);
        seek(content, "Line thickness (dp)", "line_w", 4, 14);
        seek(content, "Screen corner radius (dp)", "glow_corner", 36, 80);
        seek(content, "Soft glow depth (dp)", "glow_depth2", 10, 40);
        check(content, "Edge line under Bubble, Heart and Fireworks", "fx_edge", true);
    }

    void slider(String title, String low, String high, final String key, int def) {
        TextView t = tv(title, 17, Color.WHITE);
        t.setPadding(dp(4), dp(16), 0, 0);
        content.addView(t);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView a = tv(low, 13, GRAY);
        TextView b = tv(high, 13, GRAY);
        b.setGravity(Gravity.END);
        r.addView(a, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.setPadding(dp(4), 0, dp(4), 0);
        content.addView(r);
        SeekBar s = new SeekBar(this);
        s.setMax(100);
        s.setProgress(sp.getInt(key, def));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                sp.edit().putInt(key, p).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                preview.restart();
            }
        });
        content.addView(s);
    }

    void buildMore() {
        status = tv("", 14, Color.WHITE);
        status.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(status);
        content.addView(button("1) Open accessibility settings", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        content.addView(button("2) Grant notification access", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }));
        content.addView(button("3) Battery: set to Unrestricted", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        content.addView(button("App info (Allow restricted settings)", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        CheckBox ck = new CheckBox(this);
        ck.setText("Keep running in background (shows a small notification)");
        ck.setTextColor(Color.WHITE);
        ck.setChecked(sp.getBoolean("keepalive", true));
        ck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean("keepalive", on).apply();
                syncKeep();
            }
        });
        content.addView(ck);

        TextView h = tv("Notifications", 16, Color.WHITE);
        h.setPadding(dp(4), dp(18), 0, 0);
        content.addView(h);
        check(content, "Glow on notifications", "notif_on", true);
        check(content, "Skip silent notifications", "notif_skip_silent", true);
        check(content, "Skip when Do Not Disturb is on", "notif_skip_dnd", true);
        radio(content, "notif_mode", "all",
                new String[]{"All apps", "Selected apps only"},
                new String[]{"all", "selected"});
        content.addView(button("Choose apps...", new View.OnClickListener() {
            public void onClick(View v) {
                showAppPicker();
            }
        }));

        TextView t = tv("Tests", 16, Color.WHITE);
        t.setPadding(dp(4), dp(18), 0, 0);
        content.addView(t);
        content.addView(button("Test glow", new View.OnClickListener() {
            public void onClick(View v) {
                GlowService s = GlowService.instance;
                if (s == null) {
                    Toast.makeText(MainActivity.this, "Overlay service is not running",
                            Toast.LENGTH_LONG).show();
                } else {
                    s.showGlow(0);
                }
            }
        }));
        content.addView(button("Test glow in 5 seconds (then go Home)", delayedTest(5)));
        content.addView(button("Test glow in 30 seconds (then go Home)", delayedTest(30)));
        refreshStatus();
    }

    void refreshStatus() {
        if (status == null) return;
        status.setText("Accessibility service: " + (isServiceOn() ? "ON" : "OFF - enable \"Edge Glow\"")
                + "\nOverlay service running: " + (GlowService.instance != null ? "YES" : "NO")
                + "\nNotification access: " + (notifAccessOn() ? "ON" : "OFF - enable \"Edge Glow\"")
                + "\nKeep-alive service: " + (KeepAlive.running ? "running" : "not running")
                + "\nLast notification: " + sp.getString("last_event", "none yet")
                + "\nGlow log:\n" + sp.getString("glow_log", "none yet"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncKeep();
        refreshStatus();
        if (preview != null) preview.restart();
    }

    View.OnClickListener delayedTest(final int sec) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Go to the home screen now (" + sec + " s)",
                        Toast.LENGTH_LONG).show();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        GlowService s = GlowService.instance;
                        if (s != null) s.showGlow(0);
                    }
                }, sec * 1000L);
            }
        };
    }

    void syncKeep() {
        try {
            Intent i = new Intent(this, KeepAlive.class);
            if (sp.getBoolean("keepalive", true)) {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                else startService(i);
            } else {
                stopService(i);
            }
        } catch (Throwable t) {}
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

    TextView tv(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    void check(LinearLayout root, String text, final String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setChecked(sp.getBoolean(key, def));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean(key, on).apply();
                if (preview != null) preview.restart();
            }
        });
        root.addView(c);
    }

    void radio(LinearLayout root, final String key, String def, String[] labels, final String[] values) {
        RadioGroup g = new RadioGroup(this);
        final int[] ids = new int[labels.length];
        String cur = sp.getString(key, def);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = radioBtn(labels[i]);
            ids[i] = r.getId();
            g.addView(r);
            if (values[i].equals(cur)) g.check(ids[i]);
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup rg, int id) {
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i] == id) sp.edit().putString(key, values[i]).apply();
                }
            }
        });
        root.addView(g);
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
        final TextView t = tv(name + ": " + sp.getInt(key, def), 14, GRAY);
        t.setPadding(dp(4), dp(14), 0, 0);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(sp.getInt(key, def));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                t.setText(name + ": " + p);
                sp.edit().putInt(key, p).apply();
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                preview.restart();
            }
        });
        root.addView(t);
        root.addView(s);
    }

    public static class KeepAlive extends Service {
        static boolean running;

        @Override
        public IBinder onBind(Intent i) {
            return null;
        }

        @Override
        public int onStartCommand(Intent i, int flags, int startId) {
            try {
                NotificationManager nm = getSystemService(NotificationManager.class);
                Notification.Builder nb;
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(new NotificationChannel("keep", "Edge Glow running",
                            NotificationManager.IMPORTANCE_MIN));
                    nb = new Notification.Builder(this, "keep");
                } else {
                    nb = new Notification.Builder(this);
                }
                nb.setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Edge Glow is running")
                        .setOngoing(true);
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(1, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(1, nb.build());
                }
                running = true;
            } catch (Throwable t) {
                running = false;
                stopSelf();
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            running = false;
            super.onDestroy();
        }
    }

    public static class NotifService extends NotificationListenerService {
        long last;

        @Override
        public void onNotificationPosted(StatusBarNotification sbn) {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String pkg = sbn.getPackageName();
            String result;
            try {
                String why = decide(sp, sbn, pkg);
                if (why != null) {
                    result = "skipped (" + why + ")";
                } else {
                    GlowService s = GlowService.instance;
                    if (s == null) {
                        result = "overlay service not running";
                    } else {
                        int color = sp.getBoolean("color_auto", true) ? dominant(pkg) : 0;
                        s.showGlow(color);
                        result = "glow shown";
                    }
                }
            } catch (Throwable t) {
                result = "error: " + t.getClass().getSimpleName();
            }
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            sp.edit().putString("last_event", time + "  " + pkg + "  " + result).apply();
        }

        String decide(SharedPreferences sp, StatusBarNotification sbn, String pkg) {
            if (!sp.getBoolean("notif_on", true)) return "disabled";
            if (pkg.equals(getPackageName())) return "own app";
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return "ongoing or group";
            if (sp.getBoolean("notif_skip_silent", true)) {
                RankingMap rm = getCurrentRanking();
                Ranking r = new Ranking();
                if (rm != null && rm.getRanking(sbn.getKey(), r)
                        && r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) return "silent";
            }
            if (sp.getBoolean("notif_skip_dnd", true) && getCurrentInterruptionFilter() > 1) {
                return "do not disturb";
            }
            if ("selected".equals(sp.getString("notif_mode", "all"))) {
                Set<String> allowed = sp.getStringSet("notif_apps", new HashSet<String>());
                if (!allowed.contains(pkg)) return "app not selected";
            }
            long now = SystemClock.elapsedRealtime();
            if (now - last < 1500) return "too soon after the previous one";
            last = now;
            return null;
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
        static GlowService instance;
        WindowManager wm;
        GlowView glow;
        SharedPreferences sp;

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
            instance = this;
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent e) {}

        @Override
        public void onInterrupt() {}

        @Override
        public boolean onUnbind(Intent i) {
            hideGlow();
            if (instance == this) instance = null;
            return super.onUnbind(i);
        }

        @Override
        public void onDestroy() {
            hideGlow();
            if (instance == this) instance = null;
            super.onDestroy();
        }

        void hideGlow() {
            if (glow != null) {
                glow.stop();
                if (wm != null) {
                    try { wm.removeView(glow); } catch (Throwable t) {}
                }
            }
            glow = null;
        }

        void stat(String m) {
            String old = sp.getString("glow_log", "");
            String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = Math.max(0, lines.length - 7); i < lines.length; i++) sb.append(lines[i]).append("\n");
            sb.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())).append(" ").append(m);
            sp.edit().putString("glow_log", sb.toString()).apply();
        }

        void showGlow(final int accent) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        showGlow(accent);
                    }
                });
                return;
            }
            if (wm == null || sp == null) return;
            FxP fp = FxP.load(sp, getResources().getDisplayMetrics().density, accent);
            if ("none".equals(fp.fx)) {
                stat("effect is None");
                return;
            }
            hideGlow();
            ActivityManager.RunningAppProcessInfo ri = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(ri);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            stat("start " + fp.fx + " imp=" + ri.importance + " interactive=" + pm.isInteractive()
                    + " save=" + pm.isPowerSaveMode() + " doze=" + pm.isDeviceIdleMode());
            final GlowView g = new GlowView(this, fp);
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
                stat("addView FAILED: " + t.getClass().getSimpleName() + " " + t.getMessage());
                return;
            }
            glow = g;
            g.play(300, FxP.holdMs(sp), 500, new Runnable() {
                public void run() {
                    stat("end " + snap(g));
                    if (glow == g) hideGlow();
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    stat("+0.3s " + snap(g));
                }
            }, 300);
        }

        String snap(GlowView g) {
            return "att=" + g.isAttachedToWindow() + " vis=" + g.getWindowVisibility()
                    + " size=" + g.getWidth() + "x" + g.getHeight()
                    + " c=" + g.cTicks + " h=" + g.hTicks + " dc=" + g.drawCalls + " d=" + g.draws;
        }
    }
}

class FxP {
    float d, lineW, corner, depth, alpha;
    boolean edge;
    int color;
    int[] lineColors;
    String fx;

    static int holdMs(SharedPreferences sp) {
        return 300 + sp.getInt("dur", 20) * 25;
    }

    static FxP load(SharedPreferences sp, float d, int accent) {
        FxP p = new FxP();
        p.d = d;
        p.fx = sp.getString("fx", "basic");
        p.lineW = Math.max(1, sp.getInt("line_w", 4)) * d;
        p.corner = sp.getInt("glow_corner", 36) * d;
        p.depth = Math.max(2, sp.getInt("glow_depth2", 10)) * d;
        p.edge = sp.getBoolean("fx_edge", true);
        p.alpha = 1f - 0.8f * sp.getInt("transp", 0) / 100f;
        boolean auto = sp.getBoolean("color_auto", true);
        if (auto) p.color = accent != 0 ? accent : 0xFF0A84FF;
        else p.color = sp.getInt("color_val", 0xFF0A84FF);
        if (sp.getBoolean("color_two", false)) {
            p.lineColors = new int[]{p.color, sp.getInt("color_val2", 0xFFB14CFF), p.color};
        }
        return p;
    }
}

class Fx {
    static final String[] IDS = {"none", "basic", "wave", "bubble", "multicolor", "glow",
            "glitter", "heart", "fireworks", "eclipse", "echo", "spotlight"};
    static final String[] NAMES = {"None", "Basic", "Wave", "Bubble", "Multicolor", "Glow",
            "Glitter", "Heart", "Fireworks", "Eclipse", "Echo", "Spotlight"};
    static final int[] MULTI = {0xFF2F7BFF, 0xFF22D3EE, 0xFF34C759, 0xFFFFD60A, 0xFF2F7BFF};
    static final int[] RAINBOW = {0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A, 0xFF34C759, 0xFF22D3EE,
            0xFF0A84FF, 0xFFB14CFF, 0xFFFF4DA6, 0xFFFF3B30};

    static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final RectF R = new RectF();
    static final Matrix M = new Matrix();
    static final Path HP = new Path();
    static final float[] POS = new float[2];

    static SweepGradient SG;
    static int[] SGC;
    static int sgW, sgH;
    static PathMeasure PM;
    static Path PP;
    static int pmW, pmH;
    static float pmI, pmC;

    static float cl(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    static float ease(float x) {
        x = cl(x);
        return x * x * (3f - 2f * x);
    }

    static int al(int color, float a) {
        int v = (int) (Color.alpha(color) * cl(a));
        return (v << 24) | (color & 0x00FFFFFF);
    }

    static void prep(Paint.Style st, float w) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(st);
        P.setStrokeWidth(w);
    }

    static SweepGradient sweep(int w, int h, int[] colors, float angle) {
        if (SG == null || SGC != colors || sgW != w || sgH != h) {
            SG = new SweepGradient(w / 2f, h / 2f, colors, null);
            SGC = colors;
            sgW = w;
            sgH = h;
        }
        M.setRotate(angle, w / 2f, h / 2f);
        SG.setLocalMatrix(M);
        return SG;
    }

    static PathMeasure perim(int w, int h, float inset, float corner) {
        if (PM == null || pmW != w || pmH != h || pmI != inset || pmC != corner) {
            PP = new Path();
            float r = Math.max(0f, corner - inset);
            PP.addRoundRect(new RectF(inset, inset, w - inset, h - inset), r, r, Path.Direction.CW);
            PM = new PathMeasure(PP, false);
            pmW = w;
            pmH = h;
            pmI = inset;
            pmC = corner;
        }
        return PM;
    }

    static void edgeLine(Canvas c, int w, int h, FxP p, float width, float a, float inset, float ang) {
        prep(Paint.Style.STROKE, width);
        if (p.lineColors != null) {
            P.setShader(sweep(w, h, p.lineColors, ang));
            P.setAlpha((int) (255 * cl(a)));
        } else {
            P.setColor(al(p.color, a));
        }
        R.set(inset, inset, w - inset, h - inset);
        float r = Math.max(0f, p.corner - inset);
        c.drawRoundRect(R, r, r, P);
    }

    static void palLine(Canvas c, int w, int h, FxP p, int[] pal, float ang, float a, float width, float inset) {
        prep(Paint.Style.STROKE, width);
        P.setShader(sweep(w, h, pal, ang));
        P.setAlpha((int) (255 * cl(a)));
        R.set(inset, inset, w - inset, h - inset);
        float r = Math.max(0f, p.corner - inset);
        c.drawRoundRect(R, r, r, P);
    }

    static void basic(Canvas c, int w, int h, FxP p, float a, float ang) {
        float inset = p.lineW / 2f + 1f;
        for (int i = 4; i >= 1; i--) {
            edgeLine(c, w, h, p, p.lineW * (1f + i * 0.9f), a * 0.07f, inset, ang);
        }
        edgeLine(c, w, h, p, p.lineW, a, inset, ang);
    }

    static void heart(Canvas c, float cx, float cy, float s) {
        HP.reset();
        HP.moveTo(cx, cy + s * 0.55f);
        HP.cubicTo(cx + s * 1.1f, cy - s * 0.05f, cx + s * 0.55f, cy - s * 0.95f, cx, cy - s * 0.35f);
        HP.cubicTo(cx - s * 0.55f, cy - s * 0.95f, cx - s * 1.1f, cy - s * 0.05f, cx, cy + s * 0.55f);
        HP.close();
        c.drawPath(HP, P);
    }

    static void particles(Canvas c, int w, int h, FxP p, float t, float a, boolean hearts) {
        float inset = p.lineW / 2f + 1f;
        PathMeasure pm = perim(w, h, inset, p.corner);
        float len = pm.getLength();
        float cx = w / 2f, cy = h / 2f, d = p.d;
        Random rnd = new Random(hearts ? 23 : 11);
        int n = hearts ? 16 : 24;
        for (int i = 0; i < n; i++) {
            float s = rnd.nextFloat();
            float delay = rnd.nextFloat() * 0.45f;
            float size = (hearts ? 5f + rnd.nextFloat() * 7f : 2f + rnd.nextFloat() * 4f) * d;
            float dist = (40f + rnd.nextFloat() * 110f) * d;
            float u = (t - delay) / 0.55f;
            if (u < 0f || u > 1f) continue;
            pm.getPosTan(s * len, POS, null);
            float dx = cx - POS[0], dy = cy - POS[1];
            float L = (float) Math.sqrt(dx * dx + dy * dy);
            if (L < 1f) continue;
            dx /= L;
            dy /= L;
            float x = POS[0] + dx * dist * u;
            float y = POS[1] + dy * dist * u;
            float aa = a * (1f - u) * (u < 0.15f ? u / 0.15f : 1f);
            prep(Paint.Style.FILL, 0f);
            P.setColor(al(p.color, aa));
            if (hearts) heart(c, x, y, size);
            else c.drawCircle(x, y, size, P);
        }
    }

    static void draw(Canvas c, int w, int h, FxP p, float t, float k) {
        float a = k * p.alpha;
        if (a <= 0.003f || w <= 0 || h <= 0) return;
        String fx = p.fx;
        float d = p.d;
        float inset = p.lineW / 2f + 1f;
        float ang = 360f * t * 0.7f;
        if ("basic".equals(fx)) {
            basic(c, w, h, p, a, ang);
        } else if ("glow".equals(fx)) {
            for (int i = 16; i >= 1; i--) {
                edgeLine(c, w, h, p, 2f * p.depth * i / 16f, a * 0.075f, 0f, ang);
            }
        } else if ("multicolor".equals(fx)) {
            for (int i = 4; i >= 1; i--) {
                palLine(c, w, h, p, MULTI, 360f * t * 1.2f, a * 0.07f, p.lineW * (1f + i * 0.9f), inset);
            }
            palLine(c, w, h, p, MULTI, 360f * t * 1.2f, a, p.lineW, inset);
        } else if ("glitter".equals(fx)) {
            palLine(c, w, h, p, RAINBOW, 360f * t * 0.8f, a, p.lineW, inset);
            PathMeasure pm = perim(w, h, inset, p.corner);
            float len = pm.getLength();
            int n = 16;
            for (int i = 0; i < n; i++) {
                float s = ((i + 0.5f) / n + t * 0.4f) % 1f;
                pm.getPosTan(s * len, POS, null);
                float tw = (float) Math.abs(Math.sin(t * 18f + i * 1.7f));
                prep(Paint.Style.FILL, 0f);
                P.setColor(al(0xFFFFFFFF, a * tw));
                c.drawCircle(POS[0], POS[1], p.lineW * (0.5f + tw) * 0.9f, P);
            }
        } else if ("wave".equals(fx)) {
            edgeLine(c, w, h, p, p.lineW, a, inset, ang);
            for (int j = 0; j < 3; j++) {
                float u = t * 1.5f - j * 0.2f;
                if (u < 0f || u > 1f) continue;
                edgeLine(c, w, h, p, p.lineW * (1f - 0.4f * u), a * (1f - u) * 0.9f,
                        inset + u * 40f * d, ang);
            }
        } else if ("bubble".equals(fx) || "heart".equals(fx)) {
            if (p.edge) basic(c, w, h, p, a, ang);
            particles(c, w, h, p, t, a, "heart".equals(fx));
        } else if ("fireworks".equals(fx)) {
            if (p.edge) basic(c, w, h, p, a, ang);
            Random rnd = new Random(5);
            for (int j = 0; j < 3; j++) {
                float bx = w * (0.25f + 0.5f * rnd.nextFloat());
                float by = h * (0.2f + 0.35f * rnd.nextFloat());
                float u = (t - j * 0.2f) / 0.5f;
                if (u < 0f || u > 1f) continue;
                float rr = u * 70f * d;
                prep(Paint.Style.STROKE, 2f * d);
                P.setStrokeCap(Paint.Cap.ROUND);
                P.setColor(al(p.color, a * (1f - u)));
                for (int q = 0; q < 24; q++) {
                    double an = q * Math.PI * 2 / 24;
                    float cs = (float) Math.cos(an), sn = (float) Math.sin(an);
                    c.drawLine(bx + cs * rr * 0.55f, by + sn * rr * 0.55f, bx + cs * rr, by + sn * rr, P);
                }
            }
        } else if ("eclipse".equals(fx)) {
            float cx = w / 2f, cy = h * 0.075f;
            float u = ease(Math.min(1f, t * 1.6f));
            float r1 = w * 0.30f * (0.35f + 0.65f * u);
            prep(Paint.Style.FILL, 0f);
            P.setShader(new RadialGradient(cx, cy, r1,
                    new int[]{al(p.color, a * 0.55f), al(p.color, a * 0.25f)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r1, P);
            float r2 = r1 * 0.42f;
            prep(Paint.Style.FILL, 0f);
            P.setShader(new RadialGradient(cx, cy, r2,
                    new int[]{al(p.color, a * 0.95f), al(p.color, a * 0.7f)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r2, P);
        } else if ("echo".equals(fx)) {
            float y0 = h * 0.15f, y1 = h * 0.85f;
            for (int side = 0; side < 2; side++) {
                for (int j = 0; j < 3; j++) {
                    float ph = t * 2f - j * 0.18f;
                    float pulse = 0.5f + 0.5f * (float) Math.sin(ph * Math.PI * 2);
                    float x = side == 0 ? inset + j * 8f * d : w - inset - j * 8f * d;
                    float aa = a * (0.35f + 0.65f * pulse) * (1f - 0.22f * j);
                    prep(Paint.Style.STROKE, p.lineW * (1f - 0.2f * j));
                    P.setStrokeCap(Paint.Cap.ROUND);
                    P.setShader(new LinearGradient(0, y0, 0, y1,
                            new int[]{al(p.color, 0f), al(p.color, aa), al(p.color, aa), al(p.color, 0f)},
                            new float[]{0f, 0.25f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                    c.drawLine(x, y0, x, y1, P);
                }
            }
        } else if ("spotlight".equals(fx)) {
            float cx = w / 2f;
            float u = ease(Math.min(1f, t * 2f));
            float r1 = w * 0.30f * (0.5f + 0.5f * u);
            prep(Paint.Style.FILL, 0f);
            P.setShader(new LinearGradient(0, 0, 0, h * 0.45f,
                    new int[]{al(p.color, a * 0.55f), al(p.color, 0f)}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h * 0.45f, P);
            prep(Paint.Style.FILL, 0f);
            P.setShader(new RadialGradient(cx, h * 0.02f, r1,
                    new int[]{al(p.color, a), al(p.color, 0f)}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, h * 0.02f, r1, P);
        }
    }
}

class FxView extends View {
    final SharedPreferences sp;
    final boolean thumb;
    final String fxId;
    final float density;
    FxP p;
    long start;
    boolean running;

    final Runnable loop = new Runnable() {
        public void run() {
            if (!running) return;
            invalidate();
            postOnAnimation(this);
        }
    };

    FxView(Context c, SharedPreferences sp, boolean thumb, String fxId) {
        super(c);
        this.sp = sp;
        this.thumb = thumb;
        this.fxId = fxId;
        this.density = c.getResources().getDisplayMetrics().density;
        reload();
    }

    void reload() {
        p = FxP.load(sp, density, 0);
        if (thumb) {
            p.fx = fxId;
            p.d = density * 0.42f;
            p.lineW = 2.2f * density;
            p.corner = 14f * density;
            p.depth = 5f * density;
            p.alpha = 1f;
        }
    }

    void restart() {
        reload();
        start = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!thumb) {
            running = true;
            start = SystemClock.uptimeMillis();
            postOnAnimation(loop);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (thumb) {
            Fx.draw(c, getWidth(), getHeight(), p, 0.42f, 1f);
            return;
        }
        int in = 300, out = 500, hold = FxP.holdMs(sp), pause = 700;
        int active = in + hold + out;
        long ms = (SystemClock.uptimeMillis() - start) % (active + pause);
        if (ms >= active) return;
        float f;
        if (ms < in) f = ms / (float) in;
        else if (ms < in + hold) f = 1f;
        else f = 1f - (ms - in - hold) / (float) out;
        Fx.draw(c, getWidth(), getHeight(), p, ms / (float) active, Fx.ease(f));
    }
}

class GlowView extends View {
    final FxP p;
    final Handler h = new Handler(Looper.getMainLooper());
    float k, t;
    long start;
    int tin, thold, tout;
    boolean running;
    Runnable endCb;
    int cTicks, hTicks, draws, drawCalls;

    void step() {
        long ms = SystemClock.uptimeMillis() - start;
        int total = tin + thold + tout;
        if (ms >= total) {
            finish();
            return;
        }
        float f;
        if (ms < tin) f = ms / (float) tin;
        else if (ms < tin + thold) f = 1f;
        else f = 1f - (ms - tin - thold) / (float) tout;
        k = Fx.ease(f);
        t = ms / (float) total;
        invalidate();
    }

    final Choreographer.FrameCallback cb = new Choreographer.FrameCallback() {
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            cTicks++;
            step();
            if (running) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    final Runnable hstep = new Runnable() {
        public void run() {
            if (!running) return;
            hTicks++;
            step();
            if (running) h.postDelayed(this, 16);
        }
    };

    final Runnable safety = new Runnable() {
        public void run() {
            if (running) finish();
        }
    };

    void finish() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(cb);
        h.removeCallbacks(hstep);
        h.removeCallbacks(safety);
        if (endCb != null) endCb.run();
    }

    GlowView(Context c, FxP p) {
        super(c);
        this.p = p;
    }

    void play(int in, int hold, int out, Runnable end) {
        tin = in;
        thold = hold;
        tout = out;
        endCb = end;
        start = SystemClock.uptimeMillis();
        cTicks = 0;
        hTicks = 0;
        draws = 0;
        drawCalls = 0;
        running = true;
        Choreographer.getInstance().postFrameCallback(cb);
        h.postDelayed(hstep, 16);
        h.postDelayed(safety, in + hold + out + 300);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(cb);
        h.removeCallbacks(hstep);
        h.removeCallbacks(safety);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        drawCalls++;
        if (k <= 0f) return;
        draws++;
        Fx.draw(c, getWidth(), getHeight(), p, t, k);
    }
}
