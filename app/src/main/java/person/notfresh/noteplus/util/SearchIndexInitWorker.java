package person.notfresh.noteplus.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.search.NoteIndexer;
import person.notfresh.noteplus.search.SearchManager;

/**
 * 后台索引初始化任务
 * 使用 WorkManager 在后台构建未索引笔记的索引
 */
public class SearchIndexInitWorker extends Worker {
    private static final String TAG = "SearchIndexInitWorker";
    public static final String WORK_NAME = "search_index_init";

    public SearchIndexInitWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "开始后台索引构建任务");
        NoteIndexer indexer = null;
        android.database.Cursor cursor = null;
        try {
            indexer = new NoteIndexer(getApplicationContext(), NoteDbHelper.getInstance(getApplicationContext()));
            cursor = NoteDbHelper.getInstance(getApplicationContext()).getUnindexedNotes();
            int total = cursor.getCount();
            cursor.close();
            cursor = null;

            if (total == 0) {
                Log.i(TAG, "没有需要索引的笔记");
                return Result.success();
            }

            Log.i(TAG, "发现 " + total + " 条未索引笔记，开始构建...");

            // 执行批量索引
            indexer.indexUnindexedNotes((current, t) -> {
                Log.d(TAG, "索引进度: " + current + "/" + t);
            });

            Log.i(TAG, "后台索引构建任务完成");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "后台索引构建任务失败", e);
            return Result.retry();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (indexer != null) {
                indexer.close();
            }
        }
    }

    /**
     * 调度后台索引任务
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SearchIndexInitWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest);

        Log.i(TAG, "后台索引任务已调度");
    }

    /**
     * 取消后台索引任务
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "后台索引任务已取消");
    }
}