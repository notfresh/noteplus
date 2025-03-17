package person.notfresh.noteplus;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.model.Tag;

public class MainActivity extends AppCompatActivity {
    private EditText momentEditText;
    private Button saveButton;
    private ListView momentsListView;
    private NoteDbHelper dbHelper;
    private SimpleCursorAdapter adapter;

    // 新增视图引用
    private TextView startTimeText;
    private TextView endTimeText;
    private Button addTagButton;
    private ChipGroup tagChipGroup;
    
    // 新增数据状态
    private List<Tag> selectedTags = new ArrayList<>();
    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private boolean hasTimeRange = false;

    // 添加dialog作为成员变量
    private AlertDialog tagSelectionDialog;

    // 添加到类成员变量区域
    private TimePickerDialog startTimeDialog;
    private TimePickerDialog endTimeDialog;
    private SimpleDateFormat timeFormat;

    // 添加项目管理器
    private ProjectContextManager projectManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置工具栏 - 移到前面
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化项目管理器
        projectManager = new ProjectContextManager(this);
        
        // 获取当前项目的数据库Helper
        dbHelper = projectManager.getCurrentDbHelper();
        
        // 更新标题栏显示当前项目 - 在设置ActionBar之后执行
        updateTitle();

        // 初始化视图
        momentEditText = findViewById(R.id.momentEditText);
        saveButton = findViewById(R.id.saveButton);
        momentsListView = findViewById(R.id.momentsListView);
        
        // 初始化时间区间和标签视图
        startTimeText = findViewById(R.id.startTimeText);
        endTimeText = findViewById(R.id.endTimeText);
        tagChipGroup = findViewById(R.id.tagChipGroup);
        addTagButton = findViewById(R.id.addTagButton);
        
        // 初始化日期格式化器
        timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
        
        // 预初始化时间选择器
        initTimeDialogs();
        
        // 设置时间选择器点击事件
        startTimeText.setOnClickListener(v -> showTimePicker(true));
        endTimeText.setOnClickListener(v -> showTimePicker(false));
        
        // 设置添加标签按钮点击事件
        addTagButton.setOnClickListener(v -> showTagSelectionDialog());

        // 加载现有记录
        loadMoments();

        // 设置保存按钮点击监听器
        saveButton.setOnClickListener(v -> saveMoment());

