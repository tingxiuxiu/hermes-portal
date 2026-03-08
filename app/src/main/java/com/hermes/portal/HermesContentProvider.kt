package com.hermes.portal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

class HermesContentProvider : ContentProvider() {
    companion object {
        const val TAG = "HermesContentProvider"
        // 定义 Content URI 的 Authority，建议使用 包名.provider
        const val AUTHORITY = "com.hermes.portal.provider"
    }
    
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
        when (uri.path) {
            "/start-service", "/start-service/" -> {
                Log.d(TAG, "Starting HermesService...")
                // 启动前台服务
                context?.let { ctx ->
                    Log.d(TAG, "Context obtained, starting service")
                    try {
                        val serviceIntent = android.content.Intent(ctx, HermesService::class.java)
                        Log.d(TAG, "Intent created: $serviceIntent")
                        ctx.startForegroundService(serviceIntent)
                        Log.d(TAG, "HermesService started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting HermesService: ${e.message}", e)
                    }
                } ?: run {
                    Log.e(TAG, "Context is null, cannot start service")
                }
            }
            "/stop-service", "/stop-service/" -> {
                Log.d(TAG, "Stopping HermesService...")
                // 停止前台服务
                context?.let { ctx ->
                    Log.d(TAG, "Context obtained, stopping service")
                    try {
                        val serviceIntent = android.content.Intent(ctx, HermesService::class.java)
                        Log.d(TAG, "Intent created: $serviceIntent")
                        ctx.stopService(serviceIntent)
                        Log.d(TAG, "HermesService stopped successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping HermesService: ${e.message}", e)
                    }
                } ?: run {
                    Log.e(TAG, "Context is null, cannot stop service")
                }
            }
            else -> {
                Log.d(TAG, "Unknown path: ${uri.path}, ignoring request")
            }
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
    
    override fun getType(uri: Uri): String {
        return "vnd.android.cursor.dir/vnd.hermes.portal"
    }
}
