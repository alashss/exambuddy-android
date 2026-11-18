# ExamBuddy — Integrasi ke App Ujian

ExamBuddy bekerja sebagai overlay. App ujian kamu cukup kirim **broadcast Intent** dan ExamBuddy otomatis jawab.

## Kirim Soal ke ExamBuddy

```java
// Dari activity/fragment app ujian kamu:
Intent intent = new Intent("com.exambuddy.START");  // start service dulu
intent.setPackage("com.exambuddy");
startService(intent);  // atau startForegroundService

// Kirim soal
Intent q = new Intent();
q.setAction("com.exambuddy.QUESTION"); // atau Intent langsung ke BubbleService
q.setPackage("com.exambuddy");
q.putExtra("question", "Manakah yang bukan merupakan besaran pokok? A. Panjang B. Massa C. Volume D. Suhu");
q.putExtra("choices", "A. Panjang\nB. Massa\nC. Volume\nD. Suhu");
q.putExtra("question_number", 5);   // soal ke-5
q.putExtra("total_questions", 40);  // total 40 soal
startService(q);
```

## Atau pakai BroadcastReceiver (lebih mudah)

Daftarkan receiver di AndroidManifest app ujian kamu:
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

Kirim soal:
```java
Intent i = new Intent("com.exambuddy.QUESTION");
i.putExtra("question", soalText);
sendBroadcast(i);
```

## Terima navigasi prev/next dari ExamBuddy

```java
// Di onCreate activity ujian:
BroadcastReceiver navReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if ("com.exambuddy.NAV_PREV".equals(intent.getAction())) {
            prevQuestion(); // panggil method soal sebelumnya
        } else if ("com.exambuddy.NAV_NEXT".equals(intent.getAction())) {
            nextQuestion(); // panggil method soal berikutnya
        }
    }
};

IntentFilter f = new IntentFilter();
f.addAction("com.exambuddy.NAV_PREV");
f.addAction("com.exambuddy.NAV_NEXT");
registerReceiver(navReceiver, f);
```

## Flow lengkap

```
App Ujian               ExamBuddy Bubble
     |                        |
     |-- START service -----→ |  bubble muncul
     |                        |
     |-- QUESTION (soal 1) →  |  AI auto-jawab
     |                        |
     |←-- NAV_NEXT -----------|  user tap Next di bubble
     |                        |
     |-- QUESTION (soal 2) →  |  AI jawab soal berikutnya
     |                        |
```
