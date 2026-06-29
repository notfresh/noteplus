package person.notfresh.noteplus.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.search.NoteIndexer;
import person.notfresh.noteplus.search.SearchManager;

/**
 * 后台索引初始化任务
 * 使用 WorkManager 在后台构建未索引笔记的索引
 */
public class SearchIndexInitWorker extends Worker {
    private static final String TAG = "SearchIndexInitWorker";
    public static final String WORK_NAME = "search_index_init";
    private static final String PREF_NAME = "search_index_prefs";
    private static final String KEY_INDEX_BUILT = "search_index_built";

    public SearchIndexInitWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "开始后台索引构建任务");
        NoteIndexer indexer = null;
        try {
            indexer = new NoteIndexer(getApplicationContext(), ProjectContextManager.getInstance(getApplicationContext()));

            Log.i(TAG, "发现未索引笔记，开始构建...");

            // 执行批量索引（跨所有项目）
            indexer.indexUnindexedNotes((current, t) -> {
                Log.d(TAG, "索引进度: " + current + "/" + t);
            });

            // 标记索引已构建
            markIndexBuilt();

            Log.i(TAG, "后台索引构建任务完成");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "后台索引构建任务失败", e);
            return Result.retry();
        } finally {
            if (indexer != null) {
                indexer.close();
            }
        }
    }

    /**
     * 标记索引已构建完成
     */
    private void markIndexBuilt() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_INDEX_BUILT, true)
                .putInt(NoteDbHelper.PREF_SEARCH_INDEX_VERSION, NoteDbHelper.SEARCH_INDEX_VERSION)
                .apply();
        Log.i(TAG, "索引状态已更新: built=true, version=" + NoteDbHelper.SEARCH_INDEX_VERSION);
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