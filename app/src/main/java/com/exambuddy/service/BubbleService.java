package com.exambuddy.service;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import com.exambuddy.R;
import com.exambuddy.ai.AIManager;
import com.exambuddy.ui.BubbleView;

public class BubbleService extends Service {

    public static final String ACTION_START   = "com.exambuddy.START";
    public static final String ACTION_STOP    = "com.exambuddy.STOP";
    public static final String ACTION_QUESTION = "com.exambuddy.QUESTION";
    public static final String EXTRA_QUESTION  = "question";
    public static final String EXTRA_CHOICES   = "choices";
    public static final String EXTRA_QN        = "question_number";
    public static final String EXTRA_TOTAL     = "total_questions";

    private static final String CHANNEL_ID = "exambuddy_overlay";
    private static final int    NOTIF_ID   = 1001;

    private WindowManager windowManager;
    private BubbleView    bubbleView;
    private AIManager     aiManager;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        aiManager = new AIManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (action == null) action = ACTION_START;

        switch (action) {
            case ACTION_START:
                startForeground(NOTIF_ID, buildNotification());
                if (bubbleView == null) showBubble();
                break;
            case ACTION_STOP:
                stopSelf();
                break;
            case ACTION_QUESTION:
                String question = intent.getStringExtra(EXTRA_QUESTION);
                String choices  = intent.getStringExtra(EXTRA_CHOICES);
                int    qn       = intent.getIntExtra(EXTRA_QN, 0);
                int    total    = intent.getIntExtra(EXTRA_TOTAL, 0);
                if (bubbleView != null && question != null) {
                    bubbleView.setQuestion(question, choices, qn, total);
                }
                break;
        }
        return START_STICKY;
    }

    private void showBubble() {
        bubbleView = new BubbleView(this, windowManager, aiManager);
        bubbleView.attach();
    }

    @Override
    public void onDestroy() {
        if (bubbleView != null) {
            bubbleView.detach();
            bubbleView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "ExamBuddy Overlay",
                NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Floating exam assistant");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, BubbleService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ExamBuddy aktif")
            .setContentText("AI siap membantu ujian kamu")
            .setSmallIcon(R.drawable.ic_brain)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", stopPI)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
}
