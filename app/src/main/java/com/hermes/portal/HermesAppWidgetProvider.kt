package com.hermes.portal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log

class HermesAppWidgetProvider : AppWidgetProvider() {
    private val TAG = "HermesAppWidget"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        
        // 更新每个widget
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // 当第一个widget被添加时调用
        Log.d(TAG, "onEnabled called")
    }

    override fun onDisabled(context: Context) {
        // 当最后一个widget被删除时调用
        Log.d(TAG, "onDisabled called")
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "Updating widget: $appWidgetId")
        
        // 创建RemoteViews对象
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        
        // 设置卸载按钮的点击事件
        val uninstallIntent = createUninstallIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            uninstallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.uninstall_button, pendingIntent)
        
        // 更新widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /**
     * 创建卸载应用的Intent
     */
    private fun createUninstallIntent(context: Context): Intent {
        val packageName = context.packageName
        val uninstallIntent = Intent(Intent.ACTION_DELETE)
        uninstallIntent.data = android.net.Uri.parse("package:$packageName")
        uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return uninstallIntent
    }
}
