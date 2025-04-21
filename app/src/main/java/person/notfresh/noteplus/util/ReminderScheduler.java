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
import person.notfresh.noteplus.work.ReminderWorker;

public class ReminderScheduler {
    // Add this constant definition
    private static final int REMINDER_REQUEST_CODE = 1001;
    
    private static final String PREF_NAME = "reminder_prefs";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_REMINDER_INTERVAL = "reminder_interval";
    private static final String KEY_NEXT_REMINDER_TIME = "next_reminder_time";
    private static final long DEFAULT_REMINDER_INTERVAL = 10 * 60 * 1000; // 默认10分钟
    private static final int MAX_MISSED_MINUTES = 5; // 最大错过分钟数
    
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
        
        // 使用WorkManager作为主要提醒机制
        ReminderWorker.scheduleReminder(context, getReminderInterval(context));
        
        // 设置AlarmManager作为备选
        scheduleNextReminder(context);
    }
    
    /**
     * 关闭定时提醒
     */
    public static void stopReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, false).apply();
        
        // 取消WorkManager调度
        ReminderWorker.cancelReminder(context);
        
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
     * 获取提醒时间间隔（毫秒）
     */
    public static long getReminderInterval(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_REMINDER_INTERVAL, DEFAULT_REMINDER_INTERVAL);
    }
    
    /**
     * 设置提醒时间间隔（毫秒）
     */
    public static void setReminderInterval(Context context, long intervalMillis) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_REMINDER_INTERVAL, intervalMillis).apply();
        
        // 如果提醒已启用，更新调度
        if (isReminderEnabled(context)) {
            ReminderWorker.scheduleReminder(context, intervalMillis);
            scheduleNextReminder(context);
        }
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
        // 先取消现有提醒
        cancelAllReminders(context);
        
        if (!isReminderEnabled(context)) {
            return;
        }
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("REMINDER_ALARM_" + System.currentTimeMillis()); // 添加唯一动作标识
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        long intervalMillis = getReminderInterval(context);
        long triggerTime = SystemClock.elapsedRealtime() + intervalMillis;
        
        try {
            // 尝试设置精确闹钟，最优先使用最可靠的API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
                // 没有精确闹钟权限时
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 使用最可靠的API，即使设备处于低功耗模式也能唤醒
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                        triggerTime, pendingIntent);
            }
            
            // 记录下一次闹钟的时间
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_NEXT_REMINDER_TIME, System.currentTimeMillis() + intervalMillis).apply();
            
        } catch (SecurityException e) {
            // 捕获权限异常，降级使用不精确闹钟
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
        }
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

    /**
     * 检查是否错过了提醒
     */
    public static boolean checkMissedReminder(Context context) {
        if (!isReminderEnabled(context)) {
            return false;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long nextReminderTime = prefs.getLong(KEY_NEXT_REMINDER_TIME, 0);
        
        // 如果预期的下一次提醒时间已过期，且超过了5分钟
        if (nextReminderTime > 0 && System.currentTimeMillis() > nextReminderTime + (MAX_MISSED_MINUTES * 60 * 1000)) {
            // 立即触发一次提醒，然后重新设置提醒计划
            NotificationHelper notificationHelper = new NotificationHelper(context);
            notificationHelper.showNotification(
                    "错过的提醒",
                    "您错过了之前的提醒，请记录一下当前活动",
                    1001
            );
            
            // 重新设置提醒
            scheduleNextReminder(context);
            return true;
        }
        
        return false;
    }

    public static void cancelAllReminders(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }
}