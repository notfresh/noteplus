package person.notfresh.noteplus.manager;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.Button;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import person.notfresh.noteplus.ui.ImagePreviewDialog;
import java.util.Locale;
import java.util.Set;

import androidx.appcompat.app.AlertDialog;

import person.notfresh.noteplus.R;
import person.notfresh.noteplus.core.TimeRangeFilter;
import person.notfresh.noteplus.core.model.Comment;
import person.notfresh.noteplus.core.model.Note;
import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.manager.INoteListCallback;
import person.notfresh.noteplus.util.DisplayUtil;
import person.notfresh.noteplus.util.StringUtil;
import person.notfresh.noteplus.core.model.AudioAttachment;
import person.notfresh.noteplus.search.SearchManager;

/**
 * 笔记列表管理器
 * 负责管理笔记列表的显示、数据操作和UI交互
 * 从 MainActivity 中抽离显示区相关逻辑
 */
public class NoteListManager {
    // 依赖注入
    private ListView listView;
    private INoteListCallback callback;
    
    // 内部状态（从MainActivity移过来）
    private NoteCursorWrapper noteCursorWrapper;
    private NoteListAdapter adapter;
    private Set<Long> foldedNoteIds = new HashSet<>();
    private Set<Long> expandedComments = new HashSet<>();
    private Set<Long> hiddenNoteIds = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private Set<Long> selectedNoteIds = new HashSet<>();

    private MediaPlayer audioPlayer;
    private String playingAudioPath;
    private ImageButton playingAudioButton;
    private SeekBar playingAudioSeekBar;
    private TextView playingAudioCurrentTime;
    private TextView playingAudioTotalTime;
    private Handler audioProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable audioProgressRunnable;
    private final java.util.Map<String, Integer> audioProgressMap = new java.util.HashMap<>();
    
    /**
     * 初始化管理器
     * @param listView 列表视图
     * @param callback 回调接口
     */
    public void initialize(ListView listView, INoteListCallback callback) {
        this.listView = listView;
        this.callback = callback;
        setupListView();
    }
    
    /**
     * 设置ListView
     * 初始化点击和长按事件监听器
     * 注意：事件监听器实际在 loadNotes() 中设置，因为需要 adapter 创建后才能设置
     */
    private void setupListView() {
        // 点击和长按事件监听器将在 loadNotes() 中设置
        // 因为需要 adapter 创建后才能设置
    }
    
