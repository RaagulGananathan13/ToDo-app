package com.example.tododemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), TaskAdapter.ItemClickListener, CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    lateinit var fibAdd: View
    lateinit var recyclerView: RecyclerView
    var taskIds: ArrayList<Int> = ArrayList()
    var taskNames: ArrayList<String> = ArrayList()
    var taskPriorities: ArrayList<String> = ArrayList()
    var taskTime: ArrayList<String> = ArrayList()
    var taskScheduleDate: ArrayList<String> = ArrayList()
    var taskScheduleTime: ArrayList<String> = ArrayList()

    lateinit var sharedPreferences: SharedPreferences
    var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        job = Job()

        sharedPreferences = getSharedPreferences("tododemo", Context.MODE_PRIVATE)
        userId = sharedPreferences.getInt("userId", -1)

        recyclerView = findViewById(R.id.recycler_view)
        fibAdd = findViewById(R.id.add)

        val dbHandler = DatabaseHelper(this, null)

        // Fetching tasks with Coroutine
        launch {
            fetchTasks(dbHandler)
        }

        // Fetching full name
        launch {
            fetchFullName(dbHandler)
        }

        fibAdd.setOnClickListener {
            val intent = Intent(this, CreateActivity::class.java)
            startActivity(intent)
            finish()
        }

        val adapter = TaskAdapter(taskNames, taskPriorities, taskTime, taskScheduleDate, taskScheduleTime,
            onItemClick = { position ->
                onItemClick(position)
            },
            onItemLongClick = { position ->
                onItemLongClick(position)
            })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        registerForContextMenu(recyclerView)
    }

    // Suspending function for fetching tasks
    private suspend fun fetchTasks(dbHandler: DatabaseHelper) = withContext(Dispatchers.IO) {
        val cursor = dbHandler.getTask(userId)

        if (cursor != null && cursor.moveToFirst()) {
            do {
                taskIds.add(cursor.getInt(cursor.getColumnIndex(DatabaseHelper.TASK_ID)))
                taskNames.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.TASK_TITLE)))
                taskPriorities.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.TASK_PRIORITY)))
                taskTime.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.TASK_TIME)))
                taskScheduleDate.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.TASK_SCHEDULE_DATE)))
                taskScheduleTime.add(cursor.getString(cursor.getColumnIndex(DatabaseHelper.TASK_SCHEDULE_TIME)))
            } while (cursor.moveToNext())
            cursor.close()
        }

        // Update RecyclerView on the main thread
        withContext(Dispatchers.Main) {
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }

    // Suspending function for fetching full name
    private suspend fun fetchFullName(dbHandler: DatabaseHelper) = withContext(Dispatchers.IO) {
        val cursor = dbHandler.getName(userId)
        if (cursor != null && cursor.moveToFirst()) {
            val fName = cursor.getString(cursor.getColumnIndex(DatabaseHelper.FIRST_NAME))
            val lName = cursor.getString(cursor.getColumnIndex(DatabaseHelper.LAST_NAME))

            withContext(Dispatchers.Main) {
                val fullName = findViewById<TextView>(R.id.fullName)
                fullName.text = "$fName $lName"
            }

            cursor.close()
        }
    }

    override fun onItemClick(position: Int) {
        val intent = Intent(this, UpdateActivity::class.java)
        intent.putExtra("id", taskIds[position])
        intent.putExtra("task", taskNames[position])
        intent.putExtra("priority", taskPriorities[position])
        intent.putExtra("time", taskTime[position])
        intent.putExtra("scheduleDate", taskScheduleDate[position])
        intent.putExtra("scheduleTime", taskScheduleTime[position])
        startActivity(intent)
        finish()
    }

    override fun onItemLongClick(position: Int) {
        val taskId = taskIds[position]

        val showMenu = androidx.appcompat.widget.PopupMenu(this, recyclerView.getChildAt(position))
        showMenu.inflate(R.menu.contxt_menu)
        showMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.itemDelete -> {
                    // Deleting task with Coroutine
                    launch {
                        deleteTask(taskId, position)
                    }
                    true
                }
                R.id.itemInfo -> {
                    val taskTime = taskTime[position]
                    Toast.makeText(this, taskTime, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        showMenu.show()
    }

    // Suspending function for deleting task
    private suspend fun deleteTask(taskId: Int, position: Int) = withContext(Dispatchers.IO) {
        val dbHandler = DatabaseHelper(this@MainActivity, null)
        dbHandler.deleteTask(taskId)

        // Update the task lists and notify RecyclerView on main thread
        withContext(Dispatchers.Main) {
            taskIds.removeAt(position)
            taskNames.removeAt(position)
            taskPriorities.removeAt(position)
            taskTime.removeAt(position)
            taskScheduleDate.removeAt(position)
            taskScheduleTime.removeAt(position)

            recyclerView.adapter?.notifyItemRemoved(position)
            Toast.makeText(this@MainActivity, "Task deleted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item3 -> Toast.makeText(this, "Please wait", Toast.LENGTH_SHORT).show()
            R.id.item4 -> Toast.makeText(this, "Please wait", Toast.LENGTH_SHORT).show()
            R.id.itemLogOut -> {
                sharedPreferences.edit().remove("userId").apply()

                val intent = Intent(this, loginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()  // Cancel any active coroutines
    }
}
