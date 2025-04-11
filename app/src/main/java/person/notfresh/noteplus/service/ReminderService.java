package person.notfresh.noteplus.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import person.notfresh.noteplus.MainActivity;
import person.notfresh.noteplus.R;
import person.notfresh.noteplus.util.ReminderScheduler;

public class ReminderService extends Service {
    private static final String CHANNEL_ID = "reminder_service_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final long CHECK_INTERVAL = 60 * 1000; // 每分钟检查一次定时提醒状态

    private Thread backgroundThread;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 构建前台服务通知
        Notification notification = buildNotification("记录提醒服务正在运行", "开启后每隔10分钟提醒您记录活动");
        
        // 在Android 14及以上明确指定服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // 开始后台任务
        startBackgroundTask();

        // 如果服务被系统杀死，重启服务
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "记录提醒服务",
                    NotificationManager.IMPORTANCE_LOW); // 低重要性减少视觉干扰
            channel.setDescription("用于保持记录提醒服务在后台运行");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 构建前台服务通知
     */
    private Notification buildNotification(String title, String content) {
        // 创建打开应用的Intent
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // 构建通知
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * 开始后台任务，定期检查并维持提醒功能
     */
    private void startBackgroundTask() {
        if (isRunning) return;

        isRunning = true;
        backgroundThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // 检查并确保下一次提醒设置
                    if (ReminderScheduler.isReminderEnabled(this)) {
                        ReminderScheduler.ensureReminderActive(this);
                    }
                    
                    // 如果用户关闭了提醒，停止服务
                    if (!ReminderScheduler.isReminderEnabled(this)) {
                        stopSelf();
                        break;
                    }
                    
                    // 等待一段时间
                    Thread.sleep(CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    isRunning = false;
                    break;
                }
            }
        });
        
        backgroundThread.start();
    }

    @Override
    public void onDestroy() {
        // 停止后台任务
        isRunning = false;
        if (backgroundThread != null) {
            backgroundThread.interrupt();
        }
        
        super.onDestroy();
    }
} 