package person.notfresh.noteplus.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;

/**
 * 搜索管理器（单例）
 * 统一管理索引构建和搜索服务
 */
public class SearchManager {
    private static final String TAG = "SearchManager";
    private static volatile SearchManager instance;

    private final Context context;
    private NoteDbHelper dbHelper;
    private ProjectContextManager projectContextManager;
    private String projectName;
    private NoteIndexer noteIndexer;
    private SearchService searchService;
    private final Handler mainHandler;
    private volatile boolean isIndexing = false;
    private final ExecutorService executorService;

    private SearchManager(Context context, NoteDbHelper dbHelper, ProjectContextManager projectContextManager, String projectName) {
        this.context = context.getApplicationContext();
        this.dbHelper = dbHelper;
        this.projectContextManager = projectContextManager;
        this.projectName = projectName;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newCachedThreadPool();
        initServices();
    }

    public static SearchManager getInstance(Context context, NoteDbHelper dbHelper, ProjectContextManager projectContextManager, String projectName) {
        if (instance == null) {
            synchronized (SearchManager.class) {
                if (instance == null) {
                    instance = new SearchManager(context, dbHelper, projectContextManager, projectName);
                }
            }
        }
        return instance;
    }

    public static SearchManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SearchManager.class) {
                if (instance == null) {
                    instance = new SearchManager(context, NoteDbHelper.getInstance(context.getApplicationContext()), ProjectContextManager.getInstance(context.getApplicationContext()), "default");
                }
            }
        }
        return instance;
    }

    private synchronized void initServices() {
        if (noteIndexer != null) {
            noteIndexer.close();
        }
        if (searchService != null) {
            searchService.close();
        }
        noteIndexer = new NoteIndexer(context, projectContextManager);
        searchService = new SearchService(context, projectContextManager);
        Log.i(TAG, "SearchManager 初始化完成");
    }

    /**
     * 检查索引是否就绪（存在且版本匹配）
     * @return true 如果索引已就绪
     */
    public boolean isIndexReady() {
        // 检查索引目录是否存在
        File indexDir = new File(context.getFilesDir(), NoteIndexer.INDEX_DIR);
        if (!indexDir.exists() || !indexDir.isDirectory()) {
            return false;
        }

        // 检查版本号
        SharedPreferences prefs = context.getSharedPreferences("search_index_prefs", Context.MODE_PRIVATE);
        int savedVersion = prefs.getInt(NoteDbHelper.PREF_SEARCH_INDEX_VERSION, 0);
        return savedVersion == NoteDbHelper.SEARCH_INDEX_VERSION;
    }

    /**
     * 获取索引版本号
     */
    public int getIndexVersion() {
        SharedPreferences prefs = context.getSharedPreferences("search_index_prefs", Context.MODE_PRIVATE);
        return prefs.getInt(NoteDbHelper.PREF_SEARCH_INDEX_VERSION, 0);
    }

    /**
     * 保存索引版本号
     */
    private void saveIndexVersion() {
        SharedPreferences prefs = context.getSharedPreferences("search_index_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt(NoteDbHelper.PREF_SEARCH_INDEX_VERSION, NoteDbHelper.SEARCH_INDEX_VERSION).apply();
    }

    /**
     * 检查并修复索引版本（如果需要则重建索引）
     */
    public void checkAndFixIndexVersion() {
        if (!isIndexReady()) {
            Log.i(TAG, "索引版本不匹配或索引不存在，需要重建");
            rebuildIndex();
        }
    }

    /**
     * 重建索引（删除旧索引并触发重建）
     */
    public void rebuildIndex() {
        executorService.execute(() -> {
            // 删除旧索引
            File indexDir = new File(context.getFilesDir(), NoteIndexer.INDEX_DIR);
            if (indexDir.exists()) {
                File[] files = indexDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            Log.i(TAG, "旧索引已删除，触发重建");

            // 触发未索引笔记的重建
            indexUnindexedNotes(null);

            // 保存新版本号
            saveIndexVersion();
        });
    }

    /**
     * 在项目切换后重绑数据库并重建索引/搜索服务
     */
    public synchronized void rebindDbHelper(NoteDbHelper newDbHelper, String newProjectName) {
        if (newDbHelper == null || this.dbHelper == newDbHelper) {
            return;
        }
        this.dbHelper = newDbHelper;
        this.projectName = newProjectName;
        initServices();
        Log.i(TAG, "SearchManager 已切换到新的项目数据库: " + newProjectName);
    }

    /**
     * 索引单条笔记
     * @param noteId 笔记ID
     * @param content 笔记内容
     * @param timestamp 时间戳
     * @param projectName 项目名称
     */
    public void indexNote(long noteId, String content, long timestamp, String projectName) {
        executorService.execute(() -> {
            boolean success = noteIndexer.indexNote(noteId, content, timestamp, projectName);
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