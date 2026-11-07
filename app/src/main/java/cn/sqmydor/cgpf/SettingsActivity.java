package cn.sqmydor.cgpf;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.libxposed.service.XposedService;

/** Minimal settings screen: two feature switches and the proxy port (1-65535, default 7890). */
public final class SettingsActivity extends Activity implements App.Listener {
    private TextView status;
    private Switch probeSwitch;
    private Switch hideSwitch;
    private Switch rkpSwitch;
    private Switch rkpProxySwitch;
    private EditText portInput;
    private Button saveButton;
    private XposedService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status);

        probeSwitch = new Switch(this);
        probeSwitch.setText(R.string.probe_fix);
        root.addView(probeSwitch);
        root.addView(hint(R.string.probe_fix_desc));

        hideSwitch = new Switch(this);
        hideSwitch.setText(R.string.hide_cn);
        root.addView(hideSwitch);
        root.addView(hint(R.string.hide_cn_desc));

        rkpSwitch = new Switch(this);
        rkpSwitch.setText(R.string.rkp_renew);
        root.addView(rkpSwitch);
        root.addView(hint(R.string.rkp_renew_desc));

        rkpProxySwitch = new Switch(this);
        rkpProxySwitch.setText(R.string.rkp_proxy);
        root.addView(rkpProxySwitch);
        root.addView(hint(R.string.rkp_proxy_desc));

        TextView portLabel = new TextView(this);
        portLabel.setText(R.string.proxy_port);
        portLabel.setPadding(0, dp(8), 0, 0);
        root.addView(portLabel);

        portInput = new EditText(this);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setHint(String.valueOf(Config.DEFAULT_PROXY_PORT));
        portInput.setSingleLine(true);
        root.addView(portInput);
        root.addView(hint(R.string.proxy_port_desc));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        Button reset = new Button(this);
        reset.setText(R.string.reset_default);
        reset.setOnClickListener(v -> portInput.setText(String.valueOf(Config.DEFAULT_PROXY_PORT)));
        saveButton = new Button(this);
        saveButton.setText(R.string.save);
        saveButton.setOnClickListener(v -> save());
        buttons.addView(reset);
        buttons.addView(saveButton);
        root.addView(buttons);

        root.addView(hint(R.string.apply_note));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        render();
    }

    private TextView hint(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setAlpha(0.7f);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addListener(this);
    }

    @Override
    protected void onStop() {
        App.removeListener(this);
        super.onStop();
    }

    @Override
    public void onServiceChanged(XposedService s) {
        runOnUiThread(() -> {
            service = s;
            render();
        });
    }

    private void render() {
        boolean connected = service != null;
        probeSwitch.setEnabled(connected);
        hideSwitch.setEnabled(connected);
        rkpSwitch.setEnabled(connected);
        rkpProxySwitch.setEnabled(connected);
        portInput.setEnabled(connected);
        saveButton.setEnabled(connected);
        if (!connected) {
            status.setText(R.string.status_inactive);
            probeSwitch.setChecked(Config.DEFAULT_PROBE_ENABLED);
            hideSwitch.setChecked(Config.DEFAULT_HIDE_CN_FEATURES);
            rkpSwitch.setChecked(Config.DEFAULT_RKP_RENEW);
            rkpProxySwitch.setChecked(Config.DEFAULT_RKP_PROXY);
            portInput.setText(String.valueOf(Config.DEFAULT_PROXY_PORT));
            return;
        }
        status.setText(getString(R.string.status_active, service.getFrameworkName(),
                service.getFrameworkVersion(), service.getApiVersion()));
        try {
            SharedPreferences p = service.getRemotePreferences(Config.GROUP);
            probeSwitch.setChecked(p.getBoolean(Config.KEY_PROBE_ENABLED, Config.DEFAULT_PROBE_ENABLED));
            hideSwitch.setChecked(p.getBoolean(Config.KEY_HIDE_CN_FEATURES, Config.DEFAULT_HIDE_CN_FEATURES));
            rkpSwitch.setChecked(p.getBoolean(Config.KEY_RKP_RENEW, Config.DEFAULT_RKP_RENEW));
            rkpProxySwitch.setChecked(p.getBoolean(Config.KEY_RKP_PROXY, Config.DEFAULT_RKP_PROXY));
            portInput.setText(String.valueOf(Config.sanitizePort(
                    p.getInt(Config.KEY_PROXY_PORT, Config.DEFAULT_PROXY_PORT))));
        } catch (Throwable t) {
            status.setText(getString(R.string.status_error, String.valueOf(t.getMessage())));
        }
    }

    private void save() {
        if (service == null) return;
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (!Config.isValidPort(port)) {
            portInput.setError(getString(R.string.port_invalid));
            return;
        }
        try {
            service.getRemotePreferences(Config.GROUP).edit()
                    .putBoolean(Config.KEY_PROBE_ENABLED, probeSwitch.isChecked())
                    .putBoolean(Config.KEY_HIDE_CN_FEATURES, hideSwitch.isChecked())
                    .putBoolean(Config.KEY_RKP_RENEW, rkpSwitch.isChecked())
                    .putBoolean(Config.KEY_RKP_PROXY, rkpProxySwitch.isChecked())
                    .putInt(Config.KEY_PROXY_PORT, port)
                    .apply();
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.status_error, String.valueOf(t.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }
}
