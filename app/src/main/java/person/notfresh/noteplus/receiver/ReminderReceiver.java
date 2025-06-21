package person.notfresh.noteplus.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import person.notfresh.noteplus.util.NotificationHelper;
import person.notfresh.noteplus.util.ReminderScheduler;


public class ReminderReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        // 处理设备重启事件
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // 如果启用了提醒，重新启动提醒服务
            if (ReminderScheduler.isReminderEnabled(context)) {
                ReminderScheduler.startReminder(context);
            }
            return;
        }
        
        // 处理正常的提醒事件
        NotificationHelper notificationHelper = new NotificationHelper(context);
        
        // 显示提醒通知
        notificationHelper.showNotification(
                "记录提醒",
                "该记录一下您现在的活动了",
                1001 // 使用固定ID便于更新或取消
        );
        
        // 立即设置下一次的提醒（解决可能被系统延迟的问题）
        ReminderScheduler.scheduleNextReminder(context);
        
//        // 额外启动一个即时的WorkManager任务作为双保险
//        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ReminderWorker.class)
//                .build(); // 立即执行
//        WorkManager.getInstance(context).enqueue(workRequest);
    }
} 