package person.notfresh.noteplus.manager;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import androidx.appcompat.app.AlertDialog;

import person.notfresh.noteplus.R;
import person.notfresh.noteplus.core.model.Note;
import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.manager.INoteListCallback;
import person.notfresh.noteplus.util.DisplayUtil;
import person.notfresh.noteplus.util.NoteCursorWrapper;
import person.notfresh.noteplus.util.StringUtil;

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
    private boolean isMultiSelectMode = false;
    private Set<Long> selectedNoteIds = new HashSet<>();
    
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
        
        // 先关闭之前的 Cursor 包装器（如果存在）
        if (noteCursorWrapper != null) {
            noteCursorWrapper.close();
            noteCursorWrapper = null;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return;
        }
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // 根据设置决定排序方式，置顶的记录排在最前面
        boolean timeDescOrder = callback.getTimeDescOrder();
        String orderBy = NoteDbHelper.COLUMN_IS_PINNED + " DESC, " + 
                (timeDescOrder ? 
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC" : 
                NoteDbHelper.COLUMN_TIMESTAMP + " ASC");
        
        Cursor cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST, NoteDbHelper.COLUMN_IS_PINNED},
                null, null, null, null,
                orderBy
        );

        // 创建 Cursor 包装器
        String currentProject = callback.getProjectManager().getCurrentProject();
        noteCursorWrapper = new NoteCursorWrapper(cursor, currentProject);
        
        // 创建适配器
        adapter = new NoteListAdapter(noteCursorWrapper);
        
        // 设置适配器
        listView.setAdapter(adapter);
        
        // 添加点击监听器
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (isMultiSelectMode) {
                // 多选模式下，点击切换选择状态
                Note note = (Note) adapter.getItem(position);
                long noteId = note.getId();
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
     * 删除笔记
     * 从 MainActivity.deleteNote() 迁移
     * @param noteId 笔记ID
     */
    public void deleteNote(long noteId) {
        if (callback == null) {
            return;
        }
        
        NoteDbHelper dbHelper = callback.getDbHelper();
        if (dbHelper == null) {
            return;
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // 开始事务
        db.beginTransaction();
        try {
            // 1. 删除相关的标签关联
            db.delete(
                NoteDbHelper.TABLE_NOTE_TAGS,
                NoteDbHelper.COLUMN_RECORD_ID + " = ?",
                new String[]{String.valueOf(noteId)}
            );
            
            // 2. 删除相关的时间范围
            db.delete(
                NoteDbHelper.TABLE_TIME_RANGES,
                NoteDbHelper.COLUMN_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)}
            );
            
            // 3. 删除记录本身
            int rowsDeleted = db.delete(
                NoteDbHelper.TABLE_NOTES,
                "_id = ?",
                new String[]{String.valueOf(noteId)}
            );
            
            // 设置事务成功
            db.setTransactionSuccessful();
            
            // 显示删除成功提示
            Context context = callback.getContext();
            if (rowsDeleted > 0 && context != null) {
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
                String orderBy = NoteDbHelper.COLUMN_IS_PINNED + " DESC, " + 
                        (timeDescOrder ? 
                        NoteDbHelper.COLUMN_TIMESTAMP + " DESC" : 
                        NoteDbHelper.COLUMN_TIMESTAMP + " ASC");
                
                Cursor newCursor = db.query(
                        NoteDbHelper.TABLE_NOTES,
                        new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST, NoteDbHelper.COLUMN_IS_PINNED},
                        null, null, null, null,
                        orderBy
                );
                
                // 更新 wrapper 的 Cursor（会自动清空缓存）
                if (noteCursorWrapper != null) {
                    noteCursorWrapper.setCursor(newCursor);
                    // 刷新适配器，ListView 会自动移除不存在的项，下面的项会自动浮上来
                    if (adapter != null) {
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
        } finally {
            // 结束事务
            db.endTransaction();
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
        
        // 添加追加内容信息
        addCommentsInfo(extrasContainer, noteId);
        
        // 如果配置显示花费且花费大于0，则在记录旁边显示花费
        boolean showCost = callback != null ? callback.getShowCost() : false;
        if (showCost && cost > 0) {
            // 获取内容文本视图
            TextView contentText = view.findViewById(R.id.contentText);
            String currentText = contentText.getText().toString();
            
            // 在文本后面添加花费信息
            String costText = String.format(" [¥%.2f]", cost);
            contentText.setText(currentText + costText);
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
    private void showAddCommentDialog(long noteId, Long parentCommentId) {
        if (callback == null) {
            return;
        }
        
        Context context = callback.getContext();
        if (context == null) {
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
            addComment(noteId, parentCommentId, content, cost);
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
        String[] options = {"复制到剪切板", "追加内容", isPinned ? "取消置顶" : "置顶", "删除"};
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 复制到剪切板
                    copyToClipboard(noteId);
                    break;
                case 1: // 添加追加（新增）
                    showAddCommentDialog(noteId, null);
                    break;
                case 2: // 置顶/取消置顶
                    togglePinNote(noteId);
                    break;
                case 3: // 删除
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
     * 笔记列表适配器
     * 从 MainActivity 中移过来的内部类
     */
    private class NoteListAdapter extends BaseAdapter {
        private final NoteCursorWrapper wrapper;
        
        public NoteListAdapter(NoteCursorWrapper wrapper) {
            this.wrapper = wrapper;
        }
        
        @Override
        public int getCount() {
            return wrapper != null ? wrapper.getCount() : 0;
        }
        
        @Override
        public Note getItem(int position) {
            return wrapper != null ? wrapper.getNote(position) : null;
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
}

