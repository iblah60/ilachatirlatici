package com.ibo.ilachatirlatici

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        izinleriKontrolEt()
        testTts = TextToSpeech(this, this)

        val etIlac1 = findViewById<EditText>(R.id.etIlac1)
        val etIlac2 = findViewById<EditText>(R.id.etIlac2)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val btnKaydet = findViewById<Button>(R.id.btnKaydet)
        val btnTestSes = findViewById<Button>(R.id.btnTestSes)
        val tvDurum = findViewById<TextView>(R.id.tvDurum)

        timePicker.setIs24HourView(true)

        // Kayıtlı bilgileri telefondan oku
        val prefs = getSharedPreferences("IlacPrefs", Context.MODE_PRIVATE)
        val kayitliIlac1 = prefs.getString("ilac_1", "")
        val kayitliIlac2 = prefs.getString("ilac_2", "")
        val siradaki = prefs.getInt("siradaki_ilac", 1)
        val kayitliSaat = prefs.getInt("saat", -1)
        val kayitliDakika = prefs.getInt("dakika", -1)

        if (!kayitliIlac1.isNullOrEmpty() && !kayitliIlac2.isNullOrEmpty() && kayitliSaat != -1) {
            etIlac1.setText(kayitliIlac1)
            etIlac2.setText(kayitliIlac2)
            timePicker.hour = kayitliSaat
            timePicker.minute = kayitliDakika
            val siradakiIsim = if (siradaki == 1) kayitliIlac1 else kayitliIlac2
            tvDurum.text = String.format(
                "Aktif Saat: %02d:%02d\nSıradaki İlaç: %s\n(1. Gün: %s | 2. Gün: %s)",
                kayitliSaat, kayitliDakika, siradakiIsim, kayitliIlac1, kayitliIlac2
            )
        }

        btnKaydet.setOnClickListener {
            val ilac1 = etIlac1.text.toString().trim()
            val ilac2 = etIlac2.text.toString().trim()

            if (ilac1.isEmpty() || ilac2.isEmpty()) {
                Toast.makeText(this, "Lütfen her iki ilacın da adını girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val saat = timePicker.hour
            val dakika = timePicker.minute

            // Kaydedildiğinde ilk alarm 1. ilaç ile başlar
            prefs.edit().apply {
                putString("ilac_1", ilac1)
                putString("ilac_2", ilac2)
                putInt("siradaki_ilac", 1)
                putInt("saat", saat)
                putInt("dakika", dakika)
                apply()
            }

            alarmiKur(saat, dakika)
            tvDurum.text = String.format(
                "Aktif Saat: %02d:%02d\nİlk Sıra: %s\n(1. Gün: %s | 2. Gün: %s)",
                saat, dakika, ilac1, ilac1, ilac2
            )
            Toast.makeText(this, "Gün aşırı sesli hatırlatıcı kuruldu!", Toast.LENGTH_LONG).show()
        }

        btnTestSes.setOnClickListener {
            val ilac1 = etIlac1.text.toString().ifEmpty { "birinci ilacınız" }
            val ilac2 = etIlac2.text.toString().ifEmpty { "ikinci ilacınız" }
            val metin = "Günaydın. Bugün sıra $ilac1 ilacınızda. Yarın ise $ilac2 ilacınızı hatırlatacağım."
            testTts?.speak(metin, TextToSpeech.QUEUE_FLUSH, null, "TEST")
        }
    }

    private fun alarmiKur(saat: Int, dakika: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takvim = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, saat)
            set(Calendar.MINUTE, dakika)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, takvim.timeInMillis, pendingIntent)
    }

    private fun izinleriKontrolEt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            testTts?.setLanguage(Locale("tr", "TR"))
            testTts?.setSpeechRate(0.9f)
        }
    }

    override fun onDestroy() {
        testTts?.stop()
        testTts?.shutdown()
        super.onDestroy()
    }
}