package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.AppCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MyMoneyKeepWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.mymoneykeep.widget.ACTION_REFRESH"


        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MyMoneyKeepWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, MyMoneyKeepWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    private fun updateSingleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val transactions: List<TransactionEntity> = try {
                    db.transactionDao().getAllTransactions().first()
                } catch (e: Exception) {
                    emptyList()
                }

                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val todayStr = "$currentYear/$currentMonth/$currentDay"

                var todayExpense = 0.0
                var todayIncome = 0.0
                var monthExpense = 0.0
                var monthIncome = 0.0

                for (tx in transactions) {
                    val inc = tx.income ?: 0.0
                    val exp = tx.expense ?: 0.0

                    // 比對今日
                    val cleanDate = tx.date.trim().replace("-", "/")
                    val isToday = cleanDate == todayStr ||
                            cleanDate == String.format(Locale.US, "%d/%02d/%02d", currentYear, currentMonth, currentDay) ||
                            cleanDate == String.format(Locale.US, "%d/%d/%d", currentYear, currentMonth, currentDay)

                    if (isToday) {
                        todayExpense += exp
                        todayIncome += inc
                    }

                    // 比對本月
                    val isThisMonth = cleanDate.startsWith("$currentYear/$currentMonth/") ||
                            cleanDate.startsWith(String.format(Locale.US, "%d/%02d/", currentYear, currentMonth))

                    if (isThisMonth) {
                        monthExpense += exp
                        monthIncome += inc
                    }
                }

                val monthBalance = monthIncome - monthExpense
                val currency = AppCurrency.TWD

                val views = RemoteViews(context.packageName, R.layout.widget_mymoneykeep)
                views.setTextViewText(R.id.widget_today_expense, currency.format(todayExpense))
                views.setTextViewText(R.id.widget_today_income, currency.format(todayIncome))
                views.setTextViewText(R.id.widget_month_balance, currency.format(monthBalance))

                // 綁定 PendingIntent
                // 1. 點擊本體打開 App
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val mainPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_title, mainPendingIntent)



                // 4. 點擊「🔄 刷新」
                val refreshIntent = Intent(context, MyMoneyKeepWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    3,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
