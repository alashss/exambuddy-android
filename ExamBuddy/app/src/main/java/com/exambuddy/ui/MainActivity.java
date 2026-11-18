package com.exambuddy.ui;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.exambuddy.R;
import com.exambuddy.ai.*;
import com.exambuddy.service.BubbleService;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;

    private EditText etGeminiKey, etGroqKey, etOpenrouterKey;
    private Spinner  spGroqModel, spOrModel, spActiveProvider;
    private Button   btnSave, btnStart, btnStop;
    private TextView tvStatus;
    private Switch   swAutoStart;

    private AIManager aiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        aiManager = new AIManager(this);
        bindViews();
        loadPrefs();
        setupListeners();
        updateStatus();
    }

    private void bindViews() {
        etGeminiKey      = findViewById(R.id.et_gemini_key);
        etGroqKey        = findViewById(R.id.et_groq_key);
        etOpenrouterKey  = findViewById(R.id.et_openrouter_key);
        spGroqModel      = findViewById(R.id.sp_groq_model);
        spOrModel        = findViewById(R.id.sp_or_model);
        spActiveProvider = findViewById(R.id.sp_active_provider);
        btnSave          = findViewById(R.id.btn_save);
        btnStart         = findViewById(R.id.btn_start);
        btnStop          = findViewById(R.id.btn_stop);
        tvStatus         = findViewById(R.id.tv_status);
        swAutoStart      = findViewById(R.id.sw_auto_start);

        // Groq model spinner
        ArrayAdapter<String> groqAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Llama 3.1 8B (Fast)", "Llama 3.1 70B (Smart)",
                         "Mixtral 8x7B", "Gemma2 9B"});
        groqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGroqModel.setAdapter(groqAdapter);

        // OpenRouter model spinner
        ArrayAdapter<String> orAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"Mistral 7B (Free)", "Llama3 8B (Free)",
                         "Gemma 7B (Free)", "Phi-3 Mini (Free)", "Qwen2 7B (Free)"});
        orAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spOrModel.setAdapter(orAdapter);
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(AIManager.PREF_NAME, MODE_PRIVATE);
        etGeminiKey.setText(prefs.getString(AIManager.PREF_GEMINI_KEY, ""));
        etGroqKey.setText(prefs.getString(AIManager.PREF_GROQ_KEY, ""));
        etOpenrouterKey.setText(prefs.getString(AIManager.PREF_OPENROUTER_KEY, ""));
        swAutoStart.setChecked(prefs.getBoolean("auto_start", false));

        String groqModel = prefs.getString(AIManager.PREF_GROQ_MODEL, GroqProvider.MODEL_LLAMA_8B);
        spGroqModel.setSelection(groqModelIndex(groqModel));

        String orModel = prefs.getString(AIManager.PREF_OR_MODEL, OpenRouterProvider.MODEL_MISTRAL_7B);
        spOrModel.setSelection(orModelIndex(orModel));
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> savePrefs());
        btnStart.setOnClickListener(v -> checkOverlayAndStart());
        btnStop.setOnClickListener(v -> stopService());
    }

    private void savePrefs() {
        SharedPreferences.Editor ed = getSharedPreferences(AIManager.PREF_NAME, MODE_PRIVATE).edit();
        ed.putString(AIManager.PREF_GEMINI_KEY,      etGeminiKey.getText().toString().trim());
        ed.putString(AIManager.PREF_GROQ_KEY,        etGroqKey.getText().toString().trim());
        ed.putString(AIManager.PREF_OPENROUTER_KEY,  etOpenrouterKey.getText().toString().trim());
        ed.putString(AIManager.PREF_GROQ_MODEL,      groqModelId(spGroqModel.getSelectedItemPosition()));
        ed.putString(AIManager.PREF_OR_MODEL,        orModelId(spOrModel.getSelectedItemPosition()));
        ed.putBoolean("auto_start", swAutoStart.isChecked());
        ed.apply();

        aiManager.reload();
        updateProviderSpinner();
        Toast.makeText(this, "✅ Disimpan! " + aiManager.getProviders().size()
            + " provider aktif", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateProviderSpinner() {
        String[] names = new String[aiManager.getProviders().size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = aiManager.getProviders().get(i).getName();
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, names.length > 0 ? names : new String[]{"(belum ada)"});
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spActiveProvider.setAdapter(a);
        spActiveProvider.setSelection(aiManager.getCurrentIndex());
        spActiveProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                aiManager.setCurrentIndex(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void checkOverlayAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } else {
            startBubbleService();
        }
    }

    private void startBubbleService() {
        Intent i = new Intent(this, BubbleService.class);
        i.setAction(BubbleService.ACTION_START);
        startForegroundService(i);
        tvStatus.setText("✅ ExamBuddy aktif");
        Toast.makeText(this, "Bubble aktif! Buka app ujian kamu.", Toast.LENGTH_LONG).show();
    }

    private void stopService() {
        Intent i = new Intent(this, BubbleService.class);
        i.setAction(BubbleService.ACTION_STOP);
        startService(i);
        tvStatus.setText("⭕ Tidak aktif");
    }

    private void updateStatus() {
        int count = aiManager.getProviders().size();
        if (count == 0) {
            tvStatus.setText("⚠️ Belum ada API key — masukkan minimal satu");
        } else {
            tvStatus.setText("✅ " + count + " provider siap");
        }
        updateProviderSpinner();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            startBubbleService();
        }
    }

    // ── Model index helpers ───────────────────────────────────────────────────

    private int groqModelIndex(String id) {
        switch (id) {
            case GroqProvider.MODEL_LLAMA_70B: return 1;
            case GroqProvider.MODEL_MIXTRAL:   return 2;
            case GroqProvider.MODEL_GEMMA2:    return 3;
            default: return 0;
        }
    }

    private String groqModelId(int pos) {
        switch (pos) {
            case 1: return GroqProvider.MODEL_LLAMA_70B;
            case 2: return GroqProvider.MODEL_MIXTRAL;
            case 3: return GroqProvider.MODEL_GEMMA2;
            default: return GroqProvider.MODEL_LLAMA_8B;
        }
    }

    private int orModelIndex(String id) {
        switch (id) {
            case OpenRouterProvider.MODEL_LLAMA3_8B:  return 1;
            case OpenRouterProvider.MODEL_GEMMA_7B:   return 2;
            case OpenRouterProvider.MODEL_PHI3_MINI:  return 3;
            case OpenRouterProvider.MODEL_QWEN2_7B:   return 4;
            default: return 0;
        }
    }

    private String orModelId(int pos) {
        switch (pos) {
            case 1: return OpenRouterProvider.MODEL_LLAMA3_8B;
            case 2: return OpenRouterProvider.MODEL_GEMMA_7B;
            case 3: return OpenRouterProvider.MODEL_PHI3_MINI;
            case 4: return OpenRouterProvider.MODEL_QWEN2_7B;
            default: return OpenRouterProvider.MODEL_MISTRAL_7B;
        }
    }
}
