package com.ibo.ilachatirlatici

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var okunacakMetin: String = ""

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("IlacPrefs", Context.MODE_PRIVATE)
        val ilac1 = prefs.getString("ilac_1", "birinci ilacınızı") ?: "birinci ilacınızı"
        val ilac2 = prefs.getString("ilac_2", "ikinci ilacınızı") ?: "ikinci ilacınızı"
        val sira = prefs.getInt("siradaki_ilac", 1) // 1 ise 1. ilaç, 2 ise 2. ilaç
        val saat = prefs.getInt("saat", 8)
        val dakika = prefs.getInt("dakika", 0)

        // Bugün okunacak ilacı seç ve yarın için sırayı diğerine çevir
        val bugunkuIlac: String
        val yarinkiSira: Int

        if (sira == 1) {
            bugunkuIlac = ilac1
            yarinkiSira = 2
        } else {
            bugunkuIlac = ilac2
            yarinkiSira = 1
        }

        // Yarınki sırayı telefon hafızasına kaydet
        prefs.edit().putInt("siradaki_ilac", yarinkiSira).apply()

        okunacakMetin = "Günaydın. Bugün sıra $bugunkuIlac ilacınızda. Lütfen $bugunkuIlac almayı unutmayın."

        // 1. Ekranda bildirim göster
        bildirimGoster(context, bugunkuIlac)

        // 2. Çevrimdışı Türkçe sesli okumayı başlat
        tts = TextToSpeech(context.applicationContext, this)

        // 3. Ertesi gün aynı saat için alarmı otomatik yeniden kur
        ertesiGuneKur(context, saat, dakika)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val sonuc = tts?.setLanguage(Locale("tr", "TR"))
            if (sonuc != TextToSpeech.LANG_MISSING_DATA && sonuc != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setSpeechRate(0.9f)
                tts?.speak(okunacakMetin, TextToSpeech.QUEUE_FLUSH, null, "ILAC_UYARI")
            }
        }
    }

    private fun bildirimGoster(context: Context, ilacAdi: String) {
        val kanalId = "ilac_hatirlatici_kanal"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(kanalId, "İlaç Hatırlatıcı", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(kanal)
        }

        val bildirim = NotificationCompat.Builder(context, kanalId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Bugünkü İlaç Vakti!")
            .setContentText("Bugün alınacak ilaç: $ilacAdi")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, bildirim)
    }

    private fun ertesiGuneKur(context: Context, saat: Int, dakika: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takvim = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, saat)
            set(Calendar.MINUTE, dakika)
            set(Calendar.SECOND, 0)
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, takvim.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}