        // 设置输入框的回车键监听
        momentEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveMoment();
                return true;
            }
            return false;
        });
    }

    /**
     * 预初始化时间选择器对话框
     */
    private void initTimeDialogs() {
        // 创建开始时间选择器
        startTimeDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    startCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    startCalendar.set(Calendar.MINUTE, minute);
                    startTimeText.setText(timeFormat.format(startCalendar.getTime()));
                    hasTimeRange = true;
                },
                startCalendar.get(Calendar.HOUR_OF_DAY),
                startCalendar.get(Calendar.MINUTE),
                true);
        
        // 创建结束时间选择器
        endTimeDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    endCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    endCalendar.set(Calendar.MINUTE, minute);
                    endTimeText.setText(timeFormat.format(endCalendar.getTime()));
                    hasTimeRange = true;
                },
                endCalendar.get(Calendar.HOUR_OF_DAY),
                endCalendar.get(Calendar.MINUTE),
                true);
    }

    /**
     * 显示预创建的时间选择器
     */
    private void showTimePicker(boolean isStartTime) {
        TimePickerDialog dialog = isStartTime ? startTimeDialog : endTimeDialog;
        Calendar calendar = isStartTime ? startCalendar : endCalendar;
        
        // 更新时间选择器的当前值
        dialog.updateTime(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
        
        dialog.show();
    }

    /**
     * 显示标签选择对话框
     */
    private void showTagSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tag_selection, null);
        builder.setView(dialogView);
        
        // 获取视图组件
        final ListView listViewTags = dialogView.findViewById(R.id.listViewTags);
        final EditText editTextNewTag = dialogView.findViewById(R.id.editTextNewTag);
        Button buttonCreateTag = dialogView.findViewById(R.id.buttonCreateTag);
        
        // 创建对话框并存储在成员变量中
        tagSelectionDialog = builder.create();
        
        // 获取所有标签
        final Cursor tagsCursor = dbHelper.getAllTags();
        
        // 简化适配器代码，避免使用bindView
        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this, 
                R.layout.tag_list_item, 
                tagsCursor,
                new String[]{NoteDbHelper.COLUMN_TAG_NAME},
                new int[]{R.id.tagNameText},
                0);
        
        // 使用单独的ViewBinder来处理颜色视图
        adapter.setViewBinder((view, cursor, columnIndex) -> {
            // 只处理tagNameText的绑定，颜色视图单独处理
            if (view.getId() == R.id.tagNameText) {
                String tagName = cursor.getString(columnIndex);
                ((TextView) view).setText(tagName);
                return true;
            }
            return false;
        });
        
        // 设置适配器
        listViewTags.setAdapter(adapter);
        
        // 在适配器设置后，遍历所有列表项单独设置颜色
        listViewTags.post(() -> {
            for (int i = 0; i < adapter.getCount(); i++) {
                View itemView = adapter.getView(i, null, listViewTags);
                if (itemView != null) {
                    View colorView = itemView.findViewById(R.id.tagColorView);
                    if (colorView != null) {
                        tagsCursor.moveToPosition(i);
                        String colorCode = tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_COLOR));
                        try {
                            colorView.setBackgroundColor(Color.parseColor(colorCode));
                        } catch (Exception e) {
                            colorView.setBackgroundColor(Color.GRAY);
                        }
                    }
                }
            }
        });
        
        // 设置标签点击事件
        listViewTags.setOnItemClickListener((parent, view, position, id) -> {
            tagsCursor.moveToPosition(position);
            long tagId = tagsCursor.getLong(tagsCursor.getColumnIndexOrThrow("_id"));
            String tagName = tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME));
            String tagColor = tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_COLOR));
            
            Tag tag = new Tag(tagId, tagName, tagColor);
            addTagChip(tag);
            tagSelectionDialog.dismiss();
        });
        
        // 处理创建新标签的事件
        buttonCreateTag.setOnClickListener(v -> {
            String tagName = editTextNewTag.getText().toString().trim();
            if (!tagName.isEmpty()) {
                // 生成随机标签颜色
                String[] colors = {"#FF5722", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107", "#607D8B"};
                String randomColor = colors[new Random().nextInt(colors.length)];
                
                // 添加到数据库
                long tagId = dbHelper.addTag(tagName, randomColor);
                
                if (tagId != -1) {
                    Tag newTag = new Tag(tagId, tagName, randomColor);
                    addTagChip(newTag);
                    tagSelectionDialog.dismiss();
                } else {
                    Toast.makeText(this, "创建标签失败，可能已存在同名标签", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请输入标签名称", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 显示对话框
        tagSelectionDialog.show();
    }
    
    /**
     * 添加标签芯片到UI
     */
    private void addTagChip(Tag tag) {
        // 检查是否已添加该标签
        for (Tag existingTag : selectedTags) {
            if (existingTag.getId() == tag.getId()) {
                return; // 已存在，不重复添加
            }
        }
        
        selectedTags.add(tag);
        
        // 创建芯片控件
        Chip chip = new Chip(this);
        chip.setText(tag.getName());
        chip.setCloseIconVisible(true);
        
        try {
            int color = Color.parseColor(tag.getColor());
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(color));
            // 根据背景颜色亮度选择文本颜色
            boolean isDarkColor = isDarkColor(color);
            chip.setTextColor(isDarkColor ? Color.WHITE : Color.BLACK);
        } catch (Exception e) {
            // 解析颜色失败时使用默认颜色
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.LTGRAY));
            chip.setTextColor(Color.BLACK);
        }
        
        // 设置关闭图标点击事件
        chip.setOnCloseIconClickListener(v -> {
            tagChipGroup.removeView(chip);
            selectedTags.remove(tag);
        });
        
        tagChipGroup.addView(chip);
    }
    
    /**
     * 判断颜色是否为深色
     */
    private boolean isDarkColor(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    /**
     * 保存当前输入的时刻记录
     */
    private void saveMoment() {
        String content = momentEditText.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查时间区间的有效性
        if (hasTimeRange) {
            if (startCalendar.getTimeInMillis() >= endCalendar.getTimeInMillis()) {
                Toast.makeText(this, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // 获取当前时间
        long timestamp = System.currentTimeMillis();

        // 保存到数据库
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(NoteDbHelper.COLUMN_CONTENT, content);
        values.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);

        long newRowId = db.insert(NoteDbHelper.TABLE_NOTES, null, values);

        if (newRowId != -1) {
            // 保存时间区间(如果有)
            if (hasTimeRange) {
                dbHelper.saveTimeRange(newRowId, startCalendar.getTimeInMillis(), endCalendar.getTimeInMillis());
            }
            
            // 保存标签关联
            for (Tag tag : selectedTags) {
                dbHelper.linkNoteToTag(newRowId, tag.getId());
            }
            
            Toast.makeText(this, "记录已保存", Toast.LENGTH_SHORT).show();
            clearForm(); // 清空表单
            loadMoments(); // 重新加载列表
        } else {
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 清空表单
     */
    private void clearForm() {
        momentEditText.setText("");
        startTimeText.setText("点击选择");
        endTimeText.setText("点击选择");
        startCalendar = Calendar.getInstance();
        endCalendar = Calendar.getInstance();
        hasTimeRange = false;
        tagChipGroup.removeAllViews();
        selectedTags.clear();
    }

    /**
     * 加载所有时刻记录
     */
    private void loadMoments() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{NoteDbHelper.COLUMN_ID, NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP},
                null, null, null, null,
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC" // 按时间戳倒序排列
        );

        String[] fromColumns = {NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_CONTENT};
        int[] toViews = {R.id.timestampText, R.id.contentText};

        adapter = new SimpleCursorAdapter(
                this,
                R.layout.note_list_item,
                cursor,
                fromColumns,
                toViews,
                0
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                
                // 获取当前记录ID
                Cursor cursor = (Cursor) getItem(position);
                long noteId = cursor.getLong(cursor.getColumnIndex(NoteDbHelper.COLUMN_ID));
                
                // 为列表项添加时间区间和标签信息
                updateListItemWithExtras(view, noteId);
                
                return view;
            }
        };

        // 设置时间戳格式
        adapter.setViewBinder((view, cursor1, columnIndex) -> {
            if (columnIndex == cursor1.getColumnIndex(NoteDbHelper.COLUMN_TIMESTAMP)) {
                long timestamp = cursor1.getLong(columnIndex);
                String formattedDate = formatTimestamp(timestamp);
                ((TextView) view).setText(formattedDate);
                return true;
            }
            return false;
        });

        momentsListView.setAdapter(adapter);
    }
    
    /**
     * 为列表项添加时间区间和标签信息
     */
    private void updateListItemWithExtras(View view, long noteId) {
        // 查找或创建额外信息容器
        LinearLayout extrasContainer = view.findViewById(R.id.extrasContainer);
        if (extrasContainer == null) {
            TextView contentText = view.findViewById(R.id.contentText);
            ViewGroup parent = (ViewGroup) contentText.getParent();
            
            extrasContainer = new LinearLayout(this);
            extrasContainer.setId(R.id.extrasContainer);
            extrasContainer.setOrientation(LinearLayout.VERTICAL);
            extrasContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
            parent.addView(extrasContainer, parent.indexOfChild(contentText) + 1);
        }
        
        // 清空现有内容
        extrasContainer.removeAllViews();
        
        // 添加时间区间信息
        addTimeRangeInfo(extrasContainer, noteId);
        
        // 添加标签信息
        addTagsInfo(extrasContainer, noteId);
    }
    
    /**
     * 添加时间区间信息到列表项
     */
    private void addTimeRangeInfo(LinearLayout container, long noteId) {
        Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
        
        if (timeRangeCursor != null && timeRangeCursor.moveToFirst()) {
            long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_START_TIME));
            long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_END_TIME));
            
            LinearLayout timeRangeLayout = new LinearLayout(this);
            timeRangeLayout.setOrientation(LinearLayout.HORIZONTAL);
            timeRangeLayout.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            
            TextView timeRangeLabel = new TextView(this);
            timeRangeLabel.setText("时间区间: ");
            timeRangeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            timeRangeLayout.addView(timeRangeLabel);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            String timeRangeText = sdf.format(new Date(startTime)) + " 至 " + sdf.format(new Date(endTime));
            
            TextView timeRangeValue = new TextView(this);
            timeRangeValue.setText(timeRangeText);
            timeRangeLayout.addView(timeRangeValue);
            
            container.addView(timeRangeLayout);
            
            timeRangeCursor.close();
        }
    }
    
    /**
     * 添加标签信息到列表项
     */
    private void addTagsInfo(LinearLayout container, long noteId) {
        Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
        
        if (tagsCursor != null && tagsCursor.getCount() > 0) {
            LinearLayout tagLayout = new LinearLayout(this);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            
            TextView tagsLabel = new TextView(this);
            tagsLabel.setText("标签: ");
            tagsLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tagLayout.addView(tagsLabel);
            
            LinearLayout tagsContainer = new LinearLayout(this);
            tagsContainer.setOrientation(LinearLayout.HORIZONTAL);
            
            while (tagsCursor.moveToNext()) {
                String tagName = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                String tagColor = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                
                TextView tagView = new TextView(this);
                tagView.setText(tagName);
                tagView.setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2));
                tagView.setTextColor(Color.WHITE);
                
                try {
                    tagView.setBackgroundColor(Color.parseColor(tagColor));
                } catch (Exception e) {
                    tagView.setBackgroundColor(Color.GRAY);
                }
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(dpToPx(4), 0, 0, 0);
                tagView.setLayoutParams(params);
                
                tagsContainer.addView(tagView);
            }
            
            tagLayout.addView(tagsContainer);
            container.addView(tagLayout);
            
            tagsCursor.close();
        }
    }

    /**
     * 格式化时间戳为指定格式
     */
    private String formatTimestamp(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
        String dateStr = dateFormat.format(new Date(timestamp));
        
        // 获取星期
        String[] weekDays = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        if (dayOfWeek < 0) dayOfWeek = 0;
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
        String timeStr = timeFormat.format(new Date(timestamp));
        
        return dateStr + "，星期" + weekDays[dayOfWeek] + "，" + timeStr;
    }

    /**
     * 将dp值转换为像素值
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    /**
     * 更新标题显示当前项目
     */
    private void updateTitle() {
        if (getSupportActionBar() != null) {
            String currentProject = projectManager.getCurrentProject();
            getSupportActionBar().setTitle("时间戳记录 - " + currentProject);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_switch_project) {
            showProjectMenu(findViewById(R.id.action_switch_project));
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 显示项目菜单
     */
    private void showProjectMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        Menu menu = popup.getMenu();
        
        // 添加"新建项目"选项
        menu.add(Menu.NONE, -1, Menu.NONE, "新建项目...");
        
        // 添加现有项目
        List<String> projects = projectManager.getProjectList();
        for (int i = 0; i < projects.size(); i++) {
            String project = projects.get(i);
            menu.add(Menu.NONE, i, Menu.NONE, project);
            
            // 标记当前项目
            if (project.equals(projectManager.getCurrentProject())) {
                MenuItem item = menu.getItem(i + 1); // +1是因为"新建项目"
                item.setTitle("✓ " + project);
            }
        }
        
        // 添加项目管理选项
        menu.add(Menu.NONE, -2, Menu.NONE, "管理项目...");
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == -1) {
                // 创建新项目
                showCreateProjectDialog();
                return true;
            } else if (itemId == -2) {
                // 打开项目管理界面
                showProjectManagementDialog();
                return true;
            }
            
            // 切换到选择的项目
            String selectedProject = projects.get(itemId);
            switchProject(selectedProject);
            return true;
        });
        
        popup.show();
    }
    
    /**
     * 显示项目管理对话框
     */
    private void showProjectManagementDialog() {
        List<String> projects = projectManager.getProjectList();
        String[] items = projects.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("管理项目");
        
        builder.setItems(items, (dialog, which) -> {
            String selectedProject = projects.get(which);
            showProjectOptionsDialog(selectedProject);
        });
        
        builder.setNeutralButton("取消", null);
        
        builder.show();
    }
    
    /**
     * 显示项目操作选项对话框
     */
    private void showProjectOptionsDialog(String projectName) {
        // 默认项目不允许删除
        boolean isDefaultProject = "default".equals(projectName);
        
        String[] options = isDefaultProject ? 
                new String[]{"重命名"} : 
                new String[]{"重命名", "删除"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(projectName);
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 重命名
                showRenameProjectDialog(projectName);
            } else if (which == 1 && !isDefaultProject) {
                // 删除
                showDeleteProjectConfirmation(projectName);
            }
        });
        
        builder.show();
    }
    
    /**
     * 显示重命名项目对话框
     */
    private void showRenameProjectDialog(String oldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名项目");
        
        final EditText input = new EditText(this);
        input.setText(oldName);
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(oldName)) {
                // 实现重命名逻辑
                // 注意：需要在ProjectContextManager中添加重命名方法
                if (projectManager.renameProject(oldName, newName)) {
                    Toast.makeText(this, "项目已重命名", Toast.LENGTH_SHORT).show();
                    updateTitle();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    /**
     * 显示删除项目确认对话框
     */
    private void showDeleteProjectConfirmation(String projectName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除项目");
        builder.setMessage("确定要删除项目 \"" + projectName + "\" 吗？此操作不可恢复。");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            if (projectManager.deleteProject(projectName)) {
                Toast.makeText(this, "项目已删除", Toast.LENGTH_SHORT).show();
                updateTitle();
                clearForm();
                loadMoments();
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    /**
     * 显示创建新项目对话框
     */
    private void showCreateProjectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建新项目");
        
        final EditText input = new EditText(this);
        input.setHint("输入项目名称");
        builder.setView(input);
        
        builder.setPositiveButton("创建", (dialog, which) -> {
            String projectName = input.getText().toString().trim();
            if (!projectName.isEmpty()) {
                if (projectManager.createProject(projectName)) {
                    switchProject(projectName);
                } else {
                    Toast.makeText(this, "创建项目失败，可能项目名已存在", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    /**
     * 切换到指定项目
     */
    private void switchProject(String projectName) {
        if (projectManager.switchToProject(projectName)) {
            // 1. 关闭当前数据库连接
            if (dbHelper != null) {
                dbHelper.close();
            }
            
            // 2. 获取新项目的数据库Helper
            dbHelper = projectManager.getCurrentDbHelper();
            
            // 3. 更新标题
            updateTitle();
            
            // 4. 清空当前界面状态
            clearForm();
            
            // 5. 重新加载数据
            loadMoments();
            
            Toast.makeText(this, "已切换到项目：" + projectName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "切换项目失败", Toast.LENGTH_SHORT).show();
        }
    }
} 