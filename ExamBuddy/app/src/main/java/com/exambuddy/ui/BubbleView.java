package com.exambuddy.ui;

import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import com.exambuddy.R;
import com.exambuddy.ai.AIManager;
import com.exambuddy.ai.AIProvider;

public class BubbleView {

    private static final int BUBBLE_SIZE   = 180;  // dp
    private static final int PANEL_WIDTH   = 360;  // dp — collapsed panel width
    private static final int PANEL_HEIGHT  = 480;  // dp

    private final Context       ctx;
    private final WindowManager wm;
    private final AIManager     aiManager;
    private final Handler       mainHandler = new Handler(Looper.getMainLooper());

    // Views
    private View bubbleLayout;
    private View panelLayout;

    // Bubble widgets
    private ImageView  bubbleIcon;
    private ImageView  loadingRing;

    // Panel widgets
    private TextView   tvProviderName;
    private TextView   tvQuestionNum;
    private TextView   tvQuestion;
    private TextView   tvAnswer;
    private ScrollView svAnswer;
    private Button     btnAsk;
    private Button     btnPrev;
    private Button     btnNext;
    private Button     btnClose;
    private Button     btnCycleProvider;
    private ProgressBar progressBar;

    // State
    private boolean isPanelOpen = false;
    private boolean isAsking    = false;
    private String  currentQuestion = "";
    private String  currentChoices  = "";
    private int     currentQn    = 0;
    private int     totalQn      = 0;

    // Drag state
    private int lastX, lastY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 10;

    // Window params for bubble
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    public BubbleView(Context ctx, WindowManager wm, AIManager aiManager) {
        this.ctx       = ctx;
        this.wm        = wm;
        this.aiManager = aiManager;
    }

    public void attach() {
        inflateBubble();
        inflatePanel();
    }

    public void detach() {
        safeRemove(bubbleLayout);
        safeRemove(panelLayout);
    }

    // ── Inflate bubble ────────────────────────────────────────────────────────

    private void inflateBubble() {
        bubbleLayout = LayoutInflater.from(ctx).inflate(R.layout.view_bubble, null);
        bubbleIcon   = bubbleLayout.findViewById(R.id.iv_bubble_icon);
        loadingRing  = bubbleLayout.findViewById(R.id.iv_loading_ring);

        int size = dp(BUBBLE_SIZE);
        bubbleParams = new WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 20;
        bubbleParams.y = 400;

        setupBubbleDrag();
        wm.addView(bubbleLayout, bubbleParams);
        animateBubbleIn();
    }

