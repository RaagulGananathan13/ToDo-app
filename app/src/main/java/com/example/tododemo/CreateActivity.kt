package com.example.tododemo

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.tododemo.databinding.ActivityCreateBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Calendar

class CreateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateBinding
    lateinit var create_title: EditText
    lateinit var create_priority: ChipGroup
    lateinit var tvScheduleDate: TextView
    lateinit var tvScheduleTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btn_save = findViewById<Button>(R.id.save_button)
        create_title = findViewById(R.id.create_title)
        create_priority = findViewById(R.id.create_priority)
        val tvTime = findViewById<TextView>(R.id.idTime)
        tvScheduleDate = findViewById<TextView>(R.id.tvSchedule)
        val tvCalendar = findViewById<CalendarView>(R.id.tvCalendar)
        tvScheduleTime = findViewById(R.id.tvClock)

        tvCalendar.visibility = View.GONE
        tvScheduleDate.setOnClickListener {
            tvCalendar.visibility = View.VISIBLE
        }
        tvScheduleTime.setOnClickListener {
            tvCalendar.visibility = View.VISIBLE
        }

        tvCalendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val date = "$dayOfMonth/${month + 1}/$year"
            tvScheduleDate.text = "Schedule: $date"
            tvCalendar.visibility = View.GONE

            val clock = Calendar.getInstance()
            val hour = clock.get(Calendar.HOUR_OF_DAY)
            val minute = clock.get(Calendar.MINUTE)

            val timePicker = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val formattedHour = if (hourOfDay < 10) "0$hourOfDay" else hourOfDay.toString()
                    val formattedMinute = if (minute < 10) "0$minute" else minute.toString()
                    tvScheduleTime.text = "$formattedHour:$formattedMinute"
                }, hour, minute, false
            )

            timePicker.setOnDismissListener {
                // Check if time has been selected
                if (tvScheduleTime.text.isNullOrEmpty()) {
                    // Handle if no time has been selected
                    // For example, you can display a message to the user or set a default time
                }
            }

            timePicker.show()
        }

        val sdf = SimpleDateFormat("'Date : 'dd-MM-yyyy ' Time : 'HH:mm ")
        val currentTime = sdf.format(java.util.Date())
        tvTime.text = "Created on: $currentTime"

        btn_save.setOnClickListener {
            val dbHandler = DatabaseHelper(this, null)

            val selectedChipId = create_priority.checkedChipId
            val selectedChip = findViewById<Chip>(selectedChipId)
            val priority = selectedChip?.text.toString() ?: ""

            val sharedPreferences = getSharedPreferences("tododemo", Context.MODE_PRIVATE)
            val userId = sharedPreferences.getInt("userId", -1)

            val user = Task(
                1, create_title.text.toString(), priority,
                tvTime.text.toString(), tvScheduleDate.text.toString(), tvScheduleTime.text.toString(), userId
            )
            dbHandler.addTask(user)
            Toast.makeText(this, "Task created ", Toast.LENGTH_SHORT).show()
            create_title.text.clear()
            priority
            create_title.requestFocus()

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        binding.notify.setOnClickListener { scheduleNotification() }
    }

    private fun scheduleNotification() {
        val selectedChipId = create_priority.checkedChipId
        val selectedChip = findViewById<Chip>(selectedChipId)

        val title = create_title.text.toString()
        val message = selectedChip?.text.toString() ?: ""

        val extras = PersistableBundle()
        extras.putString(NOTIFICATION_TITLE, title)
        extras.putString(NOTIFICATION_MESSAGE, message)

        val scheduledTimeMillis = calculateScheduledTimeMillis()
        val currentTimeMillis = System.currentTimeMillis()
        val delayMillis = scheduledTimeMillis - currentTimeMillis

        val jobScheduler = applicationContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(this, MyJobScheduler::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(delayMillis)
            .setExtras(extras)
            .build()

        jobScheduler.schedule(jobInfo)

        AlertDialog.Builder(this)
            .setTitle("ToDo")
            .setMessage("You will be notified on scheduled time.")
            .setPositiveButton("Okay") { _, _ -> }
            .show()
    }

    @SuppressLint("SimpleDateFormat")
    private fun calculateScheduledTimeMillis(): Long {
        val timeParts = tvScheduleTime.text.toString().split(":")
        val hour = timeParts[0].toIntOrNull() ?: 0
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val selectedDateMillis = binding.tvCalendar.date

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDateMillis

        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        calendar.set(year, month, day, hour, minute)
        return calendar.timeInMillis
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = "ToDo"
        val description = "Priority"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANEL_ID, name, importance)
        channel.description = description

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
