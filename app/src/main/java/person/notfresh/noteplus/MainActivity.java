package person.notfresh.noteplus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.Switch;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ClipboardManager;
import android.content.ClipData;
import androidx.annotation.NonNull;
import android.view.inputmethod.InputMethodManager;

import android.os.PowerManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.model.Tag;

import person.notfresh.noteplus.util.NotificationHelper;
import person.notfresh.noteplus.util.ReminderScheduler;


public class MainActivity extends AppCompatActivity {
    // 添加权限常量
    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 1001;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1002;

    // 添加输入框展开/收起功能
    private boolean isInputExpanded = false;
    private boolean showCost = true; // 默认显示花费
    private boolean timeDescOrder = true; // 默认按时间逆序显示

    private boolean isMultiSelectMode = false;
    private Set<Long> selectedNoteIds = new HashSet<>();
    private MenuItem multiSelectMenuItem = null;

    private boolean hasTimeRange = false;

    private EditText momentEditText;
    private Button saveButton;
    private ListView momentsListView;

    private Button addTagButton;
    private ChipGroup tagChipGroup;
    private List<Tag> selectedTags = new ArrayList<>(); // 新增数据状态

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();

    // 添加dialog作为成员变量
    private AlertDialog tagSelectionDialog;

    private SimpleCursorAdapter noteListAdapter;

    // 新增视图引用
    private TextView startTimeText;
    private TextView endTimeText;
    private TimePickerDialog startTimeDialog;
    private TimePickerDialog endTimeDialog;
    private SimpleDateFormat timeFormat;
    // 添加花费输入框
    private EditText costEditText;

    // 添加项目管理器
    private ProjectContextManager projectManager;
    private NoteDbHelper dbHelper;

    // 添加通知助手
    private NotificationHelper notificationHelper;

    // 添加成员变量来存储待导入的文件信息
    private Uri pendingImportUri = null;
    private String pendingImportFormat = null;

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

        // 初始化花费输入框
        costEditText = findViewById(R.id.costEditText);

        // 加载设置配置
        loadSettings();

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

        // 添加输入框展开/收起功能
        ImageButton expandButton = findViewById(R.id.expandButton);
        expandButton.setOnClickListener(v -> toggleInputExpansion());

        // 初始化通知助手
        notificationHelper = new NotificationHelper(this);
        
