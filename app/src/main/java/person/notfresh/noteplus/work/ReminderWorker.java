package person.notfresh.noteplus.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.ExistingWorkPolicy;

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
        
        // 如果间隔小于15分钟，需要在任务完成后重新调度下一次任务
        long intervalMillis = ReminderScheduler.getReminderInterval(context);
        if (intervalMillis < 15 * 60 * 1000) {
            OneTimeWorkRequest nextRequest = 
                    new OneTimeWorkRequest.Builder(ReminderWorker.class)
                            .setInitialDelay(intervalMillis, TimeUnit.MILLISECONDS)
                            .build();
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    nextRequest
            );
        }
        
        return Result.success();
    }
    
    // 安排定期工作
    public static void scheduleReminder(Context context, long intervalMillis) {
        // 如果用户设置的是较短的间隔（小于15分钟）
        if (intervalMillis < 15 * 60 * 1000) {
            // 先立即安排一次
            OneTimeWorkRequest immediateRequest = 
                    new OneTimeWorkRequest.Builder(ReminderWorker.class)
                            .build(); // 立即执行
                            
            // 然后再安排一个延迟的任务
            OneTimeWorkRequest delayedRequest = 
                    new OneTimeWorkRequest.Builder(ReminderWorker.class)
                            .setInitialDelay(intervalMillis, TimeUnit.MILLISECONDS)
                            .build();
            
            // 先执行立即任务，然后执行延迟任务
            WorkManager.getInstance(context)
                    .beginWith(immediateRequest)
                    .then(delayedRequest)
                    .enqueue();
                    
            // 同时登记唯一任务以便后续取消
            WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    delayedRequest
            );
        } else {
            // 对于15分钟及以上的间隔，使用PeriodicWorkRequest
            long intervalMinutes = intervalMillis / (60 * 1000);
            PeriodicWorkRequest reminderRequest = 
                    new PeriodicWorkRequest.Builder(ReminderWorker.class, intervalMinutes, TimeUnit.MINUTES)
                            .setInitialDelay(intervalMillis, TimeUnit.MILLISECONDS)
                            .build();
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    reminderRequest
            );
        }
    }
    
    // 取消定期工作
    public static void cancelReminder(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
} 