    /**
     * 加载笔记列表
     * 从数据库加载数据并显示在列表中
     * 从 MainActivity.loadMoments() 迁移过来
     */
    public void loadNotes() {
        if (callback == null || listView == null) {
            return;
        }
        
        try {
            // 重新加载时清理隐藏列表
            hiddenNoteIds.clear();
            // 先关闭之前的 Cursor 包装器（如果存在）
            if (noteCursorWrapper != null) {
                noteCursorWrapper.close();
                noteCursorWrapper = null;
            }
            
            NoteDbHelper dbHelper = callback.getDbHelper();
            if (dbHelper == null) {
                android.util.Log.e("NoteListManager", "dbHelper为null，无法加载笔记");
                return;
            }
            
            // 使用NoteDbHelper封装的方法加载笔记
            boolean timeDescOrder = callback.getTimeDescOrder();
            Cursor cursor = null;
            try {
                cursor = dbHelper.loadNotes(timeDescOrder);
            } catch (Exception e) {
                android.util.Log.e("NoteListManager", "加载笔记Cursor失败", e);
                throw new RuntimeException("加载笔记失败：" + e.getMessage(), e);
            }

            // 创建 Cursor 包装器
            String currentProject = callback.getProjectManager().getCurrentProject();
            noteCursorWrapper = new NoteCursorWrapper(cursor, currentProject);
            
            // 创建适配器
            adapter = new NoteListAdapter(noteCursorWrapper);
            
            // 设置适配器
            listView.setAdapter(adapter);
        } catch (Exception e) {
            android.util.Log.e("NoteListManager", "loadNotes异常", e);
            // 显示错误提示
            if (callback != null && callback.getContext() != null) {
                android.widget.Toast.makeText(
                    callback.getContext(),
                    "加载笔记失败：" + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG
                ).show();
            }
            // 确保清理资源
            if (noteCursorWrapper != null) {
                noteCursorWrapper.close();
                noteCursorWrapper = null;
            }
        }
        
        // 添加点击监听器
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Note note = (Note) adapter.getItem(position);
            if (note == null) {
                return;
            }

            long noteId = note.getId();
            if (isMultiSelectMode) {
                // 多选模式下，点击切换选择状态
                CheckBox checkBox = view.findViewById(R.id.checkBox);
                if (checkBox != null) {
                    checkBox.setChecked(!checkBox.isChecked());
                    if (checkBox.isChecked()) {
                        selectedNoteIds.add(noteId);
                    } else {
                        selectedNoteIds.remove(noteId);
                    }
                    if (callback != null) {
                        callback.onMultiSelectChanged(selectedNoteIds);
                        callback.onRequestRefreshMenu();
                    }
                }
            } else {
                // 非多选模式下，点击列表项直接显示笔记菜单
                showNoteOptionsMenu(view, noteId);
            }
        });
        
        // 添加长按监听器
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (isMultiSelectMode) {
                // 多选模式下，长按不执行操作
                return false;
            } else {
                Note note = (Note) adapter.getItem(position);
                long noteId = note.getId();
                showNoteOptionsMenu(view, noteId);
                return true; // 返回true表示消费了长按事件
            }
        });
    }
    
    /**
     * 刷新笔记列表
     * 重新加载数据并刷新显示
     */
    public void refreshNotes() {
        loadNotes();
    }

    /**
     * 隐藏指定笔记（不重新查询）
     * 用于移动后让列表自然上移
     * @param noteIds 需要隐藏的笔记ID集合
     */
    public void hideNotes(Set<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty() || adapter == null || listView == null) {
            return;
        }

        // 保存当前滚动位置
        int firstVisiblePosition = listView.getFirstVisiblePosition();
        View firstVisibleView = listView.getChildAt(0);
        int scrollOffset = 0;
        if (firstVisibleView != null) {
            scrollOffset = firstVisibleView.getTop();
        }

        hiddenNoteIds.addAll(noteIds);
        adapter.markVisiblePositionsDirty();
        adapter.notifyDataSetChanged();

        // 恢复滚动位置
        if (firstVisiblePosition >= 0) {
            int adjustedPosition = firstVisiblePosition;
            if (adjustedPosition >= adapter.getCount()) {
                adjustedPosition = Math.max(0, adapter.getCount() - 1);
            }
            listView.setSelectionFromTop(adjustedPosition, scrollOffset);
        }
    }

    /**
     * 切换项目
     * 当项目切换时，需要重新加载数据
     */
    public void switchProject() {
        // 清空状态
        foldedNoteIds.clear();
        expandedComments.clear();
        selectedNoteIds.clear();
        
        // 加载新项目的折叠状态
        loadFoldedNoteIds();
        
        // 重新加载数据
        loadNotes();
    }
    
    /**
     * 加载折叠状态（在初始化或切换项目时调用）
     */
    public void loadFoldedNoteIds() {
        if (callback == null) {
            return;
        }
        
        foldedNoteIds.clear();
        String currentProject = callback.getProjectManager().getCurrentProject();
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        SharedPreferences prefs = context.getSharedPreferences("noteplus_fold_state", Context.MODE_PRIVATE);
        String key = "folded_note_ids_" + currentProject;
        Set<String> foldedIdsStr = prefs.getStringSet(key, new HashSet<>());
        
        for (String idStr : foldedIdsStr) {
            try {
                foldedNoteIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                // 忽略无效的ID
            }
        }
    }
    
    /**
     * 设置多选模式
     * @param enabled true表示启用多选模式，false表示禁用
     */
    public void setMultiSelectMode(boolean enabled) {
        isMultiSelectMode = enabled;
        if (!enabled) {
            selectedNoteIds.clear();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (callback != null) {
            callback.onMultiSelectChanged(selectedNoteIds);
            callback.onRequestRefreshMenu();
        }
    }
    
    /**
     * 获取选中的笔记ID集合
     * @return 选中的笔记ID集合
     */
    public Set<Long> getSelectedNoteIds() {
        return selectedNoteIds;
    }
    
    /**
     * 根据 noteId 滚动到指定位置
     * @param noteId 笔记ID
     * @param offset 顶部偏移量（像素），默认留出8dp间距
     * @return 是否成功定位（true=找到并滚动，false=未找到）
     */
    public boolean scrollToNote(long noteId, int offset) {
        if (adapter == null || listView == null) {
            return false;
        }
        
        // 在 adapter 中查找 noteId 对应的 position
        int position = -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = (Note) adapter.getItem(i);
            if (note != null && note.getId() == noteId) {
                position = i;
                break;
            }
        }
        
        if (position >= 0) {
            // 滚动到指定位置
            listView.setSelectionFromTop(position, offset);
            return true;
        }
        
        return false;
    }
    
    /**
     * 根据 noteId 滚动到指定位置（使用默认偏移量8dp）
     * @param noteId 笔记ID
     * @return 是否成功定位
     */
    public boolean scrollToNote(long noteId) {
        if (callback != null) {
            Context context = callback.getContext();
            if (context != null) {
                int defaultOffset = person.notfresh.noteplus.util.DisplayUtil.dpToPx(context, 8);
                return scrollToNote(noteId, defaultOffset);
            }
        }
        return scrollToNote(noteId, 0);
    }
    
    // ========== 数据操作方法（从 MainActivity 迁移） ==========
    
    /**
     * 根据 noteId 和 TimeRangeFilter 找到附近的 Note 列表
     * TimeRangeFilter 相当于一个时间距离，前后的都可以
     * 
     * @param noteId 目标笔记ID
     * @param timeRange 时间范围过滤器（如最近1天、最近7天、最近30天）
     * @return 在时间范围内的 Note 列表
     */
    public List<Note> getNearNotes(long noteId, TimeRangeFilter timeRange) {
        List<Note> result = new ArrayList<>();
        
        if (adapter == null) {
            return result;
        }
        
        // 1. 先找到指定 noteId 的 Note，获取它的 timestamp
        Note targetNote = null;
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = adapter.getItem(i);
            if (note != null && note.getId() == noteId) {
                targetNote = note;
                break;
            }
        }
        
        // 如果找不到目标 Note，返回空列表
        if (targetNote == null) {
            return result;
        }
        
        long targetTimestamp = targetNote.getTimestamp();
        
        // 2. 根据 TimeRangeFilter 计算时间范围（前后都可以）
        // 将天数转换为毫秒数
        int days = timeRange.getDays();
        long timeRangeMillis = days * 24L * 60L * 60L * 1000L;
        
        // 计算时间范围的起始和结束时间
        long startTime = targetTimestamp - timeRangeMillis;
        long endTime = targetTimestamp + timeRangeMillis;
        
        // 3. 遍历 adapter 中的所有 Note，筛选出在时间范围内的 Note
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = adapter.getItem(i);
            if (note != null) {
                long noteTimestamp = note.getTimestamp();
                // 检查是否在时间范围内（包含边界）
                if (noteTimestamp >= startTime && noteTimestamp <= endTime) {
                    result.add(note);
                }
            }
        }
        
        // 4. 按时间逆序排序（最新的在前）
        result.sort((note1, note2) -> Long.compare(note2.getTimestamp(), note1.getTimestamp()));
        
        return result;
    }

    /**
     * 删除笔记
     * 从 MainActivity.deleteNote() 迁移
     * @param noteId 笔记ID
     */
    public void deleteNote(long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        // 使用NoteDbHelper封装的方法删除笔记
        int rowsDeleted = dbHelper.deleteNote(noteId);
        
        if (rowsDeleted > 0) {
            // 笔记删除后，从索引中移除
            SearchManager.getInstance(context).deleteNoteIndex(noteId);

            Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show();
            
            // 保存当前滚动位置
            int firstVisiblePosition = listView.getFirstVisiblePosition();
            View firstVisibleView = listView.getChildAt(0);
            int scrollOffset = 0;
            if (firstVisibleView != null) {
                scrollOffset = firstVisibleView.getTop();
            }
            
            // 重新查询 Cursor（Cursor 是查询时的快照，删除后需要重新查询才能反映最新数据）
            boolean timeDescOrder = callback.getTimeDescOrder();
            Cursor newCursor = dbHelper.loadNotes(timeDescOrder);
            
            // 更新 wrapper 的 Cursor（会自动清空缓存）
            if (noteCursorWrapper != null) {
                noteCursorWrapper.setCursor(newCursor);
                // 刷新适配器，ListView 会自动移除不存在的项，下面的项会自动浮上来
                if (adapter != null) {
                    adapter.markVisiblePositionsDirty();
                    adapter.notifyDataSetChanged();
                    // 恢复滚动位置
                    if (firstVisiblePosition >= 0) {
                        // 如果删除后列表变短，调整 position
                        int adjustedPosition = firstVisiblePosition;
                        if (adjustedPosition >= adapter.getCount()) {
                            adjustedPosition = Math.max(0, adapter.getCount() - 1);
                        }
                        listView.setSelectionFromTop(adjustedPosition, scrollOffset);
                    }
                }
            }
        }
    }
    
    /**
     * 显示删除确认对话框
     * 从 MainActivity.showDeleteConfirmDialog() 迁移
     * @param noteId 笔记ID
     */
    public void showDeleteConfirmDialog(long noteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("删除记录");
        builder.setMessage("确定要删除这条记录吗？此操作不可恢复。");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            deleteNote(noteId);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 添加评论
     * 从 MainActivity.showAddCommentDialog() 迁移
     * @param noteId 笔记ID
     * @param parentId 父评论ID（可为null）
     * @param content 评论内容
     * @param cost 花费金额
     */
    public void addComment(long noteId, Long parentId, String content, double cost) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        // 保存追加内容
        long commentId = dbHelper.addComment(noteId, parentId, content, cost);
        if (commentId != -1) {
            Toast.makeText(context, "追加成功", Toast.LENGTH_SHORT).show();
            // 刷新该笔记的显示
            refreshNoteView(noteId);
        } else {
            Toast.makeText(context, "追加失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 切换置顶状态
     * 从 MainActivity.togglePinNote() 迁移
     * @param noteId 笔记ID
     */
    public void togglePinNote(long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        boolean success = dbHelper.togglePinNote(noteId);
        if (success) {
            boolean isPinned = dbHelper.isNotePinned(noteId);
            Toast.makeText(context, isPinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
            // 刷新列表以更新排序
            loadNotes();
        } else {
            Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 刷新单个笔记视图
     * 从 MainActivity.refreshNoteView() 迁移
     * @param noteId 笔记ID
     */
    public void refreshNoteView(long noteId) {
        if (adapter == null || listView == null) {
            return;
        }
        
        // 找到该笔记在列表中的位置
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = (Note) adapter.getItem(i);
            if (note != null) {
                long id = note.getId();
                if (id == noteId) {
                    // 找到对应的视图
                    int firstVisible = listView.getFirstVisiblePosition();
                    int lastVisible = listView.getLastVisiblePosition();
                    
                    if (i >= firstVisible && i <= lastVisible) {
                        View view = listView.getChildAt(i - firstVisible);
                        if (view != null) {
                            double cost = note.getCost();
                            updateListItemWithExtras(view, noteId, cost);
                        }
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * 复制笔记内容到剪切板
     * 从 MainActivity.copyToClipboard() 迁移
     * @param noteId 笔记ID
     */
    public void copyToClipboard(long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        // 从数据库获取笔记内容
        String noteContent = getNoteContentById(noteId);
        
        if (noteContent != null && !noteContent.trim().isEmpty()) {
            // 获取剪切板管理器
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("笔记内容", noteContent);
            clipboard.setPrimaryClip(clip);
            
            // 显示成功提示
            Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } else {
            // 显示错误提示
            Toast.makeText(context, "无法获取笔记内容", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示编辑笔记对话框
     * 复用全屏编辑布局，支持修改笔记内容
     * @param noteId 笔记ID
     */
    public void showEditNoteDialog(long noteId) {
        if (callback == null) {
            return;
        }

        Context context = callback.getContext();
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (context == null || dbHelper == null) {
            return;
        }

        // 从 adapter 中获取笔记内容
        String currentContent = null;
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                Note note = adapter.getItem(i);
                if (note != null && note.getId() == noteId) {
                    currentContent = note.getContent();
                    break;
                }
            }
        }

        // 如果 adapter 中找不到，从数据库加载
        if (currentContent == null) {
            currentContent = getNoteContentById(noteId);
        }

        // 创建全屏编辑对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View fullscreenView = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_edit, null);
        builder.setView(fullscreenView);

        AlertDialog dialog = builder.create();

        EditText fullscreenEditText = fullscreenView.findViewById(R.id.fullscreenEditText);
        ImageButton btnExitFullscreen = fullscreenView.findViewById(R.id.btnExitFullscreen);
        Button btnSaveFullscreen = fullscreenView.findViewById(R.id.btnSaveFullscreen);
        Button btnInsertTimestamp = fullscreenView.findViewById(R.id.btnInsertTimestamp);
        Button btnArchive = fullscreenView.findViewById(R.id.btnArchive);

        if (fullscreenEditText == null || btnExitFullscreen == null || btnSaveFullscreen == null || btnInsertTimestamp == null || btnArchive == null) {
            android.util.Log.e("NoteListManager", "Failed to inflate fullscreen edit views");
            return;
        }

        // 填充当前内容
        fullscreenEditText.setText(currentContent != null ? currentContent : "");
        if (currentContent != null && currentContent.length() > 0) {
            fullscreenEditText.setSelection(currentContent.length());
        }

        // 退出按钮：关闭对话框，不保存
        btnExitFullscreen.setOnClickListener(v -> dialog.dismiss());

        // 插入时间戳按钮：在光标位置插入当前时间戳
        btnInsertTimestamp.setOnClickListener(v -> {
            SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]\n", Locale.CHINA);
            String timestamp = sdf.format(new Date());
            int cursorPosition = fullscreenEditText.getSelectionStart();
            fullscreenEditText.getText().insert(cursorPosition, timestamp);
        });

        // 存档按钮：保存但不退出
        btnArchive.setOnClickListener(v -> {
            String newContent = fullscreenEditText.getText().toString().trim();
            if (!newContent.isEmpty()) {
                updateNoteContent(noteId, newContent);
                Toast.makeText(context, "已存档", Toast.LENGTH_SHORT).show();
                // 刷新列表 cursor，显示存档后的内容
                listView.post(() -> {
                    if (noteCursorWrapper != null) {
                        NoteDbHelper helper = callback.getDbHelper();
                        if (helper != null) {
                            boolean timeDescOrder = callback.getTimeDescOrder();
                            Cursor newCursor = helper.loadNotes(timeDescOrder);
                            noteCursorWrapper.setCursor(newCursor);
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            } else {
                Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
            }
        });

        // 保存按钮：更新笔记内容并关闭
        btnSaveFullscreen.setOnClickListener(v -> {
            String newContent = fullscreenEditText.getText().toString().trim();
            if (!newContent.isEmpty()) {
                updateNoteContent(noteId, newContent);
            } else {
                Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            // dialog关闭后，使用新Cursor刷新adapter
            listView.post(() -> {
                android.util.Log.d("NoteListManager", "post: refreshing note " + noteId);
                if (noteCursorWrapper != null) {
                    NoteDbHelper helper = callback.getDbHelper();
                    if (helper != null) {
                        boolean timeDescOrder = callback.getTimeDescOrder();
                        Cursor newCursor = helper.loadNotes(timeDescOrder);
                        noteCursorWrapper.setCursor(newCursor);
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        });

        // 设置窗口属性
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.white);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.show();

        // 显示键盘
        fullscreenEditText.post(() -> {
            fullscreenEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(fullscreenEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /**
     * 根据笔记ID获取笔记内容
     * 从 MainActivity.getNoteContentById() 迁移
     * @param noteId 笔记ID
     * @return 笔记内容字符串，如果获取失败返回null
     */
    private String getNoteContentById(long noteId) {
        if (callback == null) {
            return null;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return null;
        }
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String content = null;
        
        Cursor cursor = db.query(
            NoteDbHelper.TABLE_NOTES,
            new String[]{NoteDbHelper.COLUMN_CONTENT},
            NoteDbHelper.COLUMN_ID + "=?",
            new String[]{String.valueOf(noteId)},
            null, null, null
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int contentIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_CONTENT);
            if (contentIndex != -1) {
                content = cursor.getString(contentIndex);
            }
            cursor.close();
        }
        
        return content;
    }

    /**
     * 更新笔记内容
     * @param noteId 笔记ID
     * @param newContent 新的笔记内容
     */
    private void updateNoteContent(long noteId, String newContent) {
        if (callback == null) {
            return;
        }

        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(NoteDbHelper.COLUMN_CONTENT, newContent);

        db.update(
            NoteDbHelper.TABLE_NOTES,
            values,
            NoteDbHelper.COLUMN_ID + "=?",
            new String[]{String.valueOf(noteId)}
        );
    }

    /**
     * 更新列表项的内容文本视图
     * @param noteId 笔记ID
     * @param newContent 新的内容
     */
    private void updateNoteContentView(long noteId, String newContent) {
        if (adapter == null || listView == null) {
            android.util.Log.e("NoteListManager", "updateNoteContentView: adapter or listView is null");
            return;
        }

        // 先通过 adapter 找到笔记在列表中的位置
        int targetPosition = -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = adapter.getItem(i);
            if (note != null && note.getId() == noteId) {
                targetPosition = i;
                break;
            }
        }

        android.util.Log.d("NoteListManager", "updateNoteContentView: noteId=" + noteId + ", targetPosition=" + targetPosition);

        if (targetPosition < 0) {
            return;
        }

        // 检查该位置是否在可见区域内
        int firstVisible = listView.getFirstVisiblePosition();
        int lastVisible = listView.getLastVisiblePosition();
        android.util.Log.d("NoteListManager", "updateNoteContentView: firstVisible=" + firstVisible + ", lastVisible=" + lastVisible);

        if (targetPosition >= firstVisible && targetPosition <= lastVisible) {
            int viewIndex = targetPosition - firstVisible;
            View view = listView.getChildAt(viewIndex);
            android.util.Log.d("NoteListManager", "updateNoteContentView: viewIndex=" + viewIndex + ", view=" + view);
            if (view != null) {
                TextView contentText = view.findViewById(R.id.contentText);
                android.util.Log.d("NoteListManager", "updateNoteContentView: contentText=" + contentText);
                if (contentText != null) {
                    android.util.Log.d("NoteListManager", "updateNoteContentView: setting text from '" + contentText.getText() + "' to '" + newContent + "'");
                    contentText.setText(newContent);
                    view.invalidate();
                    view.postInvalidate();
                }
            }
        }
    }

    // ========== 辅助方法（从 MainActivity 迁移） ==========
    
    /**
     * 为列表项添加时间区间和标签信息
     * 从 MainActivity.updateListItemWithExtras() 迁移
     */
    private void updateListItemWithExtras(View view, long noteId, double cost) {
        Context context = callback != null ? callback.getContext() : null;
        if (context == null) {
            return;
        }
        
        // 查找或创建额外信息容器
        LinearLayout extrasContainer = view.findViewById(R.id.extrasContainer);
        if (extrasContainer == null) {
            TextView contentText = view.findViewById(R.id.contentText);
            ViewGroup parent = (ViewGroup) contentText.getParent();
            
            extrasContainer = new LinearLayout(context);
            extrasContainer.setId(R.id.extrasContainer);
            extrasContainer.setOrientation(LinearLayout.VERTICAL);
            extrasContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
            parent.addView(extrasContainer, parent.indexOfChild(contentText) + 1);
        }
        
        // 清空现有内容（按钮会在checkAndShowFoldButton中重新添加）
        extrasContainer.removeAllViews();
        
        // 添加时间区间信息
        addTimeRangeInfo(extrasContainer, noteId);
        
        // 添加标签信息
        addTagsInfo(extrasContainer, noteId);

        // 添加图片信息
        addImagesInfo(extrasContainer, noteId);

        // 添加音频信息
        addAudioInfo(extrasContainer, noteId);
        
        // 添加追加内容信息
        addCommentsInfo(extrasContainer, noteId);
        
        // 如果配置显示花费且花费大于0，则在记录旁边显示花费
        boolean showCost = callback != null ? callback.getShowCost() : false;
        android.util.Log.d("DEBUG", "updateListItemWithExtras: noteId=" + noteId + ", cost=" + cost + ", showCost=" + showCost);
        if (showCost && cost > 0) {
            // 获取内容文本视图
            TextView contentText = view.findViewById(R.id.contentText);
            String currentText = contentText.getText().toString();
            android.util.Log.d("DEBUG", "updateListItemWithExtras: 添加费用, currentText=" + currentText);

            // 在文本后面添加花费信息
            String costText = String.format(" [¥%.2f]", cost);
            contentText.setText(currentText + costText);
            android.util.Log.d("DEBUG", "updateListItemWithExtras: 添加后=" + contentText.getText().toString());
        }
    }
    
    /**
     * 添加时间区间信息到列表项
     * 从 MainActivity.addTimeRangeInfo() 迁移
     */
    private void addTimeRangeInfo(LinearLayout container, long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
        
        if (timeRangeCursor != null && timeRangeCursor.moveToFirst()) {
            @SuppressLint("Range") long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_START_TIME));
            @SuppressLint("Range") long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_END_TIME));
            
            LinearLayout timeRangeLayout = new LinearLayout(context);
            timeRangeLayout.setOrientation(LinearLayout.HORIZONTAL);
            int padding = DisplayUtil.dpToPx(context, 4);
            timeRangeLayout.setPadding(padding, padding, padding, padding);
            
            TextView timeRangeLabel = new TextView(context);
            timeRangeLabel.setText("时间区间: ");
            timeRangeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            timeRangeLayout.addView(timeRangeLabel);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            String timeRangeText = sdf.format(new Date(startTime)) + " 至 " + sdf.format(new Date(endTime));
            
            TextView timeRangeValue = new TextView(context);
            timeRangeValue.setText(timeRangeText);
            timeRangeLayout.addView(timeRangeValue);
            
            container.addView(timeRangeLayout);
            
            timeRangeCursor.close();
        }
    }
    
    /**
     * 添加标签信息到列表项
     * 从 MainActivity.addTagsInfo() 迁移
     */
    private void addTagsInfo(LinearLayout container, long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
        
        if (tagsCursor != null && tagsCursor.getCount() > 0) {
            LinearLayout tagLayout = new LinearLayout(context);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            int padding = DisplayUtil.dpToPx(context, 4);
            tagLayout.setPadding(padding, padding, padding, padding);
            
            TextView tagsLabel = new TextView(context);
            tagsLabel.setText("标签: ");
            tagsLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tagLayout.addView(tagsLabel);
            
            LinearLayout tagsContainer = new LinearLayout(context);
            tagsContainer.setOrientation(LinearLayout.HORIZONTAL);
            
            while (tagsCursor.moveToNext()) {
                @SuppressLint("Range") String tagName = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                @SuppressLint("Range") String tagColor = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                
                TextView tagView = new TextView(context);
                tagView.setText(tagName);
                int tagPadding = DisplayUtil.dpToPx(context, 4);
                int tagPaddingTop = DisplayUtil.dpToPx(context, 2);
                tagView.setPadding(tagPadding, tagPaddingTop, tagPadding, tagPaddingTop);
                tagView.setTextColor(Color.WHITE);
                
                try {
                    tagView.setBackgroundColor(Color.parseColor(tagColor));
                } catch (Exception e) {
                    tagView.setBackgroundColor(Color.GRAY);
                }
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(DisplayUtil.dpToPx(context, 4), 0, 0, 0);
                tagView.setLayoutParams(params);
                
                tagsContainer.addView(tagView);
            }
            
            tagLayout.addView(tagsContainer);
            container.addView(tagLayout);
            
            tagsCursor.close();
        }
    }

    /**
     * 添加图片信息到列表项
     */
    private void addImagesInfo(LinearLayout container, long noteId) {
        if (callback == null) {
            return;
        }

        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }

        java.util.List<String> imagePaths = dbHelper.getNoteImagePaths(noteId);
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }

        LinearLayout imageLayout = new LinearLayout(context);
        imageLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = DisplayUtil.dpToPx(context, 4);
        imageLayout.setPadding(padding, padding, padding, padding);

        TextView label = new TextView(context);
        label.setText("图片: ");
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        imageLayout.addView(label);

        android.widget.HorizontalScrollView scrollView = new android.widget.HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        int imageSize = DisplayUtil.dpToPx(context, 64);
        int margin = DisplayUtil.dpToPx(context, 4);

        for (String path : imagePaths) {
            android.widget.ImageView imageView = new android.widget.ImageView(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(imageSize, imageSize);
            params.setMargins(0, 0, margin, 0);
            imageView.setLayoutParams(params);
            imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

            android.graphics.Bitmap bitmap = decodeSampledBitmap(path, imageSize, imageSize);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }

            int index = row.getChildCount();
            imageView.setOnClickListener(v -> showImagePreview(context, imagePaths, index));
            imageView.setOnLongClickListener(v -> {
                showImageActionMenu(context, noteId, path, v);
                return true;
            });
            row.addView(imageView);
        }

        scrollView.addView(row);
        imageLayout.addView(scrollView);
        container.addView(imageLayout);
    }

    private void showImagePreview(Context context, java.util.List<String> imagePaths, int currentIndex) {
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            ImagePreviewDialog dialog = ImagePreviewDialog.newInstance(imagePaths, currentIndex);
            dialog.show(activity.getSupportFragmentManager(), "ImagePreviewDialog");
        } else {
            Toast.makeText(context, "无法显示图片预览", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImageActionMenu(Context context, long noteId, String imagePath, View anchorView) {
        if (context == null || imagePath == null) {
            return;
        }

        String[] options = new String[]{"删除图片", "笔记菜单"};
        new android.app.AlertDialog.Builder(context)
                .setTitle("图片操作")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRemoveImageDialog(context, noteId, imagePath);
                    } else if (which == 1) {
                        View noteItemView = findNoteItemView(anchorView, noteId);
                        if (noteItemView != null) {
                            showNoteOptionsMenu(noteItemView, noteId);
                        } else {
                            Toast.makeText(context, "无法打开笔记菜单", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRemoveImageDialog(Context context, long noteId, String imagePath) {
        NoteDbHelper dbHelper = callback != null ? callback.getDbHelper() : null;
        new android.app.AlertDialog.Builder(context)
                .setTitle("删除图片")
                .setMessage("确定要删除这张图片吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (dbHelper != null) {
                        dbHelper.deleteNoteImage(noteId, imagePath);
                    }
                    java.io.File imageFile = new java.io.File(imagePath);
                    if (imageFile.exists()) {
                        imageFile.delete();
                    }
                    refreshNoteView(noteId);
                    Toast.makeText(context, "图片已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View findNoteItemView(View startView, long noteId) {
        View current = startView;
        while (current != null) {
            Object tag = current.getTag();
            if (tag instanceof Long && ((Long) tag) == noteId) {
                return current;
            }
            if (tag instanceof Number && ((Number) tag).longValue() == noteId) {
                return current;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    private android.graphics.Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        if (path == null) {
            return null;
        }
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            return null;
        }

        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, options);

        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        return android.graphics.BitmapFactory.decodeFile(path, options);
    }

    /**
     * 添加音频信息到列表项
     */
    private void addAudioInfo(LinearLayout container, long noteId) {
        if (callback == null) {
            return;
        }

        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }

        List<AudioAttachment> audioItems = dbHelper.getNoteAudioItems(noteId);
        if (audioItems == null || audioItems.isEmpty()) {
            return;
        }

        LinearLayout audioLayout = new LinearLayout(context);
        audioLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = DisplayUtil.dpToPx(context, 4);
        audioLayout.setPadding(padding, padding, padding, padding);

        TextView label = new TextView(context);
        label.setText("录音: ");
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        audioLayout.addView(label);

        for (int i = 0; i < audioItems.size(); i++) {
            AudioAttachment item = audioItems.get(i);
            View itemView = LayoutInflater.from(context).inflate(R.layout.item_audio_attachment, audioLayout, false);

            TextView nameText = itemView.findViewById(R.id.audioItemName);
            TextView durationText = itemView.findViewById(R.id.audioItemDuration);
            TextView currentTimeText = itemView.findViewById(R.id.audioItemCurrentTime);
            SeekBar seekBar = itemView.findViewById(R.id.audioItemSeekBar);
            ImageButton playButton = itemView.findViewById(R.id.audioItemPlayPauseButton);
            ImageButton exportButton = itemView.findViewById(R.id.audioItemExportButton);
            ImageButton deleteButton = itemView.findViewById(R.id.audioItemDeleteButton);

            nameText.setText("录音 " + (i + 1));
            durationText.setText(formatDuration(item.getDurationMs()));
            int savedProgress = 0;
            if (item.getPath() != null && audioProgressMap.containsKey(item.getPath())) {
                savedProgress = audioProgressMap.get(item.getPath());
            }
            currentTimeText.setText(formatDuration(savedProgress));
            seekBar.setMax((int) Math.max(1, item.getDurationMs()));
            seekBar.setProgress(savedProgress);

            playButton.setOnClickListener(v -> toggleAudioPlayback(context, item.getPath(), playButton, seekBar, currentTimeText, durationText));
            exportButton.setOnClickListener(v -> exportAudioFile(context, item.getPath()));
            deleteButton.setOnClickListener(v -> showRemoveAudioDialog(context, noteId, item));

            if (item.getPath() != null && item.getPath().equals(playingAudioPath)) {
                playingAudioButton = playButton;
                playingAudioSeekBar = seekBar;
                playingAudioCurrentTime = currentTimeText;
                playingAudioTotalTime = durationText;
                playButton.setImageResource(R.drawable.ic_audio_pause);
            }

            audioLayout.addView(itemView);
        }

        container.addView(audioLayout);
    }

    private void showRemoveAudioDialog(Context context, long noteId, AudioAttachment item) {
        if (context == null || item == null || item.getPath() == null) {
            return;
        }
        NoteDbHelper dbHelper = callback != null ? callback.getDbHelper() : null;
        new android.app.AlertDialog.Builder(context)
                .setTitle("删除录音")
                .setMessage("确定要删除这条录音吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (dbHelper != null) {
                        dbHelper.deleteNoteAudio(noteId, item.getPath());
                    }
                    java.io.File audioFile = new java.io.File(item.getPath());
                    if (audioFile.exists()) {
                        audioFile.delete();
                    }
                    refreshNoteView(noteId);
                    Toast.makeText(context, "录音已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleAudioPlayback(Context context, String path, ImageButton playButton,
                                     SeekBar seekBar, TextView currentTimeText, TextView totalTimeText) {
        if (path == null || context == null) {
            return;
        }

        if (audioPlayer != null && path.equals(playingAudioPath)) {
            if (audioPlayer.isPlaying()) {
                audioPlayer.pause();
                playButton.setImageResource(R.drawable.ic_audio_play);
                stopAudioProgressUpdates();
            } else {
                audioPlayer.start();
                playButton.setImageResource(R.drawable.ic_audio_pause);
                startAudioProgressUpdates();
            }
            return;
        }

        stopAudioPlayer();

        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setDataSource(path);
            audioPlayer.prepare();
            audioPlayer.start();
            playingAudioPath = path;
            playingAudioButton = playButton;
            playingAudioSeekBar = seekBar;
            playingAudioCurrentTime = currentTimeText;
            playingAudioTotalTime = totalTimeText;
            playButton.setImageResource(R.drawable.ic_audio_pause);
            int duration = audioPlayer.getDuration();
            if (playingAudioSeekBar != null) {
                playingAudioSeekBar.setMax(Math.max(1, duration));
                playingAudioSeekBar.setProgress(0);
            }
            if (playingAudioTotalTime != null) {
                playingAudioTotalTime.setText(formatDuration(duration));
            }
            if (playingAudioCurrentTime != null) {
                playingAudioCurrentTime.setText("00:00");
            }
            if (playingAudioSeekBar != null) {
                playingAudioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && audioPlayer != null) {
                            audioPlayer.seekTo(progress);
                            if (playingAudioCurrentTime != null) {
                                playingAudioCurrentTime.setText(formatDuration(progress));
                            }
                            if (playingAudioPath != null) {
                                audioProgressMap.put(playingAudioPath, progress);
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }
            startAudioProgressUpdates();
            audioPlayer.setOnCompletionListener(mp -> {
                if (playingAudioButton != null) {
                    playingAudioButton.setImageResource(R.drawable.ic_audio_play);
                }
                if (playingAudioPath != null) {
                    audioProgressMap.put(playingAudioPath, 0);
                }
                stopAudioProgressUpdates();
                stopAudioPlayer();
            });
            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                if (playingAudioButton != null) {
                    playingAudioButton.setImageResource(R.drawable.ic_audio_play);
                }
                stopAudioProgressUpdates();
                stopAudioPlayer();
                Toast.makeText(context, "音频播放失败", Toast.LENGTH_SHORT).show();
                return true;
            });
        } catch (Exception e) {
            android.util.Log.e("NoteListManager", "播放音频失败", e);
            Toast.makeText(context, "播放音频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAudioPlayer() {
        if (audioPlayer != null) {
            try {
                audioPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                audioPlayer.release();
            } catch (Exception ignored) {
            }
            audioPlayer = null;
        }
        playingAudioPath = null;
        if (playingAudioButton != null) {
            playingAudioButton.setImageResource(R.drawable.ic_audio_play);
        }
        if (playingAudioSeekBar != null) {
            playingAudioSeekBar.setProgress(0);
        }
        if (playingAudioCurrentTime != null) {
            playingAudioCurrentTime.setText("00:00");
        }
        playingAudioButton = null;
        playingAudioSeekBar = null;
        playingAudioCurrentTime = null;
        playingAudioTotalTime = null;
        stopAudioProgressUpdates();
    }

    private void startAudioProgressUpdates() {
        stopAudioProgressUpdates();
        audioProgressRunnable = () -> {
            if (audioPlayer != null && audioPlayer.isPlaying()) {
                int position = audioPlayer.getCurrentPosition();
                if (playingAudioSeekBar != null) {
                    playingAudioSeekBar.setProgress(position);
                }
                if (playingAudioCurrentTime != null) {
                    playingAudioCurrentTime.setText(formatDuration(position));
                }
                if (playingAudioPath != null) {
                    audioProgressMap.put(playingAudioPath, position);
                }
                audioProgressHandler.postDelayed(audioProgressRunnable, 300);
            }
        };
        audioProgressHandler.post(audioProgressRunnable);
    }

    private void stopAudioProgressUpdates() {
        if (audioProgressRunnable != null) {
            audioProgressHandler.removeCallbacks(audioProgressRunnable);
            audioProgressRunnable = null;
        }
    }

    private void exportAudioFile(Context context, String path) {
        if (context == null || path == null) {
            return;
        }
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            Toast.makeText(context, "音频文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "导出录音"));
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }
    
    /**
     * 添加追加内容信息到列表项
     * 从 MainActivity.addCommentsInfo() 迁移
     */
    private void addCommentsInfo(LinearLayout extraContainer, long noteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (context == null || dbHelper == null) {
            return;
        }
        
        // 创建追加内容容器（低调，无背景色，无边框）
        LinearLayout commentsContainer = new LinearLayout(context);
        commentsContainer.setOrientation(LinearLayout.VERTICAL);
        commentsContainer.setPadding(0, DisplayUtil.dpToPx(context, 4), 0, 0); // 减少内边距，更低调

        // 创建操作行
        LinearLayout actionLayout = new LinearLayout(context);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setPadding(0, DisplayUtil.dpToPx(context, 4), 0, 0);
        LinearLayout.LayoutParams actionLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionLayout.setLayoutParams(actionLayoutParams);

        // 添加"添加追加"按钮
        TextView addCommentButton = new TextView(context);
        addCommentButton.setText("追加内容");
        addCommentButton.setTextColor(0xFF2196F3);
        addCommentButton.setTextSize(14);
        LinearLayout.LayoutParams addButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addButtonParams.setMargins(0, 0, 0, 0);
        addCommentButton.setLayoutParams(addButtonParams);
        addCommentButton.setOnClickListener(v -> {
            showAddCommentDialog(noteId, null);
        });
        actionLayout.addView(addCommentButton);

        // 添加"标签"按钮
        TextView tagButton = new TextView(context);
        tagButton.setText("标签");
        tagButton.setTextColor(0xFF4CAF50); // 绿色，与追加内容按钮区分
        tagButton.setTextSize(14);
        LinearLayout.LayoutParams tagButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tagButtonParams.setMargins(DisplayUtil.dpToPx(context, 16), 0, 0, 0);
        tagButton.setLayoutParams(tagButtonParams);
        tagButton.setOnClickListener(v -> {
            showNoteTagDialog(noteId);
        });
        actionLayout.addView(tagButton);

        commentsContainer.addView(actionLayout);

        int commentCount = dbHelper.getCommentCount(noteId);
        // 判断是否需要追加内容的展开/折叠功能
        boolean needExpandCollapse = commentCount > 3;
        boolean isExpanded = expandedComments.contains(noteId);

        if (needExpandCollapse) {
            // 添加展开/折叠按钮
            TextView expandCollapseText = new TextView(context);
            expandCollapseText.setText(isExpanded ? "收起追加" : "展开追加");
            expandCollapseText.setTextColor(0xFF2196F3);
            expandCollapseText.setTextSize(14);
            LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            expandParams.setMargins(DisplayUtil.dpToPx(context, 4), 0, 0, 0);
            expandCollapseText.setLayoutParams(expandParams);

            expandCollapseText.setOnClickListener(v -> {
                toggleCommentsExpanded(noteId, commentsContainer);
            });
            actionLayout.addView(expandCollapseText);
        }
        // 显示追加内容（如果有评论）
        if (commentCount > 0) {
            int maxDisplay = 3; // 默认显示3条
            if (needExpandCollapse && isExpanded) {
                maxDisplay = Integer.MAX_VALUE;
            }
            displayComments(commentsContainer, noteId, maxDisplay);
        }
        extraContainer.addView(commentsContainer);
    }
    
    /**
     * 切换追加内容的展开/折叠状态
     * 从 MainActivity.toggleCommentsExpanded() 迁移
     */
    private void toggleCommentsExpanded(long noteId, LinearLayout commentContainer) {
        // 移除所有评论内容（保留底部操作行）
        for (int i = commentContainer.getChildCount() - 1; i >= 1; i--) {
            commentContainer.removeViewAt(i);
        }
        // 现在 container 只剩下操作行了
        if (expandedComments.contains(noteId)) {
            // 当前是展开状态，切换到折叠状态（收起）
            expandedComments.remove(noteId);
            // 重新显示前3条（在操作行之前插入，索引0）
            displayComments(commentContainer, noteId, 3);
            // 更新展开/折叠按钮文本
            updateExpandCollapseButton(commentContainer, false);
        } else {
            // 当前是折叠状态，切换到展开状态
            expandedComments.add(noteId);
            // 显示全部（在操作行之前插入，索引0）
            displayComments(commentContainer, noteId, Integer.MAX_VALUE);
            // 更新展开/折叠按钮文本
            updateExpandCollapseButton(commentContainer, true);
        }
    }
    
    /**
     * 更新展开/折叠按钮文本
     * 从 MainActivity.updateExpandCollapseButton() 迁移
     */
    private void updateExpandCollapseButton(LinearLayout container, boolean isExpanded) {
        int actionLayoutIndex = 0;
        LinearLayout actionLayout = (LinearLayout) container.getChildAt(actionLayoutIndex);
        if (actionLayout != null && actionLayout.getChildCount() > 1) {
            TextView expandCollapseText = (TextView) actionLayout.getChildAt(1);
            if (expandCollapseText != null) {
                expandCollapseText.setText(isExpanded ? "收起追加" : "展开追加");
            }
        }
    }
    
    /**
     * 显示追加内容
     * 从 MainActivity.displayComments() 迁移
     */
    private void displayComments(LinearLayout container, long noteId, int maxDisplay) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        Cursor commentsCursor = dbHelper.getCommentsForNote(noteId);
        
        boolean hasComments = commentsCursor != null && commentsCursor.getCount() > 0;
        
        if (hasComments) {
            int count = 0;
            
            while (commentsCursor.moveToNext() && count < maxDisplay) {
                long commentId = commentsCursor.getLong(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID));
                int parentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                String content = commentsCursor.getString(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
                long timestamp = commentsCursor.getLong(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP));
                double cost = commentsCursor.getDouble(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST));
                // 检查parent_comment_id是否为NULL
                Long parentId = null;
                if (!commentsCursor.isNull(parentIdIndex)) {
                    parentId = commentsCursor.getLong(parentIdIndex);
                }
                // 获取当前评论的编号（所有评论都按时间顺序编号）
                int currentNumber = dbHelper.getCommentNumber(commentId);
                // 创建追加内容项
                LinearLayout commentItem = new LinearLayout(context);
                commentItem.setOrientation(LinearLayout.VERTICAL);
                int itemPadding = DisplayUtil.dpToPx(context, 8);
                int itemPaddingTop = DisplayUtil.dpToPx(context, 4);
                commentItem.setPadding(itemPadding, itemPaddingTop, itemPadding, itemPaddingTop);
                
                // 构建显示文本
                String displayText;
                if (parentId == null) {
                    // 直接回复笔记：@编号 内容
                    displayText = DisplayUtil.formatCommentTimestamp(timestamp) + " @" + currentNumber + " " + content;
                } else {
                    // 回复评论：@当前编号 追加@父编号 内容
                    int parentNumber = dbHelper.getCommentNumber(parentId);
                    if (parentNumber > 0) {
                        displayText = DisplayUtil.formatCommentTimestamp(timestamp) + " @" + currentNumber + " 追加@" + parentNumber + " " + content;
                    } else {
                        // 如果找不到父评论编号，只显示当前编号
                        displayText = DisplayUtil.formatCommentTimestamp(timestamp) + " @" + currentNumber + " " + content;
                    }
                }
                
                // 时间戳和编号
                TextView timeText = new TextView(context);
                timeText.setText(displayText);
                timeText.setTextColor(0xFF333333);
                timeText.setTextSize(14);
                commentItem.addView(timeText);
                
                // 花费（如果有）
                boolean showCost = callback.getShowCost();
                if (showCost && cost > 0) {
                    TextView costText = new TextView(context);
                    costText.setText(String.format("花费: ¥%.2f", cost));
                    costText.setTextColor(0xFF4CAF50);
                    costText.setTextSize(12);
                    costText.setPadding(0, DisplayUtil.dpToPx(context, 2), 0, 0);
                    commentItem.addView(costText);
                }
                
                // 添加"添加追加"按钮（回复该评论）- 样式与展开/折叠按钮一致
                TextView addReplyButton = new TextView(context);
                addReplyButton.setText("回复追加");
                addReplyButton.setTextColor(0xFF2196F3); // 蓝色，与展开/折叠按钮一致
                addReplyButton.setTextSize(12);
                addReplyButton.setPadding(0, DisplayUtil.dpToPx(context, 4), 0, 0);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                addReplyButton.setLayoutParams(buttonParams);
                addReplyButton.setOnClickListener(v -> {
                    showAddCommentDialog(noteId, commentId);
                });
                commentItem.addView(addReplyButton);
                
                // 长按显示选项菜单
                commentItem.setOnLongClickListener(v -> {
                    showCommentOptionsMenu(commentId, noteId);
                    return true;
                });
                
                container.addView(commentItem);
                count++;
            }
            commentsCursor.close();
        }
    }
    
    /**
     * 检查并显示折叠按钮
     * 从 MainActivity.checkAndShowFoldButton() 迁移
     */
    private void checkAndShowFoldButton(View view, long noteId, String content, boolean isPinned) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        // 读取当前项目的折叠字数配置
        String foldLengthStr = callback.getSetting(NoteDbHelper.KEY_FOLD_DISPLAY_LENGTH, "300");
        int foldDisplayLength;
        try {
            foldDisplayLength = Integer.parseInt(foldLengthStr);
        } catch (NumberFormatException e) {
            foldDisplayLength = 300; // 默认值
        }
        
        // 计算笔记字数
        int wordCount = StringUtil.calculateWordCount(content);
        
        // 如果超过配置值，显示折叠按钮
        if (wordCount > foldDisplayLength) {
            TextView contentText = view.findViewById(R.id.contentText);
            if (contentText != null) {
                boolean isFolded = foldedNoteIds.contains(noteId);
                String displayText;
                String buttonText;
                
                if (isFolded) {
                    // 折叠状态：显示截断后的文本
                    String truncated = StringUtil.truncateToWordCount(content, foldDisplayLength);
                    displayText = truncated + "...";
                    buttonText = "展开记录";
                } else {
                    // 展开状态：显示完整内容
                    displayText = content;
                    buttonText = "折叠记录";
                }
                
                // 设置内容文本（需要保留可能已添加的花费信息和置顶标识）
                String currentText = contentText.getText().toString();
                String finalText = displayText;
                
                // 检查是否有置顶标识
                boolean hasPinPrefix = currentText.startsWith("📌 ");
                if (hasPinPrefix || isPinned) {
                    // 如果当前文本已有置顶标识，或者笔记是置顶的，确保标识在最前面
                    if (!finalText.startsWith("📌 ")) {
                        finalText = "📌 " + finalText;
                    }
                }
                
                // 检查是否有花费信息（格式：[¥xx.xx]）
                if (currentText.contains(" [¥")) {
                    int costIndex = currentText.lastIndexOf(" [¥");
                    if (costIndex > 0) {
                        String costPart = currentText.substring(costIndex);
                        finalText = finalText + costPart;
                    }
                }
                contentText.setText(finalText);
                
                // 获取或创建extrasContainer（用于放置按钮）
                LinearLayout extrasContainer = view.findViewById(R.id.extrasContainer);
                if (extrasContainer == null) {
                    // 如果extrasContainer不存在，创建它
                    ViewGroup parent = (ViewGroup) contentText.getParent();
                    if (parent != null) {
                        extrasContainer = new LinearLayout(context);
                        extrasContainer.setId(R.id.extrasContainer);
                        extrasContainer.setOrientation(LinearLayout.VERTICAL);
                        extrasContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                        parent.addView(extrasContainer, parent.indexOfChild(contentText) + 1);
                    } else {
                        return; // 如果找不到父容器，无法添加按钮
                    }
                }
                
                // 查找或创建折叠按钮
                TextView foldButton = (TextView) view.findViewWithTag("foldButton_" + noteId);
                if (foldButton == null) {
                    // 创建新按钮
                    foldButton = new TextView(context);
                    foldButton.setTag("foldButton_" + noteId);
                    foldButton.setTextColor(Color.BLACK);
                    foldButton.setTypeface(null, android.graphics.Typeface.BOLD);
                    int foldPadding = DisplayUtil.dpToPx(context, 4);
                    int foldPaddingTop = DisplayUtil.dpToPx(context, 8);
                    foldButton.setPadding(foldPadding, foldPaddingTop, 0, foldPadding);
                    foldButton.setTextSize(14);
                    
                    // 将按钮添加到extrasContainer底部
                    extrasContainer.addView(foldButton);
                } else {
                    // 确保已存在的按钮也保持加粗样式
                    foldButton.setTypeface(null, android.graphics.Typeface.BOLD);
                }
                
                // 设置按钮文本和点击事件
                foldButton.setText(buttonText);
                foldButton.setOnClickListener(v -> {
                    // 找到被点击的View在ListView中的position
                    int position = -1;
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View childView = listView.getChildAt(i);
                        if (childView != null && childView == view) {
                            position = listView.getFirstVisiblePosition() + i;
                            break;
                        }
                    }
                    
                    // 切换折叠状态
                    toggleNoteFold(noteId);
                    
                    // 直接更新当前View，不刷新整个列表
                    updateSingleNoteView(noteId, content, position);
                });
                foldButton.setVisibility(View.VISIBLE);
            }
        } else {
            // 如果不超过配置值，隐藏折叠按钮
            TextView foldButton = (TextView) view.findViewWithTag("foldButton_" + noteId);
            if (foldButton != null) {
                foldButton.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * 切换笔记的折叠状态
     * 从 MainActivity.toggleNoteFold() 迁移
     */
    private void toggleNoteFold(long noteId) {
        if (foldedNoteIds.contains(noteId)) {
            foldedNoteIds.remove(noteId);
        } else {
            foldedNoteIds.add(noteId);
        }
        saveFoldedNoteIds();
    }
    
    /**
     * 更新单个笔记的视图（用于折叠/展开）
     * 从 MainActivity.updateSingleNoteView() 迁移
     */
    private void updateSingleNoteView(long noteId, String content, int targetPosition) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return;
        }
        
        // 获取置顶状态
        boolean isPinned = dbHelper.isNotePinned(noteId);
        
        // 保存当前滚动位置（作为备用）
        int firstVisiblePosition = listView.getFirstVisiblePosition();
        View firstVisibleView = listView.getChildAt(0);
        int scrollOffset = 0;
        if (firstVisibleView != null) {
            scrollOffset = firstVisibleView.getTop();
        }
        
        // 查找对应的View并更新
        boolean found = false;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View childView = listView.getChildAt(i);
            if (childView != null) {
                // 通过tag查找对应的笔记
                Object tag = childView.getTag();
                if (tag != null && tag.equals(noteId)) {
                    // 找到对应的View，直接更新
                    // 重新调用checkAndShowFoldButton来更新内容和按钮
                    checkAndShowFoldButton(childView, noteId, content, isPinned);
                    found = true;
                    break;
                }
            }
        }
        
        // 如果View不在可见区域，使用notifyDataSetChanged但恢复滚动位置
        if (!found) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            // 如果知道目标position，滚动到该位置；否则恢复之前的滚动位置
            Context context = callback.getContext();
            if (context != null) {
                int offset = DisplayUtil.dpToPx(context, 8);
                if (targetPosition >= 0) {
                    listView.setSelectionFromTop(targetPosition, offset);
                } else {
                    listView.setSelectionFromTop(firstVisiblePosition, scrollOffset);
                }
            }
        } else {
            // 如果找到了View，滚动到该记录的顶部
            Context context = callback.getContext();
            if (context != null && targetPosition >= 0) {
                int offset = DisplayUtil.dpToPx(context, 8);
                listView.setSelectionFromTop(targetPosition, offset);
            }
        }
    }
    
    
    /**
     * 保存折叠状态到 SharedPreferences
     * 从 MainActivity.saveFoldedNoteIds() 迁移
     */
    private void saveFoldedNoteIds() {
        if (callback == null) {
            return;
        }
        
        String currentProject = callback.getProjectManager().getCurrentProject();
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        SharedPreferences prefs = context.getSharedPreferences("noteplus_fold_state", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = "folded_note_ids_" + currentProject;
        
        Set<String> foldedIdsStr = new HashSet<>();
        for (Long id : foldedNoteIds) {
            foldedIdsStr.add(String.valueOf(id));
        }
        
        editor.putStringSet(key, foldedIdsStr);
        editor.apply();
    }
    
    /**
     * 显示添加追加内容对话框
     * 从 MainActivity.showAddCommentDialog() 迁移
     */
    /**
     * 显示添加追加内容对话框
     * 从 MainActivity.showAddCommentDialog() 迁移
     */
    public void showAddCommentDialog(long noteId, Long parentCommentId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return;
        }
        
        showAddCommentDialog(context, noteId, parentCommentId, dbHelper);
    }
    
    /**
     * 显示添加追加内容对话框（重载方法，可供外部调用）
     * @param context Android Context
     * @param noteId 笔记ID
     * @param parentCommentId 父评论ID（null表示新建评论）
     * @param dbHelper 数据库Helper
     */
    public void showAddCommentDialog(Context context, long noteId, Long parentCommentId, NoteDbHelper dbHelper) {
        showAddCommentDialog(context, noteId, parentCommentId, dbHelper, null);
    }
    
    /**
     * 显示添加追加内容对话框（重载方法，可供外部调用）
     * @param context Android Context
     * @param noteId 笔记ID
     * @param parentCommentId 父评论ID（null表示新建评论）
     * @param dbHelper 数据库Helper
     * @param onCommentAdded 评论添加成功的回调，null表示不刷新
     */
    public void showAddCommentDialog(Context context, long noteId, Long parentCommentId, NoteDbHelper dbHelper, Runnable onCommentAdded) {
        if (context == null || dbHelper == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(parentCommentId == null ? "追加内容" : "回复追加");
        
        // 创建输入视图
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = DisplayUtil.dpToPx(context, 16);
        layout.setPadding(padding, padding, padding, padding);
        
        EditText contentEdit = new EditText(context);
        contentEdit.setHint("输入追加内容...");
        contentEdit.setMinLines(3);
        contentEdit.setMaxLines(5);
        contentEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(contentEdit);
        
        // 可选：花费输入
        EditText costEdit = new EditText(context);
        costEdit.setHint("花费金额（可选）");
        costEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(costEdit);
        
        builder.setView(layout);
        
        builder.setPositiveButton("添加", (dialog, which) -> {
            String content = contentEdit.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show();
                return;
            }
            
            double cost = 0.0;
            String costText = costEdit.getText().toString().trim();
            if (!costText.isEmpty()) {
                try {
                    cost = Double.parseDouble(costText);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "花费金额格式不正确", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 保存追加内容
            long commentId = dbHelper.addComment(noteId, parentCommentId, content, cost);
            if (commentId != -1) {
                Toast.makeText(context, "追加成功", Toast.LENGTH_SHORT).show();
                // 回调刷新UI
                if (onCommentAdded != null) {
                    onCommentAdded.run();
                }
            } else {
                Toast.makeText(context, "追加失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 显示评论选项菜单
     * 从 MainActivity.showCommentOptionsMenu() 迁移
     */
    public void showCommentOptionsMenu(long commentId, long noteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择操作");
        
        String[] options = {"复制到剪切板", "删除"};
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 复制到剪切板
                    copyCommentToClipboard(commentId);
                    break;
                case 1: // 删除
                    showDeleteCommentDialog(commentId, noteId);
                    break;
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 设置对话框居中显示
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
        }
    }
    
    /**
     * 复制评论内容到剪切板
     * 从 MainActivity.copyCommentToClipboard() 迁移
     */
    private void copyCommentToClipboard(long commentId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        // 从数据库获取评论内容
        String commentContent = dbHelper.getCommentContentById(commentId);
        
        if (commentContent != null && !commentContent.trim().isEmpty()) {
            // 获取剪切板管理器
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("追加内容", commentContent);
            clipboard.setPrimaryClip(clip);
            
            // 显示成功提示
            Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } else {
            // 显示错误提示
            Toast.makeText(context, "无法获取追加内容", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示删除评论对话框
     * 从 MainActivity.showDeleteCommentDialog() 迁移
     */
    public void showDeleteCommentDialog(long commentId, long noteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (context == null || dbHelper == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("删除追加内容");
        builder.setMessage("确定要删除这条追加内容吗？");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            int result = dbHelper.deleteComment(commentId);
            if (result > 0) {
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show();
                refreshNoteView(noteId);
            } else {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 显示笔记选项菜单
     * 从 MainActivity.showNoteOptionsMenu() 迁移
     */
    public void showNoteOptionsMenu(View anchorView, long noteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (context == null || dbHelper == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择操作");
        
        // 检查当前置顶状态
        boolean isPinned = dbHelper.isNotePinned(noteId);
        String[] options = {"复制到剪切板", "编辑", "追加内容", isPinned ? "取消置顶" : "置顶", "转为评论", "移动到...", "归档", "删除"};
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 复制到剪切板
                    copyToClipboard(noteId);
                    break;
                case 1: // 编辑
                    showEditNoteDialog(noteId);
                    break;
                case 2: // 添加追加（新增）
                    showAddCommentDialog(noteId, null);
                    break;
                case 3: // 置顶/取消置顶
                    togglePinNote(noteId);
                    break;
                case 4: // 合并到...
                    showMergeToDialog(noteId);
                    break;
                case 5: // 移动到...
                    if (callback != null) {
                        callback.onRequestMoveToProject(noteId);
                    }
                    break;
                case 6: // 归档
                    showArchiveConfirmDialog(noteId);
                    break;
                case 7: // 删除
                    showDeleteConfirmDialog(noteId);
                    break;
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 设置对话框居中显示
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
        }
    }

    /**
     * 显示归档确认对话框
     * @param noteId 笔记ID
     */
    private void showArchiveConfirmDialog(long noteId) {
        if (callback == null) {
            return;
        }

        Context context = callback.getContext();
        if (context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("归档记录");
        builder.setMessage("确定要归档这条记录吗？归档后可在设置-已归档中查看。");

        builder.setPositiveButton("归档", (dialog, which) -> archiveNote(noteId));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 归档笔记
     * @param noteId 笔记ID
     */
    private void archiveNote(long noteId) {
        if (callback == null) {
            return;
        }

        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }

        boolean success = dbHelper.archiveNote(noteId);
        if (success) {
            Toast.makeText(context, "已归档", Toast.LENGTH_SHORT).show();
            Set<Long> archivedIds = new HashSet<>();
            archivedIds.add(noteId);
            hideNotes(archivedIds);
        } else {
            Toast.makeText(context, "归档失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示合并到目标Note的对话框
     * 列出最近一周内的Note供选择
     * 使用类似Timeline的样式
     * @param sourceNoteId 源笔记ID
     */
    private void showMergeToDialog(long sourceNoteId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        // 使用现有的getNearNotes方法获取一周内的Note列表
        List<Note> nearNotes = getNearNotes(sourceNoteId, TimeRangeFilter.LAST_WEEK);
        
        // 过滤掉源Note本身
        List<Note> targetNotes = new ArrayList<>();
        for (Note note : nearNotes) {
            if (note.getId() != sourceNoteId) {
                targetNotes.add(note);
            }
        }
        
        if (targetNotes.isEmpty()) {
            Toast.makeText(context, "一周内没有其他笔记可合并", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 创建自定义对话框（使用Timeline样式）
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_timeline, null);
        builder.setView(dialogView);
        
        // 获取对话框中的视图组件
        ListView noteListView = dialogView.findViewById(R.id.timelineListView);
        TextView itemCountText = dialogView.findViewById(R.id.timelineItemCount);
        android.widget.Button closeButton = dialogView.findViewById(R.id.btnCloseTimeline);
        
        // 更新标题和数量
        itemCountText.setText(targetNotes.size() + " 项");
        
        // 创建对话框
        AlertDialog dialog = builder.create();
        
        // 设置关闭按钮点击事件
        closeButton.setOnClickListener(v -> dialog.dismiss());
        
        // 创建适配器并设置点击监听器
        MergeTargetAdapter mergeAdapter = new MergeTargetAdapter(context, targetNotes);
        mergeAdapter.setOnItemClickListener((position) -> {
            Note targetNote = targetNotes.get(position);
            // 同时获取源Note（从NoteListAdapter中）
            Note sourceNote = null;
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    Note note = adapter.getItem(i);
                    if (note != null && note.getId() == sourceNoteId) {
                        sourceNote = note;
                        break;
                    }
                }
            }
            dialog.dismiss();
            showMergeConfirmDialog(sourceNote, targetNote);
        });
        noteListView.setAdapter(mergeAdapter);
        
        // 显示对话框
        dialog.show();
        
        // 设置对话框窗口大小
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
    }
    
    /**
     * 合并目标选择列表项点击监听器接口
     */
    private interface MergeTargetItemClickListener {
        void onItemClick(int position);
    }
    
    /**
     * 合并目标选择列表适配器
     * 使用Timeline样式显示Note
     */
    private class MergeTargetAdapter extends BaseAdapter {
        private final Context context;
        private final List<Note> notes;
        private MergeTargetItemClickListener onItemClickListener;
        
        public void setOnItemClickListener(MergeTargetItemClickListener listener) {
            this.onItemClickListener = listener;
        }
        
        public MergeTargetAdapter(Context context, List<Note> notes) {
            this.context = context;
            this.notes = notes;
        }
        
        @Override
        public int getCount() {
            return notes.size();
        }
        
        @Override
        public Note getItem(int position) {
            return notes.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Note note = getItem(position);
            
            View view = convertView;
            if (view == null || view.findViewById(R.id.timelineProjectName) == null) {
                view = LayoutInflater.from(context).inflate(R.layout.item_timeline, parent, false);
            }
            
            // 获取视图组件
            TextView projectNameText = view.findViewById(R.id.timelineProjectName);
            TextView itemTypeText = view.findViewById(R.id.timelineItemType);
            TextView timeText = view.findViewById(R.id.timelineTime);
            TextView contentText = view.findViewById(R.id.timelineContent);
            LinearLayout tagsContainer = view.findViewById(R.id.timelineTagsContainer);
            
            // 设置项目名称
            String projectName = note.getProjectName();
            if (projectName == null || projectName.isEmpty()) {
                projectName = callback != null && callback.getProjectManager() != null 
                    ? callback.getProjectManager().getCurrentProject() 
                    : "默认项目";
            }
            projectNameText.setText(projectName);
            
            // 设置类型标签（都是Note）
            itemTypeText.setText("Note");
            itemTypeText.setBackgroundColor(Color.parseColor("#2196F3")); // 蓝色
            
            // 设置时间
            timeText.setText(DisplayUtil.formatTimestamp(note.getTimestamp()));
            
            // 设置内容预览（最多200字符）
            String content = note.getContent();
            if (content == null) {
                content = "";
            }
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            contentText.setText(content);
            
            // 加载标签
            tagsContainer.removeAllViews();
            if (callback != null && callback.getDbHelper() != null) {
                NoteDbHelper dbHelper = callback.getDbHelper();
                Cursor tagsCursor = null;
                try {
                    tagsCursor = dbHelper.getTagsForNote(note.getId());
                    if (tagsCursor != null && tagsCursor.getCount() > 0) {
                        tagsContainer.setVisibility(View.VISIBLE);
                        while (tagsCursor.moveToNext()) {
                            @SuppressLint("Range") String tagName = tagsCursor.getString(
                                tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                            @SuppressLint("Range") String tagColor = tagsCursor.getString(
                                tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                            
                            // 创建标签 TextView
                            TextView tagView = new TextView(context);
                            tagView.setText(tagName);
                            tagView.setPadding(
                                DisplayUtil.dpToPx(context, 6), 
                                DisplayUtil.dpToPx(context, 2), 
                                DisplayUtil.dpToPx(context, 6), 
                                DisplayUtil.dpToPx(context, 2)
                            );
                            tagView.setTextColor(Color.WHITE);
                            tagView.setTextSize(11);
                            
                            try {
                                tagView.setBackgroundColor(Color.parseColor(tagColor));
                            } catch (Exception e) {
                                tagView.setBackgroundColor(Color.GRAY);
                            }
                            
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                            params.setMargins(0, 0, DisplayUtil.dpToPx(context, 4), 0);
                            tagView.setLayoutParams(params);
                            
                            tagsContainer.addView(tagView);
                        }
                    } else {
                        tagsContainer.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    tagsContainer.setVisibility(View.GONE);
                    e.printStackTrace();
                } finally {
                    if (tagsCursor != null) {
                        tagsCursor.close();
                    }
                }
            } else {
                tagsContainer.setVisibility(View.GONE);
            }
            
            // 设置点击事件
            view.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(position);
                }
            });
            
            return view;
        }
    }
    
    /**
     * 显示合并确认对话框
     * @param sourceNote 源笔记对象
     * @param targetNote 目标笔记对象
     */
    private void showMergeConfirmDialog(Note sourceNote, Note targetNote) {
        if (callback == null || sourceNote == null || targetNote == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("确认合并");
        builder.setMessage("确定要将当前笔记及其所有追加内容合并到目标笔记吗？此操作不可恢复。");
        
        builder.setPositiveButton("确认合并", (dialog, which) -> {
            performMerge(sourceNote, targetNote);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 执行合并操作
     * 将源Note及其所有评论合并到目标Note中
     * @param sourceNote 源笔记对象（已从adapter获取，只需加载评论）
     * @param targetNote 目标笔记对象（已从adapter获取，只需加载评论）
     */
    private void performMerge(Note sourceNote, Note targetNote) {
        if (callback == null || sourceNote == null || targetNote == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }
        
        try {
            // 1. 加载源Note的评论（Note基本信息已从adapter获取）
            loadCommentsForNote(sourceNote, dbHelper);
            
            // 2. 加载目标Note的评论（Note基本信息已从adapter获取）
            loadCommentsForNote(targetNote, dbHelper);
            
            // 3. 使用NoteDbHelper封装的方法执行合并（包含事务管理）
            boolean success = dbHelper.mergeNotes(sourceNote, targetNote);
            
            if (success) {
                Toast.makeText(context, "合并成功", Toast.LENGTH_SHORT).show();
                
                // 保存目标笔记ID，用于后续滚动
                long targetNoteId = targetNote.getId();
                
                // 刷新列表
                refreshNotes();
                
                // 在列表刷新后，滚动到目标笔记位置
                // 使用post延迟执行，确保列表已经刷新完成
                if (listView != null) {
                    listView.post(() -> {
                        // 再次延迟一点，确保adapter已经更新
                        listView.postDelayed(() -> {
                            scrollToNote(targetNoteId);
                        }, 100);
                    });
                }
            } else {
                Toast.makeText(context, "合并失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "合并失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 为已有的Note对象加载评论数据
     * @param note Note对象（基本信息已存在，只需加载评论）
     * @param dbHelper 数据库Helper
     */
    private void loadCommentsForNote(Note note, NoteDbHelper dbHelper) {
        if (note == null || dbHelper == null) {
            return;
        }
        
        // 确保comments列表已初始化
        if (note.getComments() == null) {
            note.setComments(new ArrayList<>());
        } else {
            // 清空已有评论（避免重复）
            note.getComments().clear();
        }
        
        String projectName = note.getProjectName();
        if (projectName == null && callback != null) {
            projectName = callback.getProjectManager().getCurrentProject();
        }
        
        // 加载评论（使用现有的getCommentsForNote方法）
        Cursor commentsCursor = dbHelper.getCommentsForNote(note.getId());
        if (commentsCursor != null && commentsCursor.getCount() > 0) {
            while (commentsCursor.moveToNext()) {
                @SuppressLint("Range") long commentId = commentsCursor.getLong(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID));
                @SuppressLint("Range") int parentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                @SuppressLint("Range") String content = commentsCursor.getString(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
                @SuppressLint("Range") long timestamp = commentsCursor.getLong(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP));
                @SuppressLint("Range") double cost = commentsCursor.getDouble(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST));
                
                Long parentId = null;
                if (!commentsCursor.isNull(parentIdIndex)) {
                    parentId = commentsCursor.getLong(parentIdIndex);
                }
                
                Comment comment = new Comment(
                    commentId,
                    note.getId(),
                    parentId,
                    content,
                    timestamp,
                    cost,
                    projectName
                );
                note.addComment(comment);
            }
            commentsCursor.close();
        }
    }
    
    /**
     * 笔记列表适配器
     * 从 MainActivity 中移过来的内部类
     */
    private class NoteListAdapter extends BaseAdapter {
        private final NoteCursorWrapper wrapper;
        private final List<Integer> visiblePositions = new ArrayList<>();
        private boolean visiblePositionsDirty = true;
        
        public NoteListAdapter(NoteCursorWrapper wrapper) {
            this.wrapper = wrapper;
        }

        private void rebuildVisiblePositions() {
            visiblePositions.clear();
            if (wrapper == null) {
                visiblePositionsDirty = false;
                return;
            }

            int total = wrapper.getCount();
            for (int i = 0; i < total; i++) {
                long noteId = wrapper.getNoteIdAtPosition(i);
                if (!hiddenNoteIds.contains(noteId)) {
                    visiblePositions.add(i);
                }
            }
            visiblePositionsDirty = false;
        }

        public void markVisiblePositionsDirty() {
            visiblePositionsDirty = true;
        }
        
        @Override
        public int getCount() {
            if (visiblePositionsDirty) {
                rebuildVisiblePositions();
            }
            return visiblePositions.size();
        }
        
        @Override
        public Note getItem(int position) {
            if (wrapper == null) {
                return null;
            }
            if (visiblePositionsDirty) {
                rebuildVisiblePositions();
            }
            if (position < 0 || position >= visiblePositions.size()) {
                return null;
            }
            int cursorPosition = visiblePositions.get(position);
            return wrapper.getNote(cursorPosition);
        }
        
        @Override
        public long getItemId(int position) {
            Note note = getItem(position);
            return note != null ? note.getId() : position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // 获取 Note 对象（首次访问时才创建）
            Note note = getItem(position);
            if (note == null) {
                return convertView;
            }
            
            long noteId = note.getId();
            Context context = callback != null ? callback.getContext() : null;
            if (context == null) {
                return convertView;
            }
            
            LayoutInflater inflater = LayoutInflater.from(context);
            
            // 使用 Note 对象填充视图
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.note_list_item, parent, false);
            }
            
            // 设置tag，用于后续定位View
            convertView.setTag(noteId);

            // 点击列表项空白区域时弹出笔记菜单（图片区域有自己的点击事件）
            convertView.setOnClickListener(v -> {
                if (isMultiSelectMode) {
                    CheckBox checkBox = v.findViewById(R.id.checkBox);
                    if (checkBox != null) {
                        checkBox.setChecked(!checkBox.isChecked());
                        if (checkBox.isChecked()) {
                            selectedNoteIds.add(noteId);
                        } else {
                            selectedNoteIds.remove(noteId);
                        }
                        if (callback != null) {
                            callback.onMultiSelectChanged(selectedNoteIds);
                            callback.onRequestRefreshMenu();
                        }
                    }
                } else {
                    showNoteOptionsMenu(v, noteId);
                }
            });

            // 长按列表项弹出笔记菜单（图片区域有自己的长按菜单）
            convertView.setOnLongClickListener(v -> {
                if (isMultiSelectMode) {
                    return false;
                }
                showNoteOptionsMenu(v, noteId);
                return true;
            });
            
            // 获取基础视图组件
            TextView contentText = convertView.findViewById(R.id.contentText);
            TextView timestampText = convertView.findViewById(R.id.timestampText);
            
            // 设置内容
            contentText.setText(note.getContent());
            timestampText.setText(DisplayUtil.formatTimestamp(note.getTimestamp()));
            
            // 获取花费金额和置顶状态
            double cost = note.getCost();
            boolean isPinned = note.isPinned();
            
            // 为列表项添加时间区间和标签信息
            updateListItemWithExtras(convertView, noteId, cost);
            
            // 如果置顶，在内容前添加标识（在updateListItemWithExtras之后，确保内容已设置）
            if (isPinned) {
                if (contentText != null) {
                    String currentText = contentText.getText().toString();
                    // 检查是否已经包含置顶标识，避免重复添加
                    if (!currentText.startsWith("📌 ")) {
                        contentText.setText("📌 " + currentText);
                    }
                }
            }
            
            // 判断是否显示折叠按钮（传入置顶状态）
            checkAndShowFoldButton(convertView, noteId, note.getContent(), isPinned);
            
            // 处理多选模式下的复选框
            CheckBox checkBox = convertView.findViewById(R.id.checkBox);
            if (checkBox != null) {
                if (isMultiSelectMode) {
                    checkBox.setVisibility(View.VISIBLE);
                    checkBox.setChecked(selectedNoteIds.contains(noteId));
                    
                    // 设置复选框点击事件
                    checkBox.setOnClickListener(v -> {
                        if (checkBox.isChecked()) {
                            selectedNoteIds.add(noteId);
                        } else {
                            selectedNoteIds.remove(noteId);
                        }
                        if (callback != null) {
                            callback.onMultiSelectChanged(selectedNoteIds);
                            callback.onRequestRefreshMenu();
                        }
                    });
                } else {
                    checkBox.setVisibility(View.GONE);
                }
            }
            
            return convertView;
        }
    }

    /**
     * 显示笔记标签编辑对话框
     * @param noteId 笔记ID
     */
    public void showNoteTagDialog(long noteId) {
        if (callback == null) {
            return;
        }

        NoteDbHelper dbHelper = callback.getDbHelper();
        Context context = callback.getContext();
        if (dbHelper == null || context == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_note_tag_edit, null);
        builder.setView(dialogView);
        builder.setTitle("编辑标签");

        // 获取容器
        LinearLayout currentTagsContainer = dialogView.findViewById(R.id.currentTagsContainer);
        LinearLayout allTagsContainer = dialogView.findViewById(R.id.allTagsContainer);
        EditText editNewTagName = dialogView.findViewById(R.id.editNewTagName);
        Button buttonCreateTag = dialogView.findViewById(R.id.buttonCreateTag);

        // 加载当前笔记的标签
        Cursor currentTagsCursor = dbHelper.getTagsForNote(noteId);
        List<Long> currentTagIds = new ArrayList<>();
        if (currentTagsCursor != null && currentTagsCursor.getCount() > 0) {
            while (currentTagsCursor.moveToNext()) {
                @SuppressLint("Range") long tagId = currentTagsCursor.getLong(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_ID));
                @SuppressLint("Range") String tagName = currentTagsCursor.getString(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                @SuppressLint("Range") String tagColor = currentTagsCursor.getString(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                currentTagIds.add(tagId);

                // 创建标签视图（带删除按钮）
                LinearLayout tagLayout = new LinearLayout(context);
                tagLayout.setOrientation(LinearLayout.HORIZONTAL);
                tagLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView tagView = new TextView(context);
                tagView.setText(tagName);
                tagView.setPadding(DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4), DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4));
                tagView.setTextColor(Color.WHITE);
                tagView.setTextSize(12);
                try {
                    tagView.setBackgroundColor(Color.parseColor(tagColor));
                } catch (Exception e) {
                    tagView.setBackgroundColor(Color.GRAY);
                }

                TextView removeBtn = new TextView(context);
                removeBtn.setText(" ×");
                removeBtn.setTextColor(Color.RED);
                removeBtn.setTextSize(16);
                removeBtn.setOnClickListener(v -> {
                    dbHelper.unlinkNoteFromTag(noteId, tagId);
                    refreshNoteView(noteId);
                    // 刷新对话框
                    builder.create().dismiss();
                    showNoteTagDialog(noteId);
                });

                tagLayout.addView(tagView);
                tagLayout.addView(removeBtn);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 8));
                tagLayout.setLayoutParams(params);

                currentTagsContainer.addView(tagLayout);
            }
            currentTagsCursor.close();
        }

        // 加载所有可用标签（排除已添加的）
        Cursor allTagsCursor = dbHelper.getAllTags();
        if (allTagsCursor != null && allTagsCursor.getCount() > 0) {
            while (allTagsCursor.moveToNext()) {
                @SuppressLint("Range") long tagId = allTagsCursor.getLong(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_ID));
                // 跳过已添加的标签
                if (currentTagIds.contains(tagId)) {
                    continue;
                }
                @SuppressLint("Range") String tagName = allTagsCursor.getString(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                @SuppressLint("Range") String tagColor = allTagsCursor.getString(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));

                TextView tagView = new TextView(context);
                tagView.setText("+ " + tagName);
                tagView.setPadding(DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4), DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4));
                try {
                    tagView.setTextColor(Color.parseColor(tagColor));
                } catch (Exception e) {
                    tagView.setTextColor(Color.GRAY);
                }
                tagView.setTextSize(12);
                tagView.setBackgroundResource(android.R.drawable.edit_text);
                tagView.setOnClickListener(v -> {
                    dbHelper.linkNoteToTag(noteId, tagId);
                    refreshNoteView(noteId);
                    // 刷新对话框
                    builder.create().dismiss();
                    showNoteTagDialog(noteId);
                });

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 8));
                tagView.setLayoutParams(params);

                allTagsContainer.addView(tagView);
            }
            allTagsCursor.close();
        }

        // 创建按钮点击事件
        buttonCreateTag.setOnClickListener(v -> {
            String newTagName = editNewTagName.getText().toString().trim();
            if (!newTagName.isEmpty()) {
                long newTagId = dbHelper.addTag(newTagName, "#2196F3");
                if (newTagId > 0) {
                    dbHelper.linkNoteToTag(noteId, newTagId);
                    refreshNoteView(noteId);
                    builder.create().dismiss();
                    showNoteTagDialog(noteId);
                }
            }
        });

        builder.setNegativeButton("关闭", null);
        builder.show();
    }
}

