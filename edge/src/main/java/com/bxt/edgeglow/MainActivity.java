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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
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
import android.widget.EditText;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    static final String PREFS = "cfg";
    static final String[] CNAMES = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue",
            "Purple", "Pink", "White", "Custom hex..."};
    static final int[] CVALS = {0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A, 0xFF34C759, 0xFF22D3EE,
            0xFF2F7BFF, 0xFFB14CFF, 0xFFFF4DA6, 0xFFFFFFFF, 0};
    TextView status;
    SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Edge Glow");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(15);
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
        root.addView(button("3) Battery: set to Unrestricted", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        CheckBox ck = new CheckBox(this);
        ck.setText("Keep running in background (shows a small notification)");
        ck.setChecked(sp.getBoolean("keepalive", true));
        ck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                sp.edit().putBoolean("keepalive", on).apply();
                syncKeep();
            }
        });
        root.addView(ck);
        root.addView(button("App info (Allow restricted settings)", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));

        heading(root, "Notifications");
        check(root, "Glow on notifications", "notif_on", true);
        check(root, "Use each app's color", "notif_appcolor", true);
        check(root, "Skip silent notifications", "notif_skip_silent", true);
        check(root, "Skip when Do Not Disturb is on", "notif_skip_dnd", true);
        radio(root, "notif_mode", "all",
                new String[]{"All apps", "Selected apps only"},
                new String[]{"all", "selected"});
        root.addView(button("Choose apps...", new View.OnClickListener() {
            public void onClick(View v) {
                showAppPicker();
            }
        }));

        heading(root, "Shape");
        radio(root, "glow_shape", "line",
                new String[]{"Thin line (Samsung-like)", "Soft glow"},
                new String[]{"line", "soft"});
        check(root, "Soft halo around the line", "line_halo", true);
        seek(root, "Line thickness (dp)", "line_w", 4, 14);
        seek(root, "Soft glow depth (dp)", "glow_depth2", 10, 40);
        seek(root, "Screen corner radius (dp)", "glow_corner", 36, 80);
        seek(root, "Hold time (ms)", "hold_ms", 800, 5000);

        heading(root, "Colors (when not using each app's color)");
        radio(root, "glow_palette", "rainbow",
                new String[]{"Rainbow", "Two colors", "One color", "Blue-purple"},
                new String[]{"rainbow", "two", "one", "bixby"});
        colorRow(root, "Color 1", "color1", 0xFF2F7BFF);
        colorRow(root, "Color 2", "color2", 0xFFB14CFF);

        root.addView(button("Test glow in 5 seconds (then go Home)", delayedTest(5)));
        root.addView(button("Test glow in 30 seconds (then go Home)", delayedTest(30)));
        root.addView(button("Test glow in 2 minutes (then go Home)", delayedTest(120)));
        root.addView(button("Test glow", new View.OnClickListener() {
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

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncKeep();
        status.setText("Accessibility service: " + (isServiceOn() ? "ON" : "OFF - enable \"Edge Glow\"")
                + "\nOverlay service running: " + (GlowService.instance != null ? "YES" : "NO")
                + "\nNotification access: " + (notifAccessOn() ? "ON" : "OFF - enable \"Edge Glow\"")
                + "\nKeep-alive service: " + (KeepAlive.running ? "running" : "not running")
                + "\nLast notification: " + sp.getString("last_event", "none yet")
                + "\nGlow log:\n" + sp.getString("glow_log", "none yet"));
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

    void heading(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setPadding(0, dp(24), 0, 0);
        root.addView(t);
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

    void radio(LinearLayout root, final String key, String def, String[] labels, final String[] values) {
        RadioGroup g = new RadioGroup(this);
        final int[] ids = new int[labels.length];
        String cur = sp.getString(key, def);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(labels[i]);
            r.setId(View.generateViewId());
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

    void colorRow(LinearLayout root, String label, final String key, int def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final View sw = new View(this);
        sw.setBackgroundColor(sp.getInt(key, def));
        row.addView(button(label, new View.OnClickListener() {
            public void onClick(View v) {
                pickColor(key, sw);
            }
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.setMargins(dp(8), 0, 0, 0);
        row.addView(sw, lp);
        root.addView(row);
    }

    void pickColor(final String key, final View sw) {
        new AlertDialog.Builder(this)
                .setTitle("Pick a color")
                .setItems(CNAMES, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (CVALS[which] != 0) {
                            sp.edit().putInt(key, CVALS[which]).apply();
                            sw.setBackgroundColor(CVALS[which]);
                        } else {
                            hexDialog(key, sw);
                        }
                    }
                })
                .show();
    }

    void hexDialog(final String key, final View sw) {
        final EditText et = new EditText(this);
        et.setHint("#RRGGBB");
        new AlertDialog.Builder(this)
                .setTitle("Custom color")
                .setView(et)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            int c = Color.parseColor(et.getText().toString().trim()) | 0xFF000000;
                            sp.edit().putInt(key, c).apply();
                            sw.setBackgroundColor(c);
                        } catch (Throwable t) {
                            Toast.makeText(MainActivity.this, "Invalid color", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                        int color = sp.getBoolean("notif_appcolor", true) ? dominant(pkg) : 0;
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

        static int mix(int c, int t, float f) {
            return Color.rgb((int) (Color.red(c) * (1 - f) + Color.red(t) * f),
                    (int) (Color.green(c) * (1 - f) + Color.green(t) * f),
                    (int) (Color.blue(c) * (1 - f) + Color.blue(t) * f));
        }

        int[] colorsFor(int accent) {
            if (accent != 0) return new int[]{accent, mix(accent, 0xFFFFFFFF, 0.5f), accent};
            String pal = sp.getString("glow_palette", "rainbow");
            int c1 = sp.getInt("color1", 0xFF2F7BFF);
            int c2 = sp.getInt("color2", 0xFFB14CFF);
            if ("one".equals(pal)) return new int[]{c1, mix(c1, 0xFFFFFFFF, 0.5f), c1};
            if ("two".equals(pal)) return new int[]{c1, c2, c1};
            if ("bixby".equals(pal)) {
                return new int[]{0xFF2F7BFF, 0xFF4A4BEF, 0xFF7B3FE4, 0xFFB9A8FF, 0xFF7B3FE4, 0xFF4A4BEF, 0xFF2F7BFF};
            }
            return new int[]{0xFF3D7BFF, 0xFFB14CFF, 0xFFFF4D8D, 0xFFFFB020, 0xFF34D058, 0xFF22C7E8, 0xFF3D7BFF};
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
            hideGlow();
            ActivityManager.RunningAppProcessInfo ri = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(ri);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            stat("start imp=" + ri.importance + " interactive=" + pm.isInteractive()
                    + " save=" + pm.isPowerSaveMode() + " doze=" + pm.isDeviceIdleMode());
            float d = getResources().getDisplayMetrics().density;
            boolean line = !"soft".equals(sp.getString("glow_shape", "line"));
            final GlowView g = new GlowView(this, line, sp.getBoolean("line_halo", true),
                    (int) (Math.max(1, sp.getInt("line_w", 4)) * d),
                    (int) (Math.max(2, sp.getInt("glow_depth2", 10)) * d),
                    sp.getInt("glow_corner", 36) * d,
                    colorsFor(accent));
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
            g.play(300, Math.max(100, sp.getInt("hold_ms", 800)), 500, new Runnable() {
                public void run() {
                    stat("end " + snap(g) + " maxK=" + String.format(Locale.US, "%.2f", g.maxK));
                    if (glow == g) hideGlow();
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    stat("+0.3s " + snap(g));
                }
            }, 300);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    stat("+1.5s " + snap(g));
                }
            }, 1500);
        }

        String snap(GlowView g) {
            return "att=" + g.isAttachedToWindow() + " vis=" + g.getWindowVisibility()
                    + " size=" + g.getWidth() + "x" + g.getHeight()
                    + " c=" + g.cTicks + " h=" + g.hTicks + " dc=" + g.drawCalls + " d=" + g.draws;
        }
    }
}

class GlowView extends View {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final boolean line, halo;
    final int lineW, depth;
    final float corner;
    final int[] colors;
    final Matrix mat = new Matrix();
    final RectF rect = new RectF();
    final Handler h = new Handler(Looper.getMainLooper());
    SweepGradient sg;
    float k, angle;
    long start;
    int tin, thold, tout;
    boolean running;
    Runnable endCb;
    int cTicks, hTicks, draws, drawCalls;
    float maxK;

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
        f = Math.max(0f, Math.min(1f, f));
        k = f * f * (3f - 2f * f);
        if (k > maxK) maxK = k;
        angle = 360f * 0.7f * ms / total;
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

    GlowView(Context c, boolean line, boolean halo, int lineW, int depth, float corner, int[] colors) {
        super(c);
        this.line = line;
        this.halo = halo;
        this.lineW = lineW;
        this.depth = depth;
        this.corner = corner;
        this.colors = colors;
        paint.setStyle(Paint.Style.STROKE);
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
        maxK = 0f;
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
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sg = new SweepGradient(w / 2f, h / 2f, colors, null);
    }

    void rr(Canvas c, float inset) {
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float r = Math.max(0f, corner - inset);
        c.drawRoundRect(rect, r, r, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        drawCalls++;
        if (sg == null || k <= 0f) return;
        draws++;
        mat.setRotate(angle, getWidth() / 2f, getHeight() / 2f);
        sg.setLocalMatrix(mat);
        paint.setShader(sg);
        if (line) {
            float inset = lineW / 2f + 1f;
            if (halo) {
                paint.setAlpha((int) (255 * k * 0.07f));
                for (int i = 4; i >= 1; i--) {
                    paint.setStrokeWidth(lineW * (1f + i * 0.9f));
                    rr(c, inset);
                }
            }
            paint.setAlpha((int) (255 * k));
            paint.setStrokeWidth(lineW);
            rr(c, inset);
        } else {
            paint.setAlpha((int) (255 * k * 0.075f));
            int layers = 16;
            for (int i = layers; i >= 1; i--) {
                paint.setStrokeWidth(2f * depth * i / layers);
                rr(c, 0f);
            }
        }
    }
            }
