package person.notfresh.noteplus.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import person.notfresh.noteplus.util.NotificationHelper;
import person.notfresh.noteplus.util.ReminderScheduler;

public class ReminderWorker extends Worker {
    private static final String WORK_NAME = "reminder_work";
    
    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        
        // 检查是否启用提醒
        if (!ReminderScheduler.isReminderEnabled(context)) {
            return Result.success();
        }
        
        // 发送提醒通知
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.showNotification(
                "记录提醒",
                "该记录一下您现在的活动了",
                1001
        );
        
        // 确保AlarmManager提醒被设置
        ReminderScheduler.ensureReminderActive(context);
        
        return Result.success();
    }
    
    // 安排定期工作
    public static void scheduleReminder(Context context) {
        PeriodicWorkRequest reminderRequest = 
                new PeriodicWorkRequest.Builder(ReminderWorker.class, 15, TimeUnit.MINUTES)
                        .setInitialDelay(10, TimeUnit.MINUTES)
                        .build();
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderRequest
        );
    }
    
    // 取消定期工作
    public static void cancelReminder(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
} 