    private void setupBubbleDrag() {
        bubbleLayout.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = (int) event.getRawX();
                    lastY = (int) event.getRawY();
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - lastX;
                    int dy = (int) event.getRawY() - lastY;
                    if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        bubbleParams.x += dx;
                        bubbleParams.y += dy;
                        wm.updateViewLayout(bubbleLayout, bubbleParams);
                        lastX = (int) event.getRawX();
                        lastY = (int) event.getRawY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        togglePanel();
                    }
                    snapToEdge();
                    return true;
            }
            return false;
        });
    }

    private void snapToEdge() {
        // Snap bubble to nearest screen edge for clean docking
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int targetX = (bubbleParams.x + dp(BUBBLE_SIZE) / 2 < screenW / 2) ? 0
            : screenW - dp(BUBBLE_SIZE) - 20;

        ValueAnimator anim = ValueAnimator.ofInt(bubbleParams.x, targetX);
        anim.setDuration(200);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            bubbleParams.x = (int) a.getAnimatedValue();
            if (bubbleLayout.isAttachedToWindow()) {
                wm.updateViewLayout(bubbleLayout, bubbleParams);
            }
        });
        anim.start();
    }

    // ── Inflate panel ─────────────────────────────────────────────────────────

    private void inflatePanel() {
        panelLayout = LayoutInflater.from(ctx).inflate(R.layout.view_panel, null);

        tvProviderName    = panelLayout.findViewById(R.id.tv_provider_name);
        tvQuestionNum     = panelLayout.findViewById(R.id.tv_question_num);
        tvQuestion        = panelLayout.findViewById(R.id.tv_question);
        tvAnswer          = panelLayout.findViewById(R.id.tv_answer);
        svAnswer          = panelLayout.findViewById(R.id.sv_answer);
        btnAsk            = panelLayout.findViewById(R.id.btn_ask);
        btnPrev           = panelLayout.findViewById(R.id.btn_prev);
        btnNext           = panelLayout.findViewById(R.id.btn_next);
        btnClose          = panelLayout.findViewById(R.id.btn_close);
        btnCycleProvider  = panelLayout.findViewById(R.id.btn_cycle_provider);
        progressBar       = panelLayout.findViewById(R.id.progress_bar);

        panelParams = new WindowManager.LayoutParams(
            dp(PANEL_WIDTH), dp(PANEL_HEIGHT),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.CENTER;
        panelParams.alpha   = 0.97f;

        setupPanelButtons();
        wm.addView(panelLayout, panelParams);
        panelLayout.setVisibility(View.GONE);
        updateProviderLabel();
    }

    private void setupPanelButtons() {
        btnAsk.setOnClickListener(v -> {
            if (!currentQuestion.isEmpty()) askAI();
        });

        btnClose.setOnClickListener(v -> closePanel());

        btnCycleProvider.setOnClickListener(v -> {
            aiManager.nextProvider();
            updateProviderLabel();
            showToast(aiManager.getCurrent() != null
                ? aiManager.getCurrent().getName() : "No provider");
        });

        btnPrev.setOnClickListener(v -> {
            // Send navigation event back to exam app
            Intent i = new Intent("com.exambuddy.NAV_PREV");
            ctx.sendBroadcast(i);
            showToast("← Soal sebelumnya");
        });

        btnNext.setOnClickListener(v -> {
            Intent i = new Intent("com.exambuddy.NAV_NEXT");
            ctx.sendBroadcast(i);
            showToast("Soal berikutnya →");
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setQuestion(String question, String choices, int qn, int total) {
        mainHandler.post(() -> {
            currentQuestion = question;
            currentChoices  = choices != null ? choices : "";
            currentQn       = qn;
            totalQn         = total;

            tvQuestion.setText(question);
            if (qn > 0 && total > 0) {
                tvQuestionNum.setText("Soal " + qn + " / " + total);
                tvQuestionNum.setVisibility(View.VISIBLE);
            }
            tvAnswer.setText("");
            tvAnswer.setHint("Tekan Tanya AI →");

            // Auto-ask when question arrives
            if (!question.isEmpty()) askAI();
        });
    }

    // ── Core: ask AI ──────────────────────────────────────────────────────────

    private void askAI() {
        if (isAsking) return;
        isAsking = true;

        String fullQuestion = currentQuestion;
        if (!currentChoices.isEmpty()) {
            fullQuestion += "\n\nPilihan:\n" + currentChoices;
        }

        setLoadingState(true);
        tvAnswer.setText("⏳ Memproses...");

        final String q = fullQuestion;
        aiManager.ask(q, new AIProvider.AICallback() {
            @Override
            public void onSuccess(String answer) {
                mainHandler.post(() -> {
                    isAsking = false;
                    setLoadingState(false);
                    tvAnswer.setText(answer);
                    svAnswer.post(() -> svAnswer.fullScroll(View.FOCUS_DOWN));
                    animateAnswer();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    isAsking = false;
                    setLoadingState(false);
                    tvAnswer.setText("❌ " + error);
                });
            }

            @Override
            public void onStream(String chunk) {
                mainHandler.post(() -> {
                    String current = tvAnswer.getText().toString();
                    if (current.equals("⏳ Memproses...")) current = "";
                    tvAnswer.setText(current + chunk);
                    svAnswer.post(() -> svAnswer.fullScroll(View.FOCUS_DOWN));
                });
            }
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void togglePanel() {
        if (isPanelOpen) closePanel();
        else openPanel();
    }

    private void openPanel() {
        isPanelOpen = true;
        panelLayout.setVisibility(View.VISIBLE);
        panelLayout.setAlpha(0f);
        panelLayout.setScaleX(0.85f);
        panelLayout.setScaleY(0.85f);
        panelLayout.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(new OvershootInterpolator(1.1f))
            .start();
        updateProviderLabel();
    }

    private void closePanel() {
        isPanelOpen = false;
        panelLayout.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(180)
            .withEndAction(() -> panelLayout.setVisibility(View.GONE))
            .start();
    }

    private void setLoadingState(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnAsk.setEnabled(!loading);
        btnAsk.setText(loading ? "⏳ Proses..." : "🤖 Tanya AI");
        if (loadingRing != null) {
            loadingRing.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) rotateLoading();
        }
    }

    private void rotateLoading() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(loadingRing, "rotation", 0f, 360f);
        anim.setDuration(1000);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.start();
    }

    private void animateBubbleIn() {
        bubbleLayout.setScaleX(0f);
        bubbleLayout.setScaleY(0f);
        bubbleLayout.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(300)
            .setInterpolator(new OvershootInterpolator())
            .start();
    }

    private void animateAnswer() {
        tvAnswer.setAlpha(0f);
        tvAnswer.animate().alpha(1f).setDuration(300).start();
    }

    private void updateProviderLabel() {
        if (tvProviderName != null) {
            AIProvider p = aiManager.getCurrent();
            tvProviderName.setText(p != null ? "🤖 " + p.getName() : "⚠️ No provider");
        }
    }

    private void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }

    private int dp(int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    private void safeRemove(View v) {
        if (v != null && v.isAttachedToWindow()) {
            try { wm.removeView(v); } catch (Exception ignored) {}
        }
    }
}
