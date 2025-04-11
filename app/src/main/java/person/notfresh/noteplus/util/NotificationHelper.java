package person.notfresh.noteplus.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import person.notfresh.noteplus.MainActivity;
import person.notfresh.noteplus.R;

public class NotificationHelper {
    private static final String CHANNEL_ID = "noteplus_channel";
    private static final String CHANNEL_NAME = "NotePlus通知";
    private static final String CHANNEL_DESC = "显示提醒和通知";
    
    private final Context context;
    
    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannel();
    }
    
    /**
     * 创建通知渠道(Android 8.0+需要)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            
            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * 显示一条普通通知
     */
    public void showNotification(String title, String content, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // 处理缺少通知权限的情况
            e.printStackTrace();
        }
    }
    
    /**
     * 显示带有更多内容的通知
     */
    public void showExpandableNotification(String title, String content, 
                                          String expandedText, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(expandedText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // 处理缺少通知权限的情况
            e.printStackTrace();
        }
    }
    
    /**
     * 取消特定ID的通知
     */
    public void cancelNotification(int notificationId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(notificationId);
    }
    
    /**
     * 取消所有通知
     */
    public void cancelAllNotifications() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }
} 