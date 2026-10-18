package com.bxt.test;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String PKG = "com.samsung.android.bixby.agent";
    static final String AH = "com.samsung.android.bixby.assistanthome.";
    TextView log;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 96, 32, 32);

        add(root, "1) Alias + TYPING (SHORTCUT)", new Runnable() { public void run() {
            Intent i = new Intent("com.samsung.android.bixby.assistanthome.SHORTCUT");
            i.setComponent(new ComponentName(PKG, AH + "AssistantHomeLauncherActivity"));
            i.putExtra("launch_type", "LAUNCH_TYPE_TYPING");
            i.putExtra("entry_point", "ENTRY_POINT_SHORTCUT");
            go(i);
        }});
        add(root, "2) Alias + VOICE (SHORTCUT)", new Runnable() { public void run() {
            Intent i = new Intent("com.samsung.android.bixby.assistanthome.SHORTCUT");
            i.setComponent(new ComponentName(PKG, AH + "AssistantHomeLauncherActivity"));
            i.putExtra("launch_type", "LAUNCH_TYPE_VOICE");
            i.putExtra("entry_point", "ENTRY_POINT_SHORTCUT");
            go(i);
        }});
        add(root, "3) AliasActivity direct (expected to fail)", new Runnable() { public void run() {
            Intent i = new Intent("com.samsung.android.bixby.assistanthome.SHORTCUT");
            i.setComponent(new ComponentName(PKG, AH + "AssistantHomeAliasActivity"));
            i.putExtra("launch_type", "LAUNCH_TYPE_TYPING");
            i.putExtra("entry_point", "ENTRY_POINT_SHORTCUT");
            go(i);
        }});
        add(root, "4) AI_ASSIST -> MainActivity", new Runnable() { public void run() {
            Intent i = new Intent("com.samsung.android.intent.action.AI_ASSIST");
            i.setComponent(new ComponentName(PKG, AH + "AssistantHomeMainActivity"));
            go(i);
        }});
        add(root, "5) VOICE_COMMAND (HeadSetReceiver)", new Runnable() { public void run() {
            Intent i = new Intent(Intent.ACTION_VOICE_COMMAND);
            i.setPackage(PKG);
            go(i);
        }});
        add(root, "6) AI_ASSIST implicit", new Runnable() { public void run() {
            Intent i = new Intent("com.samsung.android.intent.action.AI_ASSIST");
            i.setPackage(PKG);
            go(i);
        }});

        log = new TextView(this);
        log.setTextSize(14);
        log.setPadding(0, 32, 0, 0);
        root.addView(log);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    void add(LinearLayout root, String text, final Runnable r) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setAllCaps(false);
        bt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { r.run(); }
        });
        root.addView(bt);
    }

    void go(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            log.setText("OK: started\n" + i.toUri(0));
        } catch (Throwable t) {
            log.setText("FAIL: " + t.getClass().getSimpleName() + "\n" + t.getMessage());
        }
    }
}
