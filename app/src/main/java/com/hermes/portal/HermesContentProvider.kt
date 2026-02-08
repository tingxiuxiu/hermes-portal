package com.hermes.portal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

class HermesContentProvider : ContentProvider() {
    private val TAG = "HermesContentProvider"
    
    override fun onCreate(): Boolean {
        Log.d(TAG, "ContentProvider onCreate called")
        return true
    }
    
    override fun query(
        uri: Uri, 
        projection: Array<out String>?, 
        selection: String?, 
        selectionArgs: Array<out String>?, 
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "ContentProvider query called with uri: $uri")
        Log.d(TAG, "Uri path: ${uri.path}")
        Log.d(TAG, "Uri authority: ${uri.authority}")
        Log.d(TAG, "Context available: ${context != null}")
        
        // 检查是否是启动服务的请求
        if (uri.path == "/start-service" || uri.path == "/start-service/") {
            Log.d(TAG, "Starting StatusService...")
            // 启动前台服务
            context?.let {ctx ->
                Log.d(TAG, "Context obtained, starting service")
                try {
                    val serviceIntent = android.content.Intent(ctx, StatusService::class.java)
                    Log.d(TAG, "Intent created: $serviceIntent")
                    ctx.startForegroundService(serviceIntent)
                    Log.d(TAG, "StatusService started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting StatusService: ${e.message}", e)
                }
            } ?: run {
                Log.e(TAG, "Context is null, cannot start service")
            }
        } else if (uri.path == "/stop-service" || uri.path == "/stop-service/") {
            Log.d(TAG, "Stopping StatusService...")
            // 停止前台服务
            context?.let {ctx ->
                Log.d(TAG, "Context obtained, stopping service")
                try {
                    val serviceIntent = android.content.Intent(ctx, StatusService::class.java)
                    Log.d(TAG, "Intent created: $serviceIntent")
                    ctx.stopService(serviceIntent)
                    Log.d(TAG, "StatusService stopped successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping StatusService: ${e.message}", e)
                }
            } ?: run {
                Log.e(TAG, "Context is null, cannot stop service")
            }
        } else {
            Log.d(TAG, "Unknown path: ${uri.path}, ignoring request")
        }
        
        // 返回空Cursor，因为我们只是处理命令而不提供数据
        return null
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
    
    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.dir/vnd.hermes.portal"
    }
}