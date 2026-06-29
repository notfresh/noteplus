package person.notfresh.noteplus.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 搜索管理器（单例）
 * 统一管理索引构建和搜索服务
 */
public class SearchManager {
    private static final String TAG = "SearchManager";
    private static volatile SearchManager instance;

    private final Context context;
    private final NoteDbHelper dbHelper;
    private NoteIndexer noteIndexer;
    private SearchService searchService;
    private final Handler mainHandler;
    private volatile boolean isIndexing = false;
    private final ExecutorService executorService;

    private SearchManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = NoteDbHelper.getInstance(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newCachedThreadPool();
        initServices();
    }

    public static SearchManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SearchManager.class) {
                if (instance == null) {
                    instance = new SearchManager(context);
                }
            }
        }
        return instance;
    }

    private void initServices() {
        noteIndexer = new NoteIndexer(context, dbHelper);
        searchService = new SearchService(context, dbHelper);
        Log.i(TAG, "SearchManager 初始化完成");
    }

    /**
     * 索引单条笔记
     * @param noteId 笔记ID
     * @param content 笔记内容
     * @param timestamp 时间戳
     */
    public void indexNote(long noteId, String content, long timestamp) {
        executorService.execute(() -> {
            boolean success = noteIndexer.indexNote(noteId, content, timestamp);
            if (!success) {
                Log.w(TAG, "笔记 " + noteId + " 索引失败");
            }
        });
    }

    /**
     * 删除笔记索引
     * @param noteId 笔记ID
     */
    public void deleteNoteIndex(long noteId) {
        executorService.execute(() -> {
            noteIndexer.deleteNoteIndex(noteId);
        });
    }

    /**
     * 搜索笔记
     * @param query 搜索词
     * @param callback 搜索结果回调（在主线程）
     */
    public void search(String query, SearchCallback callback) {
        if (callback == null) {
            return;
        }
        executorService.execute(() -> {
            java.util.List<SearchResult> results = searchService.search(query);
            mainHandler.post(() -> callback.onSearchResult(results));
        });
    }

    /**
     * 批量索引未索引的笔记（后台执行）
     * @param callback 进度回调（current, total）
     */
    public void indexUnindexedNotes(java.util.function.BiConsumer<Integer, Integer> callback) {
        if (isIndexing) {
            Log.w(TAG, "索引任务已在执行中");
            return;
        }
        isIndexing = true;
        executorService.execute(() -> {
            int count = noteIndexer.indexUnindexedNotes((current, total) -> {
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(current, total));
                }
            });
            isIndexing = false;
            Log.i(TAG, "批量索引完成，共 " + count + " 条");
        });
    }

    /**
     * 释放资源
     */
    public void release() {
        if (noteIndexer != null) {
            noteIndexer.close();
        }
        if (searchService != null) {
            searchService.close();
        }
        executorService.shutdown();
        instance = null;
    }

    /**
     * 搜索回调接口
     */
    public interface SearchCallback {
        void onSearchResult(java.util.List<SearchResult> results);
    }
}