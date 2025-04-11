package person.notfresh.noteplus.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import person.notfresh.noteplus.receiver.ReminderReceiver;
import person.notfresh.noteplus.service.ReminderService;
import person.notfresh.noteplus.work.ReminderWorker;

public class ReminderScheduler {
    
    private static final String PREF_NAME = "reminder_prefs";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final long REMINDER_INTERVAL = 10 * 60 * 1000; // 10分钟
    
    /**
     * 开启定时提醒
     */
    public static void startReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, true).apply();
        
        // 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
            // 提示用户需要开启权限
            Toast.makeText(context, "请在设置中允许应用设置精确闹钟以启用提醒功能", Toast.LENGTH_LONG).show();
            // 引导用户到设置界面
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                // 如果无法打开特定页面，尝试打开应用详情页
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return;
        }
        
        // 同时也使用WorkManager作为后备方案
        ReminderWorker.scheduleReminder(context);
        
        // 启动前台服务
        startReminderService(context);
        
        scheduleNextReminder(context);
    }
    
    /**
     * 关闭定时提醒
     */
    public static void stopReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, false).apply();
        
        // 同时取消WorkManager调度
        ReminderWorker.cancelReminder(context);
        
        // 停止前台服务
        stopReminderService(context);
        
        // 取消闹钟
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
        
        // 取消已有通知
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.cancelNotification(1001);
    }
    
    /**
     * 检查提醒是否已启用
     */
    public static boolean isReminderEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_REMINDER_ENABLED, false);
    }
    
    /**
     * 检查是否有设置精确闹钟的权限(Android 12+需要)
     */
    public static boolean hasExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager.canScheduleExactAlarms();
        }
        return true; // 旧版本Android不需要特别权限
    }
    
    /**
     * 调度下一次提醒
     */
    public static void scheduleNextReminder(Context context) {
        if (!isReminderEnabled(context)) {
            return;
        }
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        long triggerTime = SystemClock.elapsedRealtime() + REMINDER_INTERVAL;
        
        try {
            // 尝试设置精确闹钟
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
                // 如果没有精确闹钟权限，使用不精确的闹钟（可能会延迟）
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            }
        } catch (SecurityException e) {
            // 捕获权限异常，降级使用不精确闹钟
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
        }
    }

    /**
     * 启动前台服务
     */
    public static void startReminderService(Context context) {
        Intent serviceIntent = new Intent(context, ReminderService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    /**
     * 停止前台服务
     */
    public static void stopReminderService(Context context) {
        Intent serviceIntent = new Intent(context, ReminderService.class);
        context.stopService(serviceIntent);
    }

    /**
     * 确保提醒功能处于活动状态
     */
    public static void ensureReminderActive(Context context) {
        // 重新调度下一次提醒，防止系统清除了之前的闹钟
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(context, ReminderReceiver.class), 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        
        // 如果找不到待定的Intent，说明闹钟可能被取消了，需要重新设置
        if (pendingIntent == null) {
            scheduleNextReminder(context);
        }
    }
} 