        // 请求通知权限(Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission();
        }

        // // 启动定时提醒
        if (ReminderScheduler.isReminderEnabled(this)) {
            // 先取消所有现有提醒
            ReminderScheduler.cancelAllReminders(this);
            // 然后设置新提醒
            ReminderScheduler.scheduleNextReminder(this);
            checkBatteryOptimizations();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 检查是否错过了提醒
        if (ReminderScheduler.isReminderEnabled(this)) {
            ReminderScheduler.checkMissedReminder(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
        // 即使closeAll()不可用，也确保正确关闭数据库
        if (projectManager != null) {
            // 暂时的解决方案，不调用closeAll
            // 代替的方法是切换到默认项目，这会确保当前数据库关闭
            projectManager.switchToProject("default");
        }
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
        final SimpleCursorAdapter tagAdapter = new SimpleCursorAdapter(
                this, 
                R.layout.tag_list_item, 
                tagsCursor,
                new String[]{NoteDbHelper.COLUMN_TAG_NAME},
                new int[]{R.id.tagNameText},
                0);
        
        // 使用单独的ViewBinder来处理颜色视图
        tagAdapter.setViewBinder((view, cursor, columnIndex) -> {
            // 只处理tagNameText的绑定，颜色视图单独处理
            if (view.getId() == R.id.tagNameText) {
                String tagName = cursor.getString(columnIndex);
                ((TextView) view).setText(tagName);
                return true;
            }
            return false;
        });
        
        // 设置适配器
        listViewTags.setAdapter(tagAdapter);
        
        // 在适配器设置后，遍历所有列表项单独设置颜色
        listViewTags.post(() -> {
            for (int i = 0; i < tagAdapter.getCount(); i++) {
                View itemView = tagAdapter.getView(i, null, listViewTags);
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
            @SuppressLint("Range") String tagColor = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
            
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
     * 添加标签到UI
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
     * 加载设置配置
     */
    private void loadSettings() {
        // 加载花费显示设置
        showCost = Boolean.parseBoolean(dbHelper.getSetting(NoteDbHelper.KEY_COST_DISPLAY, "true"));
        
        // 根据设置决定是否显示花费输入框
        if (!showCost) {
            findViewById(R.id.costContainer).setVisibility(View.GONE);
        } else {
            findViewById(R.id.costContainer).setVisibility(View.VISIBLE);
        }
        
        // 加载时间排序设置
        timeDescOrder = Boolean.parseBoolean(dbHelper.getSetting(NoteDbHelper.KEY_TIME_DESC_ORDER, "true"));
    }

    /**
     * 保存一条记录
     */
    private void saveMoment() {
        String content = momentEditText.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        
        // 检查是否开启了时间范围必填
        String timeRangeRequired = dbHelper.getSetting(NoteDbHelper.KEY_TIME_RANGE_REQUIRED, "false");
        if (Boolean.parseBoolean(timeRangeRequired) && !hasTimeRange) {
            Toast.makeText(this, "请设置开始和结束时间", Toast.LENGTH_SHORT).show();
            return;
        }
        // 检查时间区间的有效性
        if (hasTimeRange) {
            if (startCalendar.getTimeInMillis() >= endCalendar.getTimeInMillis()) {
                Toast.makeText(this, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
            }
        }
        
        // 检查花费必填配置
        String costRequired = dbHelper.getSetting(NoteDbHelper.KEY_COST_REQUIRED, "false");
        String costText = costEditText.getText().toString().trim();
        if (Boolean.parseBoolean(costRequired) && costText.isEmpty()) {
            Toast.makeText(this, "请输入花费金额", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 解析花费金额
        double cost = 0.0;
        if (!costText.isEmpty()) {
            try {
                cost = Double.parseDouble(costText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "花费金额格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // 开始事务
        db.beginTransaction();
        try {
            // 1. 保存笔记内容
            ContentValues values = new ContentValues();
            values.put(NoteDbHelper.COLUMN_CONTENT, content);
            values.put(NoteDbHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());
            values.put(NoteDbHelper.COLUMN_COST, cost); // 保存花费金额
            
            long noteId = db.insert(NoteDbHelper.TABLE_NOTES, null, values);
            
            // 2. 如果设置了时间范围，保存时间范围
            if (hasTimeRange) {
                dbHelper.saveTimeRange(noteId, startCalendar.getTimeInMillis(), endCalendar.getTimeInMillis());
            }
            
            // 3. 保存关联的标签
            for (Tag tag : selectedTags) {
                dbHelper.linkNoteToTag(noteId, tag.getId());
            }
            
            // 设置事务成功
            db.setTransactionSuccessful();
            
            // 清空表单
            clearForm();
            
            // 重新加载列表
            loadMoments();
            
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        } finally {
            // 结束事务
            db.endTransaction();
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
        costEditText.setText(""); // 清空花费输入框
        
        // 清空选中标签
        selectedTags.clear();
        tagChipGroup.removeAllViews();
    }

    /**
     * 加载已有记录，并添加长按删除功能
     */
    private void loadMoments() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // 根据设置决定排序方式
        String orderBy = timeDescOrder ? 
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC" : 
                NoteDbHelper.COLUMN_TIMESTAMP + " ASC";
        
        Cursor cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                null, null, null, null,
                orderBy
        );

        // 使用应用中实际存在的资源ID
        String[] from = new String[]{NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP};
        int[] to = new int[]{R.id.contentText, R.id.timestampText};

        noteListAdapter = new SimpleCursorAdapter(
                this,
                R.layout.note_list_item,
                cursor,
                from,
                to,
                0
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                
                // 获取当前记录ID
                Cursor cursor = (Cursor) getItem(position);
                long noteId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                
                // 获取花费金额
                double cost = cursor.getDouble(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
                
                // 为列表项添加时间区间和标签信息
                updateListItemWithExtras(view, noteId, cost);
                
                // 处理多选模式下的复选框
                CheckBox checkBox = view.findViewById(R.id.checkBox);
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
                            updateMultiSelectMenu();
                        });
                    } else {
                        checkBox.setVisibility(View.GONE);
                    }
                }
                
                return view;
            }
        };

        // 设置时间戳格式
        noteListAdapter.setViewBinder((view, cursor1, columnIndex) -> {
            if (columnIndex == cursor1.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP)) {
                long timestamp = cursor1.getLong(columnIndex);
                String formattedDate = formatTimestamp(timestamp);
                ((TextView) view).setText(formattedDate);
                return true;
            }
            return false;
        });

        momentsListView.setAdapter(noteListAdapter);
        
        // 添加点击监听器
        momentsListView.setOnItemClickListener((parent, view, position, id) -> {
            if (isMultiSelectMode) {
                // 多选模式下，点击切换选择状态
                CheckBox checkBox = view.findViewById(R.id.checkBox);
                if (checkBox != null) {
                    checkBox.setChecked(!checkBox.isChecked());
                    if (checkBox.isChecked()) {
                        selectedNoteIds.add(id);
                    } else {
                        selectedNoteIds.remove(id);
                    }
                    updateMultiSelectMenu();
                }
            }
        });
        
        // 添加长按监听器
        momentsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (isMultiSelectMode) {
                // 多选模式下，长按不执行操作
                return false;
            } else {
                showNoteOptionsMenu(view, id);
                return true; // 返回true表示消费了长按事件
            }
        });
    }
    
    /**
     * 为列表项添加时间区间和标签信息
     */
    private void updateListItemWithExtras(View view, long noteId, double cost) {
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
        
        // 如果配置显示花费且花费大于0，则在记录旁边显示花费
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
     */
    private void addTimeRangeInfo(LinearLayout container, long noteId) {
        Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
        
        if (timeRangeCursor != null && timeRangeCursor.moveToFirst()) {
            @SuppressLint("Range") long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_START_TIME));
            @SuppressLint("Range") long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndex(NoteDbHelper.COLUMN_END_TIME));
            
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
                @SuppressLint("Range") String tagName = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                @SuppressLint("Range") String tagColor = tagsCursor.getString(tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                
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



    /**
     * 更新标题显示当前项目
     */
    private void updateTitle() {
        if (getSupportActionBar() != null) {
            String currentProject = projectManager.getCurrentProject();
            getSupportActionBar().setTitle("时间记录 - " + currentProject);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // 获取多选相关菜单项的引用
        multiSelectMenuItem = menu.findItem(R.id.action_multi_select);
        MenuItem moveToProjectMenuItem = menu.findItem(R.id.action_move_to_project);
        MenuItem cancelMultiSelectMenuItem = menu.findItem(R.id.action_cancel_multi_select);
        
        // 根据多选模式设置菜单项可见性
        if (isMultiSelectMode) {
            // 多选模式下显示移动和取消多选菜单项
            if (moveToProjectMenuItem != null) {
                moveToProjectMenuItem.setVisible(true);
            }
            if (cancelMultiSelectMenuItem != null) {
                cancelMultiSelectMenuItem.setVisible(true);
            }
            // 隐藏其他菜单项
            menu.findItem(R.id.action_switch_project).setVisible(false);
            menu.findItem(R.id.action_settings).setVisible(false);
            menu.findItem(R.id.action_export).setVisible(false);
            menu.findItem(R.id.action_import).setVisible(false);
            menu.findItem(R.id.action_recycle_bin).setVisible(false);
        } else {
            // 非多选模式下隐藏移动和取消多选菜单项
            if (moveToProjectMenuItem != null) {
                moveToProjectMenuItem.setVisible(false);
            }
            if (cancelMultiSelectMenuItem != null) {
                cancelMultiSelectMenuItem.setVisible(false);
            }
            // 显示其他菜单项
            menu.findItem(R.id.action_switch_project).setVisible(true);
            menu.findItem(R.id.action_settings).setVisible(true);
            menu.findItem(R.id.action_export).setVisible(true);
            menu.findItem(R.id.action_import).setVisible(true);
            menu.findItem(R.id.action_recycle_bin).setVisible(true);
        }
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_switch_project) {
            showProjectMenu(findViewById(R.id.action_switch_project));
            return true;
        } else if (id == R.id.action_export) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_import) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_recycle_bin) {
            showRecycleBinDialog();
            return true;
        } else if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        } else if (id == R.id.action_multi_select) {
            toggleMultiSelectMode();
            return true;
        } else if (id == R.id.action_move_to_project) {
            showMoveToProjectDialog();
            return true;
        } else if (id == R.id.action_cancel_multi_select) {
            exitMultiSelectMode();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 显示项目菜单 - 简化版
     */
    private void showProjectMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        Menu menu = popup.getMenu();
        
        // 直接创建项目列表，确保至少包含默认项目
        List<String> projects = new ArrayList<>();
        projects.add("default");
        
        // 尝试从projectManager获取更多项目
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            // 如果获取失败，至少确保有默认项目
            e.printStackTrace();
        }
        
        // 去重并确保默认项目存在
        Set<String> uniqueProjects = new HashSet<>(projects);
        uniqueProjects.add("default");
        final List<String> finalProjects = new ArrayList<>(uniqueProjects);
        
        // 添加所有项目到菜单
        for (int i = 0; i < finalProjects.size(); i++) {
            String project = finalProjects.get(i);
            String displayName = project;
            
            // 标记当前项目
            if (project.equals(projectManager.getCurrentProject())) {
                displayName = "✓ " + project;
            }
            
            // 标记默认项目
            if (projectManager.isDefaultProject(project)) {
                if (!displayName.startsWith("✓ ")) {
                    displayName = "★ " + displayName;
                }
                displayName += " (默认)";
            }
            menu.add(Menu.NONE, i, Menu.NONE, displayName);
        }
        
        // 添加管理选项到底部
        menu.add(Menu.NONE, -1, Menu.NONE, "项目管理...");
        
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == -1) {
                // 打开项目管理界面
                showProjectManagementDialog();
                return true;
            }
            
            // 切换到选择的项目
            String selectedProject = finalProjects.get(itemId);
            
            // 如果点击的是当前项目，不执行切换
            if (selectedProject.equals(projectManager.getCurrentProject())) {
                return true;
            }
            
            switchProject(selectedProject);
            return true;
        });
        
        popup.show();
    }
    
    /**
     * 显示项目管理对话框
     */
    private void showProjectManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("项目管理");
        
        // 创建一个带图标的列表项
        String[] options = new String[]{"创建新项目", "重命名项目", "删除项目", "设置默认项目", "回收站"};
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 创建新项目
                    showCreateProjectDialog();
                    break;
                case 1: // 重命名项目
                    showSelectProjectForRename();
                    break;
                case 2: // 删除项目
                    showSelectProjectForDelete();
                    break;
                case 3: // 设置默认项目
                    showSelectProjectForDefault();
                    break;
                case 4: // 回收站
                    showRecycleBinDialog();
                    break;
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 显示选择要重命名的项目对话框
     */
    private void showSelectProjectForRename() {
        // 直接创建项目列表，确保至少包含默认项目
        List<String> projects = new ArrayList<>();
        projects.add("default");
        
        // 尝试从projectManager获取更多项目
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            // 如果获取失败，至少确保有默认项目
            e.printStackTrace();
        }
        
        // 去重并确保默认项目存在
        Set<String> uniqueProjects = new HashSet<>(projects);
        final List<String> finalProjects = new ArrayList<>(uniqueProjects);
        
        String[] items = new String[finalProjects.size()];
        
        // 为每个项目添加标识，显示默认项目
        for (int i = 0; i < finalProjects.size(); i++) {
            String project = finalProjects.get(i);
            if (projectManager.isDefaultProject(project)) {
                items[i] = project + " (默认)";
            } else {
                items[i] = project;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要重命名的项目");
        
        builder.setItems(items, (dialog, which) -> {
            String selectedProject = finalProjects.get(which);
            showRenameProjectDialog(selectedProject);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示选择要设置为默认的项目对话框
     */
    private void showSelectProjectForDefault() {
        // 直接创建项目列表，确保至少包含默认项目
        List<String> projects = new ArrayList<>();
        projects.add("default");
        
        // 尝试从projectManager获取更多项目
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            // 如果获取失败，至少确保有默认项目
            e.printStackTrace();
        }
        
        // 去重并确保默认项目存在
        Set<String> uniqueProjects = new HashSet<>(projects);
        uniqueProjects.add("default");
        final List<String> finalProjects = new ArrayList<>(uniqueProjects);
        
        // 创建显示项（保持默认项目标识）
        String[] items = new String[finalProjects.size()];
        for (int i = 0; i < finalProjects.size(); i++) {
            String project = finalProjects.get(i);
            if (projectManager.isDefaultProject(project)) {
                items[i] = "✓ " + project + " (当前默认)";
            } else {
                items[i] = project;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择默认项目");
        
        builder.setItems(items, (dialog, which) -> {
            String selectedProject = finalProjects.get(which);
            
            // 如果选择的已经是默认项目，不需要重复设置
            if (projectManager.isDefaultProject(selectedProject)) {
                Toast.makeText(this, "该项目已经是默认项目", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 设置新的默认项目
            if (projectManager.setDefaultProject(selectedProject)) {
                Toast.makeText(this, "已将 \"" + selectedProject + "\" 设置为默认项目", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "设置默认项目失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示选择要删除的项目对话框
     */
    private void showSelectProjectForDelete() {
        // 直接创建项目列表，确保至少包含默认项目
        List<String> projects = new ArrayList<>();
        projects.add("default");
        
        // 尝试从projectManager获取更多项目
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            // 如果获取失败，至少确保有默认项目
            e.printStackTrace();
        }
        
        // 去重并确保默认项目存在
        Set<String> uniqueProjects = new HashSet<>(projects);
        uniqueProjects.add("default");
        List<String> allProjects = new ArrayList<>(uniqueProjects);
        
        // 移除默认项目，防止被删除
        String defaultProject = projectManager.getDefaultProject();
        allProjects.remove(defaultProject);
        
        if (allProjects.isEmpty()) {
            Toast.makeText(this, "没有可删除的项目", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final List<String> finalProjects = allProjects;
        String[] items = new String[finalProjects.size()];
        
        // 为每个项目添加标识
        for (int i = 0; i < finalProjects.size(); i++) {
            String project = finalProjects.get(i);
            items[i] = project;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要删除的项目 (默认项目 \"" + defaultProject + "\" 不能被删除)");
        
        builder.setItems(items, (dialog, which) -> {
            String selectedProject = finalProjects.get(which);
            showDeleteProjectConfirmation(selectedProject);
        });
        
        builder.setNegativeButton("取消", null);
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
        builder.setMessage("确定要删除项目 \"" + projectName + "\" 吗？项目将被移至回收站。");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            // 显示进度对话框
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在删除项目...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // 在后台线程中执行删除操作
            new Thread(() -> {
                boolean success = projectManager.moveProjectToRecycleBin(projectName);
                
                // 返回UI线程处理结果
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (success) {
                        Toast.makeText(this, "项目已移至回收站", Toast.LENGTH_SHORT).show();
                        updateTitle();
                        clearForm();
                        
                        try {
                            // 安全地重新加载数据
                            loadMoments();
                        } catch (Exception e) {
                            // 如果加载失败，重新初始化数据库连接
                            e.printStackTrace();
                            Toast.makeText(this, "正在恢复...", Toast.LENGTH_SHORT).show();
                            
                            // 重新初始化数据库连接
                            if (dbHelper != null) {
                                dbHelper.close();
                            }
                            dbHelper = projectManager.getCurrentDbHelper();
                            loadMoments();
                        }
                    } else {
                        Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
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
        // 显示加载指示器
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在切换项目...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 使用后台线程处理数据库操作
        new Thread(() -> {
            if (projectManager.switchToProject(projectName)) {
                // 数据库操作放在后台线程中执行
                if (dbHelper != null) {
                    dbHelper.close();
                }
                dbHelper = projectManager.getCurrentDbHelper();
                
                // 回到主线程更新UI
                runOnUiThread(() -> {
                    updateTitle();
                    clearForm();
                    
                    // 重新加载新项目的设置
                    loadSettings();
                    
                    // 加载数据前更新提示
                    progressDialog.setMessage("正在加载数据...");
                    
                    // 再次使用后台线程加载数据
                    new Thread(() -> {
                        // 最后在UI线程中安全地加载
                        runOnUiThread(() -> {
                            loadMoments(); // 使用现有的加载方法
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, 
                                    "已切换到项目：" + projectName, 
                                    Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                });
            } else {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, 
                            "切换项目失败", 
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示回收站对话框
     */
    private void showRecycleBinDialog() {
        List<String> recycledProjects = projectManager.getRecycledProjects();
        
        if (recycledProjects.isEmpty()) {
            Toast.makeText(this, "回收站为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] items = recycledProjects.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("回收站");
        
        builder.setItems(items, (dialog, which) -> {
            String selectedProject = recycledProjects.get(which);
            showRecycleBinItemOptionsDialog(selectedProject);
        });
        
        builder.setPositiveButton("清空回收站", (dialog, which) -> {
            showEmptyRecycleBinConfirmation();
        });
        
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    /**
     * 显示回收站项目操作选项对话框
     */
    private void showRecycleBinItemOptionsDialog(String projectName) {
        String[] options = new String[]{"恢复项目", "永久删除"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(projectName);
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 恢复项目
                if (projectManager.restoreProjectFromRecycleBin(projectName)) {
                    Toast.makeText(this, "项目已恢复", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "恢复失败", Toast.LENGTH_SHORT).show();
                }
            } else if (which == 1) {
                // 永久删除
                showPermanentDeleteConfirmation(projectName);
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示永久删除确认对话框
     */
    private void showPermanentDeleteConfirmation(String projectName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("永久删除");
        builder.setMessage("确定要永久删除项目 \"" + projectName + "\" 吗？此操作不可恢复。");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            // 显示进度对话框
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在删除...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // 在后台线程中执行操作
            new Thread(() -> {
                boolean success = projectManager.permanentlyDeleteProject(projectName);
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (success) {
                        Toast.makeText(this, "项目已永久删除", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示清空回收站确认对话框
     */
    private void showEmptyRecycleBinConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("清空回收站");
        builder.setMessage("确定要清空回收站吗？此操作将永久删除所有回收站中的项目。");
        
        builder.setPositiveButton("清空", (dialog, which) -> {
            // 显示进度对话框
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在清空回收站...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // 在后台线程中执行操作
            new Thread(() -> {
                boolean success = projectManager.emptyRecycleBin();
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (success) {
                        Toast.makeText(this, "回收站已清空", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "操作部分失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示导出选项对话框
     */
    private void showExportDialog() {
        String[] exportOptions = new String[]{"导出为CSV", "导出为JSON"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择导出格式");
        builder.setItems(exportOptions, (dialog, which) -> {
            String format = exportOptions[which];
            // 检查并请求存储权限
            if (checkStoragePermission()) {
                exportData(format);
            } else {
                requestStoragePermission();
            }
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 检查存储权限
     */
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用作用域存储，不需要请求WRITE_EXTERNAL_STORAGE权限
            return true;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 请求存储权限
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_WRITE_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限获取成功，可以导出
                showExportDialog();
            } else {
                Toast.makeText(this, "需要存储权限才能导出数据", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 导出数据
     */
    private void exportData(String format) {
        String fileName;
        String mimeType;
        String fileExtension = "";
        
        if (format.contains("CSV")) {
            fileName = projectManager.getCurrentProject() + "_export.csv";
            mimeType = "text/csv";
            fileExtension = ".csv";
        } else if (format.contains("JSON")) {
            fileName = projectManager.getCurrentProject() + "_export.json";
            mimeType = "application/json";
            fileExtension = ".json";
        } else {
            mimeType = "";
            fileName = "";
        }

        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在导出数据...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行导出操作
        new Thread(() -> {
            boolean success = false;
            Uri fileUri = null;
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 使用作用域存储 (Android 10+)
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
                    
                    ContentResolver resolver = getContentResolver();
                    fileUri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
                    
                    if (fileUri != null) {
                        OutputStream outputStream = resolver.openOutputStream(fileUri);
                        if (outputStream != null) {
                            // 写入数据
                            if (format.contains("CSV")) {
                                writeCsvData(outputStream);
                            } else {
                                writeJsonData(outputStream);
                            }
                            outputStream.close();
                            success = true;
                        }
                    }
                } else {
                    // 使用传统文件存储 (Android 9 及以下)
                    File documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    if (!documentsFolder.exists()) {
                        documentsFolder.mkdirs();
                    }
                    
                    File outputFile = new File(documentsFolder, fileName);
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    
                    // 写入数据
                    if (format.contains("CSV")) {
                        writeCsvData(fos);
                    } else {
                        writeJsonData(fos);
                    }
                    
                    fos.close();
                    fileUri = Uri.fromFile(outputFile);
                    success = true;
                }
                
                final Uri finalFileUri = fileUri;
                final boolean finalSuccess = success;
                
                // 在UI线程中更新界面
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (finalSuccess) {
                        showExportSuccessDialog(format, finalFileUri);
                    } else {
                        Toast.makeText(MainActivity.this, "导出失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, "导出错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示导出成功对话框
     */
    private void showExportSuccessDialog(String format, Uri fileUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("导出成功");
        builder.setMessage("数据已成功导出为" + format + "格式");
        
        builder.setPositiveButton("打开文件", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, format.contains("CSV") ? "text/csv" : "application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "没有找到可以打开此类文件的应用", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("确定", null);
        builder.show();
    }

    /**
     * 写入CSV数据
     */
    private void writeCsvData(OutputStream outputStream) throws IOException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // 获取所有记录
        Cursor notesCursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                null, null, null, null,
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC"
        );
        
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        
        // 写入CSV头
        writer.write("ID,内容,时间戳,花费,开始时间,结束时间,标签\n");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        // 写入记录
        while (notesCursor.moveToNext()) {
            long noteId = notesCursor.getLong(notesCursor.getColumnIndexOrThrow("_id"));
            String content = notesCursor.getString(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
            long timestamp = notesCursor.getLong(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
            double cost = notesCursor.getDouble(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
            
            // 处理CSV中的特殊字符
            content = "\"" + content.replace("\"", "\"\"") + "\"";
            
            StringBuilder line = new StringBuilder();
            line.append(noteId).append(",");
            line.append(content).append(",");
            line.append(sdf.format(new Date(timestamp))).append(",");
            line.append(cost).append(",");
            
            // 获取时间范围
            Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
            if (timeRangeCursor.moveToFirst()) {
                long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                line.append(sdf.format(new Date(startTime))).append(",");
                line.append(sdf.format(new Date(endTime))).append(",");
            } else {
                line.append(",,");
            }
            timeRangeCursor.close();
            
            // 获取标签
            Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
            StringBuilder tags = new StringBuilder();
            while (tagsCursor.moveToNext()) {
                if (tags.length() > 0) {
                    tags.append(";");
                }
                tags.append(tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME)));
            }
            line.append("\"").append(tags).append("\"");
            tagsCursor.close();
            
            writer.write(line.toString() + "\n");
        }
        
        notesCursor.close();
        writer.flush();
    }

    /**
     * 写入JSON数据
     */
    private void writeJsonData(OutputStream outputStream) throws IOException, JSONException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // 获取所有记录
        Cursor notesCursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                null, null, null, null,
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC"
        );
        
        JSONArray notesArray = new JSONArray();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        // 构建记录
        while (notesCursor.moveToNext()) {
            JSONObject noteObject = new JSONObject();
            
            long noteId = notesCursor.getLong(notesCursor.getColumnIndexOrThrow("_id"));
            String content = notesCursor.getString(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
            long timestamp = notesCursor.getLong(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
            double cost = notesCursor.getDouble(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
            
            noteObject.put("id", noteId);
            noteObject.put("content", content);
            noteObject.put("timestamp", sdf.format(new Date(timestamp)));
            noteObject.put("cost", cost);
            
            // 获取时间范围
            Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
            if (timeRangeCursor.moveToFirst()) {
                JSONObject timeRange = new JSONObject();
                long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                
                timeRange.put("start", sdf.format(new Date(startTime)));
                timeRange.put("end", sdf.format(new Date(endTime)));
                noteObject.put("timeRange", timeRange);
            }
            timeRangeCursor.close();
            
            // 获取标签
            Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
            JSONArray tagsArray = new JSONArray();
            while (tagsCursor.moveToNext()) {
                JSONObject tagObject = new JSONObject();
                tagObject.put("name", tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME)));
                tagObject.put("color", tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_COLOR)));
                tagsArray.put(tagObject);
            }
            tagsCursor.close();
            
            noteObject.put("tags", tagsArray);
            notesArray.put(noteObject);
        }
        
        notesCursor.close();
        
        // 创建根JSON对象
        JSONObject rootObject = new JSONObject();
        rootObject.put("projectName", projectManager.getCurrentProject());
        rootObject.put("exportDate", sdf.format(new Date()));
        rootObject.put("notes", notesArray);
        
        // 写入数据
        OutputStreamWriter writer = new OutputStreamWriter(outputStream);
        writer.write(rootObject.toString(2)); // 格式化JSON输出
        writer.flush();
    }

    // 添加这个方法来处理输入框展开/收起
    private void toggleInputExpansion() {
        isInputExpanded = !isInputExpanded;
        
        // 更新输入框的最小行数
        if (isInputExpanded) {
            momentEditText.setMinLines(4);  // 展开为多行
            momentEditText.setMaxLines(8);
            // 将光标定位到文本末尾
            momentEditText.setSelection(momentEditText.getText().length());
            // 显示键盘
            momentEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(momentEditText, InputMethodManager.SHOW_IMPLICIT);
        } else {
            momentEditText.setMinLines(1);  // 收起为单行
            momentEditText.setMaxLines(3);
        }
        
        // 更新按钮图标
        ((ImageButton)findViewById(R.id.expandButton)).setImageResource(
            isInputExpanded ? R.drawable.ic_collapse : R.drawable.ic_expand);
    }

    /**
     * 显示删除确认对话框
     * @param noteId 要删除的记录ID
     */
    private void showDeleteConfirmDialog(long noteId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除记录");
        builder.setMessage("确定要删除这条记录吗？此操作不可恢复。");
        
        builder.setPositiveButton("删除", (dialog, which) -> {
            deleteNote(noteId);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示笔记选项菜单
     * @param anchorView 锚点视图，菜单将显示在此视图附近
     * @param noteId 笔记ID
     */
    private void showNoteOptionsMenu(View anchorView, long noteId) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        
        // 设置菜单显示在顶部（登高位置）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setGravity(Gravity.TOP);
        }
        
        Menu menu = popupMenu.getMenu();
        
        // 添加菜单项
        menu.add("复制到剪切板");
        menu.add("删除");
        
        // 设置菜单项点击监听器
        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "复制到剪切板":
                    copyToClipboard(noteId);
                    return true;
                case "删除":
                    showDeleteConfirmDialog(noteId);
                    return true;
                default:
                    return false;
            }
        });
        
        popupMenu.show();
    }

    /**
     * 复制笔记内容到剪切板
     * @param noteId 笔记ID
     */
    private void copyToClipboard(long noteId) {
        // 从数据库获取笔记内容
        String noteContent = getNoteContentById(noteId);
        
        if (noteContent != null && !noteContent.trim().isEmpty()) {
            // 获取剪切板管理器
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("笔记内容", noteContent);
            clipboard.setPrimaryClip(clip);
            
            // 显示成功提示
            Toast.makeText(this, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } else {
            // 显示错误提示
            Toast.makeText(this, "无法获取笔记内容", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 根据笔记ID获取笔记内容
     * @param noteId 笔记ID
     * @return 笔记内容字符串，如果获取失败返回null
     */
    private String getNoteContentById(long noteId) {
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
     * 删除记录
     * @param noteId 要删除的记录ID
     */
    private void deleteNote(long noteId) {
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
            if (rowsDeleted > 0) {
                Toast.makeText(this, "记录已删除", Toast.LENGTH_SHORT).show();
                
                // 刷新列表
                loadMoments();
            }
        } finally {
            // 结束事务
            db.endTransaction();
        }
    }

    /**
     * 显示项目设置对话框
     */
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("项目设置");
        
        // 创建设置对话框布局
        View settingsView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        builder.setView(settingsView);
        
        // 初始化时间范围必填开关
        Switch timeRangeRequiredSwitch = settingsView.findViewById(R.id.switchTimeRangeRequired);
        String currentTimeValue = dbHelper.getSetting(NoteDbHelper.KEY_TIME_RANGE_REQUIRED, "false");
        timeRangeRequiredSwitch.setChecked(Boolean.parseBoolean(currentTimeValue));
        
        // 初始化花费显示开关
        Switch costDisplaySwitch = settingsView.findViewById(R.id.switchCostDisplay);
        String currentCostDisplayValue = dbHelper.getSetting(NoteDbHelper.KEY_COST_DISPLAY, "true");
        costDisplaySwitch.setChecked(Boolean.parseBoolean(currentCostDisplayValue));
        
        // 初始化花费必填开关
        Switch costRequiredSwitch = settingsView.findViewById(R.id.switchCostRequired);
        String currentCostRequiredValue = dbHelper.getSetting(NoteDbHelper.KEY_COST_REQUIRED, "false");
        costRequiredSwitch.setChecked(Boolean.parseBoolean(currentCostRequiredValue));
        
        // 找到提醒间隔输入框
        EditText reminderIntervalEdit = settingsView.findViewById(R.id.editTextReminderInterval);

        // 设置当前值
        long currentIntervalMillis = ReminderScheduler.getReminderInterval(this);
        int currentIntervalMinutes = (int) (currentIntervalMillis / (60 * 1000));
        reminderIntervalEdit.setText(String.valueOf(currentIntervalMinutes));

        // 定时提醒开关
        Switch reminderSwitch = settingsView.findViewById(R.id.switchReminder);
        reminderSwitch.setChecked(ReminderScheduler.isReminderEnabled(this));
        
        // 初始化时间排序开关
        Switch timeDescOrderSwitch = settingsView.findViewById(R.id.switchTimeDescOrder);
        timeDescOrderSwitch.setChecked(timeDescOrder);
        
        // 保存按钮点击事件处理
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 保存时间范围设置
            boolean isTimeRangeRequired = timeRangeRequiredSwitch.isChecked();
            dbHelper.saveSetting(NoteDbHelper.KEY_TIME_RANGE_REQUIRED, String.valueOf(isTimeRangeRequired));
            
            // 保存花费显示设置
            boolean isCostDisplay = costDisplaySwitch.isChecked();
            dbHelper.saveSetting(NoteDbHelper.KEY_COST_DISPLAY, String.valueOf(isCostDisplay));
            
            // 保存花费必填设置
            boolean isCostRequired = costRequiredSwitch.isChecked();
            dbHelper.saveSetting(NoteDbHelper.KEY_COST_REQUIRED, String.valueOf(isCostRequired));
            
            // 读取提醒间隔设置
            String intervalStr = reminderIntervalEdit.getText().toString().trim();
            long intervalMinutes;
            try {
                intervalMinutes = Long.parseLong(intervalStr);
                // 确保最小值为1分钟
                if (intervalMinutes < 1) {
                    intervalMinutes = 1;
                }
            } catch (NumberFormatException e) {
                // 如果输入无效，使用默认值10分钟
                intervalMinutes = 10;
            }
            
            // 转换为毫秒并保存
            long intervalMillis = intervalMinutes * 60 * 1000;
            ReminderScheduler.setReminderInterval(this, intervalMillis);
            
            // 保存定时提醒设置
            boolean enableReminder = reminderSwitch.isChecked();
            if (enableReminder) {
                ReminderScheduler.startReminder(this);
            } else {
                ReminderScheduler.stopReminder(this);
            }
            
            // 保存时间排序设置
            boolean newTimeDescOrder = timeDescOrderSwitch.isChecked();
            dbHelper.saveSetting(NoteDbHelper.KEY_TIME_DESC_ORDER, String.valueOf(newTimeDescOrder));
            
            // 重新加载设置以更新界面
            loadSettings();
            
            // 重新加载列表以应用新的排序设置
            loadMoments();
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        });
        
        // 取消按钮
        builder.setNegativeButton("取消", null);
        
        builder.show();
    }

    /**
     * 请求通知权限(Android 13+需要)
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }

    private void checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            boolean isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(getPackageName());
            
            if (!isIgnoringBatteryOptimizations && ReminderScheduler.isReminderEnabled(this)) {
                // 当用户启用了提醒但应用不在电池优化白名单时，显示提示
                new AlertDialog.Builder(this)
                    .setTitle("提高提醒可靠性")
                    .setMessage("为了确保提醒功能在后台正常工作，建议将应用加入电池优化白名单")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("稍后再说", null)
                    .show();
            }
        }
    }

    private void showImportDialog() {
        String[] importOptions = new String[]{"从CSV导入", "从JSON导入"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择导入格式");
        builder.setItems(importOptions, (dialog, which) -> {
            String format = importOptions[which];
            if (checkStoragePermission()) {
                importData(format);
            } else {
                requestStoragePermission();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private static final int PICK_FILE_REQUEST_CODE = 1003;

    private void importData(String format) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (format.contains("CSV")) {
            intent.setType("text/csv");
        } else if (format.contains("JSON")) {
            intent.setType("application/json");
        }

        try {
            startActivityForResult(Intent.createChooser(intent, "选择一个文件进行导入"), PICK_FILE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "请安装文件管理器.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // 存储待导入的文件信息
                pendingImportUri = uri;
                
                // 确定文件格式
                String path = uri.getPath();
                if(path != null){
                     if (path.endsWith(".csv")) {
                        pendingImportFormat = "CSV";
                    } else if (path.endsWith(".json")) {
                        pendingImportFormat = "JSON";
                    } else {
                         // Fallback for URIs that don't have a clear path extension
                         // e.g. from Google Drive. We rely on the MIME type from the intent.
                         ContentResolver cR = this.getContentResolver();
                         String mimeType = cR.getType(uri);
                         if (mimeType != null) {
                             if (mimeType.equals("text/csv") || mimeType.equals("text/comma-separated-values")) {
                                 pendingImportFormat = "CSV";
                             } else if (mimeType.equals("application/json")) {
                                 pendingImportFormat = "JSON";
                             } else {
                                 Toast.makeText(this, "不支持的文件类型: " + mimeType, Toast.LENGTH_SHORT).show();
                                 return;
                             }
                         } else {
                              Toast.makeText(this, "无法确定文件类型", Toast.LENGTH_SHORT).show();
                              return;
                         }
                    }
                }
                
                // 显示导入目标选择对话框
                showImportTargetDialog();
            }
        }
    }

    /**
     * 显示导入目标选择对话框
     */
    private void showImportTargetDialog() {
        String currentProject = projectManager.getCurrentProject();
        String[] options = new String[]{
            "导入到当前项目: " + currentProject,
            "导入到新项目"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择导入的目标位置");
        //builder.setMessage("请选择将数据导入到哪里？");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 导入到当前项目
                importToCurrentProject();
            } else if (which == 1) {
                // 导入到新项目
                showCreateProjectForImportDialog();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> {
            // 清除待导入的文件信息
            pendingImportUri = null;
            pendingImportFormat = null;
        });
        builder.show();
    }

    /**
     * 显示为导入创建新项目的对话框
     */
    private void showCreateProjectForImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建新项目");
        builder.setMessage("请输入新项目的名称，数据将导入到这个项目中");
        
        final EditText input = new EditText(this);
        input.setHint("输入项目名称");
        builder.setView(input);
        
        builder.setPositiveButton("创建并导入", (dialog, which) -> {
            String projectName = input.getText().toString().trim();
            if (!projectName.isEmpty()) {
                if (projectManager.createProject(projectName)) {
                    // 切换到新项目并导入数据
                    switchProjectAndImport(projectName);
                } else {
                    Toast.makeText(this, "创建项目失败，可能项目名已存在", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请输入项目名称", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            // 清除待导入的文件信息
            pendingImportUri = null;
            pendingImportFormat = null;
        });
        
        builder.show();
    }

    /**
     * 导入到当前项目
     */
    private void importToCurrentProject() {
        if (pendingImportUri != null && pendingImportFormat != null) {
            if ("CSV".equals(pendingImportFormat)) {
                readCsvData(pendingImportUri);
            } else if ("JSON".equals(pendingImportFormat)) {
                readJsonData(pendingImportUri);
            }
            
            // 清除待导入的文件信息
            pendingImportUri = null;
            pendingImportFormat = null;
        }
    }

    /**
     * 切换到指定项目并导入数据
     */
    private void switchProjectAndImport(String projectName) {
        // 显示加载指示器
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在创建项目并准备导入...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 使用后台线程处理数据库操作
        new Thread(() -> {
            if (projectManager.switchToProject(projectName)) {
                // 数据库操作放在后台线程中执行
                if (dbHelper != null) {
                    dbHelper.close();
                }
                dbHelper = projectManager.getCurrentDbHelper();
                
                // 回到主线程更新UI并开始导入
                runOnUiThread(() -> {
                    updateTitle();
                    clearForm();
                    
                    // 重新加载新项目的设置
                    loadSettings();
                    
                    // 开始导入数据
                    if (pendingImportUri != null && pendingImportFormat != null) {
                        if ("CSV".equals(pendingImportFormat)) {
                            readCsvData(pendingImportUri);
                        } else if ("JSON".equals(pendingImportFormat)) {
                            readJsonData(pendingImportUri);
                        }
                        
                        // 清除待导入的文件信息
                        pendingImportUri = null;
                        pendingImportFormat = null;
                    }
                    
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, 
                            "已切换到项目：" + projectName, 
                            Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, 
                            "切换项目失败", 
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void readCsvData(Uri uri) {
        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在从CSV导入...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行导入操作
        new Thread(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                java.io.InputStream inputStream = resolver.openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
                String line;
                int importedCount = 0;
                int skippedCount = 0;
                
                // 跳过标题行
                reader.readLine();
                
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                
                try {
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        
                        try {
                            // 解析CSV行
                            String[] fields = parseCsvLine(line);
                            if (fields.length >= 3) {
                                // 解析字段 - 格式：ID,内容,时间戳,花费,开始时间,结束时间,标签
                                long noteId = Long.parseLong(fields[0]);
                                String content = fields[1].replace("\"\"", "\""); // 处理转义的双引号
                                String timestampStr = fields[2];
                                
                                // 解析时间戳
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                long timestamp = sdf.parse(timestampStr).getTime();
                                
                                // 插入笔记
                                ContentValues noteValues = new ContentValues();
                                noteValues.put(NoteDbHelper.COLUMN_CONTENT, content);
                                noteValues.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                                
                                // 处理花费信息 (字段3)
                                if (fields.length > 3 && !fields[3].isEmpty()) {
                                    try {
                                        double cost = Double.parseDouble(fields[3]);
                                        noteValues.put(NoteDbHelper.COLUMN_COST, cost);
                                    } catch (NumberFormatException e) {
                                        // 花费解析失败，使用默认值0
                                        noteValues.put(NoteDbHelper.COLUMN_COST, 0.0);
                                    }
                                }
                                
                                long newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, noteValues);
                                
                                // 处理时间范围 (字段4和5)
                                if (fields.length > 6 && !fields[4].isEmpty() && !fields[5].isEmpty()) {
                                    try {
                                        long startTime = sdf.parse(fields[4]).getTime();
                                        long endTime = sdf.parse(fields[5]).getTime();
                                        dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                                    } catch (Exception e) {
                                        // 时间范围解析失败，跳过
                                    }
                                }
                                
                                // 处理标签 (字段6)
                                if (fields.length > 6 && !fields[6].isEmpty()) {
                                    String tagsStr = fields[6].replace("\"", "");
                                    String[] tagNames = tagsStr.split(";");
                                    for (String tagName : tagNames) {
                                        if (!tagName.trim().isEmpty()) {
                                            // 检查标签是否存在，不存在则创建
                                            long tagId = dbHelper.getTagIdByName(tagName.trim());
                                            if (tagId == -1) {
                                                // 创建新标签
                                                String[] colors = {"#FF5722", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107", "#607D8B"};
                                                String randomColor = colors[new Random().nextInt(colors.length)];
                                                tagId = dbHelper.addTag(tagName.trim(), randomColor);
                                            }
                                            if (tagId != -1) {
                                                dbHelper.linkNoteToTag(newNoteId, tagId);
                                            }
                                        }
                                    }
                                }
                                
                                importedCount++;
                            }
                        } catch (Exception e) {
                            skippedCount++;
                            e.printStackTrace();
                        }
                    }
                    
                    db.setTransactionSuccessful();
                    
                    final int finalImportedCount = importedCount;
                    final int finalSkippedCount = skippedCount;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        String currentProject = projectManager.getCurrentProject();
                        String message = String.format("导入完成！成功导入 %d 条记录到项目 \"%s\"", finalImportedCount, currentProject);
                        if (finalSkippedCount > 0) {
                            message += String.format("，跳过 %d 条记录", finalSkippedCount);
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        loadMoments(); // 刷新列表
                    });
                    
                } finally {
                    db.endTransaction();
                    reader.close();
                    inputStream.close();
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void readJsonData(Uri uri) {
        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在从JSON导入...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行导入操作
        new Thread(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                java.io.InputStream inputStream = resolver.openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // 读取JSON内容
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
                StringBuilder jsonString = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonString.append(line);
                }
                
                JSONObject rootObject = new JSONObject(jsonString.toString());
                JSONArray notesArray = rootObject.getJSONArray("notes");
                
                int importedCount = 0;
                int skippedCount = 0;
                
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    
                    for (int i = 0; i < notesArray.length(); i++) {
                        try {
                            JSONObject noteObject = notesArray.getJSONObject(i);
                            
                            // 解析笔记基本信息
                            String content = noteObject.getString("content");
                            String timestampStr = noteObject.getString("timestamp");
                            long timestamp = sdf.parse(timestampStr).getTime();
                            
                            // 插入笔记
                            ContentValues noteValues = new ContentValues();
                            noteValues.put(NoteDbHelper.COLUMN_CONTENT, content);
                            noteValues.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                            
                            // 处理花费信息
                            if (noteObject.has("cost")) {
                                double cost = noteObject.getDouble("cost");
                                noteValues.put(NoteDbHelper.COLUMN_COST, cost);
                            }
                            
                            long newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, noteValues);
                            
                            // 处理时间范围
                            if (noteObject.has("timeRange")) {
                                JSONObject timeRange = noteObject.getJSONObject("timeRange");
                                String startTimeStr = timeRange.getString("start");
                                String endTimeStr = timeRange.getString("end");
                                
                                long startTime = sdf.parse(startTimeStr).getTime();
                                long endTime = sdf.parse(endTimeStr).getTime();
                                dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                            }
                            
                            // 处理标签
                            if (noteObject.has("tags")) {
                                JSONArray tagsArray = noteObject.getJSONArray("tags");
                                for (int j = 0; j < tagsArray.length(); j++) {
                                    JSONObject tagObject = tagsArray.getJSONObject(j);
                                    String tagName = tagObject.getString("name");
                                    String tagColor = tagObject.getString("color");
                                    
                                    // 检查标签是否存在
                                    long tagId = dbHelper.getTagIdByName(tagName);
                                    if (tagId == -1) {
                                        // 创建新标签
                                        tagId = dbHelper.addTag(tagName, tagColor);
                                    }
                                    if (tagId != -1) {
                                        dbHelper.linkNoteToTag(newNoteId, tagId);
                                    }
                                }
                            }
                            
                            importedCount++;
                            
                        } catch (Exception e) {
                            skippedCount++;
                            e.printStackTrace();
                        }
                    }
                    
                    db.setTransactionSuccessful();
                    
                    final int finalImportedCount = importedCount;
                    final int finalSkippedCount = skippedCount;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        String currentProject = projectManager.getCurrentProject();
                        String message = String.format("导入完成！成功导入 %d 条记录到项目 \"%s\"", finalImportedCount, currentProject);
                        if (finalSkippedCount > 0) {
                            message += String.format("，跳过 %d 条记录", finalSkippedCount);
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        loadMoments(); // 刷新列表
                    });
                    
                } finally {
                    db.endTransaction();
                    reader.close();
                    inputStream.close();
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 解析CSV行，处理引号内的逗号
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 转义的双引号
                    currentField.append('"');
                    i++; // 跳过下一个引号
                } else {
                    // 切换引号状态
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段分隔符
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // 添加最后一个字段
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }

    /**
     * 切换多选模式
     */
    private void toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode;
        
        if (isMultiSelectMode) {
            // 进入多选模式
            selectedNoteIds.clear();
            updateMultiSelectMenu();
            invalidateOptionsMenu(); // 刷新菜单
            noteListAdapter.notifyDataSetChanged(); // 刷新列表显示复选框
        } else {
            // 退出多选模式
            selectedNoteIds.clear();
            updateMultiSelectMenu();
            invalidateOptionsMenu(); // 刷新菜单
            noteListAdapter.notifyDataSetChanged(); // 隐藏复选框
        }
    }

    /**
     * 退出多选模式
     */
    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedNoteIds.clear();
        updateMultiSelectMenu();
        invalidateOptionsMenu(); // 刷新菜单
        noteListAdapter.notifyDataSetChanged(); // 隐藏复选框
    }

    /**
     * 更新多选菜单状态
     */
    private void updateMultiSelectMenu() {
        if (multiSelectMenuItem != null) {
            if (isMultiSelectMode) {
                multiSelectMenuItem.setTitle("多选 (" + selectedNoteIds.size() + ")");
            } else {
                multiSelectMenuItem.setTitle("多选");
            }
        }
    }

    /**
     * 显示移动到项目对话框
     */
    private void showMoveToProjectDialog() {
        if (selectedNoteIds.isEmpty()) {
            Toast.makeText(this, "请先选择要移动的记录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取所有项目列表
        List<String> projects = new ArrayList<>();
        projects.add("default");
        
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 去重并确保默认项目存在
        Set<String> uniqueProjects = new HashSet<>(projects);
        uniqueProjects.add("default");
        final List<String> finalProjects = new ArrayList<>(uniqueProjects);
        
        // 移除当前项目
        String currentProject = projectManager.getCurrentProject();
        finalProjects.remove(currentProject);
        
        if (finalProjects.isEmpty()) {
            Toast.makeText(this, "没有其他项目可以移动", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] items = new String[finalProjects.size()];
        for (int i = 0; i < finalProjects.size(); i++) {
            String project = finalProjects.get(i);
            if (projectManager.isDefaultProject(project)) {
                items[i] = project + " (默认)";
            } else {
                items[i] = project;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择目标项目");
        //builder.setMessage("将选中的 " + selectedNoteIds.size() + " 条记录移动到哪个项目？");
        
        builder.setItems(items, (dialog, which) -> {
            String targetProject = finalProjects.get(which);
            showMoveConfirmationDialog(targetProject);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 显示移动确认对话框
     */
    private void showMoveConfirmationDialog(String targetProject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认移动");
        builder.setMessage("确定要将选中的 " + selectedNoteIds.size() + " 条记录移动到项目 \"" + targetProject + "\" 吗？");
        
        builder.setPositiveButton("移动", (dialog, which) -> {
            moveNotesToProject(targetProject);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 移动记录到指定项目
     */
    private void moveNotesToProject(String targetProject) {
        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在移动记录...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 在后台线程执行移动操作
        new Thread(() -> {
            try {
                // 获取目标项目的数据库Helper
                NoteDbHelper targetDbHelper = projectManager.getDbHelperForProject(targetProject);
                if (targetDbHelper == null) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "目标项目不存在", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                SQLiteDatabase sourceDb = dbHelper.getReadableDatabase();
                SQLiteDatabase targetDb = targetDbHelper.getWritableDatabase();
                
                int movedCount = 0;
                int failedCount = 0;
                
                // 开始事务
                targetDb.beginTransaction();
                sourceDb.beginTransaction();
                
                try {
                    for (Long noteId : selectedNoteIds) {
                        try {
                            // 1. 从源数据库获取记录信息
                            Cursor noteCursor = sourceDb.query(
                                NoteDbHelper.TABLE_NOTES,
                                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                                "_id = ?",
                                new String[]{String.valueOf(noteId)},
                                null, null, null
                            );
                            
                            if (noteCursor.moveToFirst()) {
                                String content = noteCursor.getString(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
                                long timestamp = noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
                                double cost = noteCursor.getDouble(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
                                
                                // 2. 插入到目标数据库
                                ContentValues values = new ContentValues();
                                values.put(NoteDbHelper.COLUMN_CONTENT, content);
                                values.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                                values.put(NoteDbHelper.COLUMN_COST, cost);
                                
                                long newNoteId = targetDb.insert(NoteDbHelper.TABLE_NOTES, null, values);
                                
                                if (newNoteId != -1) {
                                    // 3. 复制时间范围
                                    Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
                                    if (timeRangeCursor.moveToFirst()) {
                                        long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                                        long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                                        targetDbHelper.saveTimeRange(newNoteId, startTime, endTime);
                                    }
                                    timeRangeCursor.close();
                                    
                                    // 4. 复制标签关联
                                    Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
                                    while (tagsCursor.moveToNext()) {
                                        String tagName = tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME));
                                        String tagColor = tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_COLOR));
                                        
                                        // 检查目标项目中是否存在该标签
                                        long tagId = targetDbHelper.getTagIdByName(tagName);
                                        if (tagId == -1) {
                                            // 创建新标签
                                            tagId = targetDbHelper.addTag(tagName, tagColor);
                                        }
                                        if (tagId != -1) {
                                            targetDbHelper.linkNoteToTag(newNoteId, tagId);
                                        }
                                    }
                                    tagsCursor.close();
                                    
                                    // 5. 从源数据库删除记录
                                    sourceDb.delete(NoteDbHelper.TABLE_NOTES, "_id = ?", new String[]{String.valueOf(noteId)});
                                    
                                    movedCount++;
                                } else {
                                    failedCount++;
                                }
                            }
                            noteCursor.close();
                            
                        } catch (Exception e) {
                            failedCount++;
                            e.printStackTrace();
                        }
                    }
                    
                    // 提交事务
                    targetDb.setTransactionSuccessful();
                    sourceDb.setTransactionSuccessful();
                    
                    final int finalMovedCount = movedCount;
                    final int finalFailedCount = failedCount;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        
                        String message = String.format("移动完成！成功移动 %d 条记录到项目 \"%s\"", finalMovedCount, targetProject);
                        if (finalFailedCount > 0) {
                            message += String.format("，失败 %d 条记录", finalFailedCount);
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        
                        // 退出多选模式并刷新列表
                        exitMultiSelectMode();
                        loadMoments();
                    });
                    
                } finally {
                    targetDb.endTransaction();
                    sourceDb.endTransaction();
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "移动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}