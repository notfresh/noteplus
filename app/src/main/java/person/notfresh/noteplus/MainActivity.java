package person.notfresh.noteplus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
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
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

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
import person.notfresh.noteplus.core.model.Tag;
import person.notfresh.noteplus.core.GlobalTimeline;
import person.notfresh.noteplus.core.TimeRangeFilter;
import person.notfresh.noteplus.core.model.Comment;

import person.notfresh.noteplus.util.NotificationHelper;
import person.notfresh.noteplus.util.ReminderScheduler;
import person.notfresh.noteplus.util.StringUtil;
import person.notfresh.noteplus.util.DisplayUtil;
import person.notfresh.noteplus.core.model.Note;
import person.notfresh.noteplus.manager.NoteListManager;
import person.notfresh.noteplus.manager.INoteListCallback;


public class MainActivity extends AppCompatActivity implements INoteListCallback {
    // 添加权限常量
    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 1001;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1002;
    
    // 时间线偏好保存常量
    private static final String PREFS_TIMELINE = "timeline_prefs";
    private static final String KEY_TIMELINE_RANGE = "time_range";
    private static final String KEY_TIMELINE_SORT = "sort_descending";

    // 输入模式：0=普通, 1=展开, 2=全屏
    private static final int INPUT_MODE_NORMAL = 0;
    private static final int INPUT_MODE_EXPANDED = 1;
    private static final int INPUT_MODE_FULLSCREEN = 2;
    private int inputMode = INPUT_MODE_NORMAL;
    
    private boolean showCost = true; // 默认显示花费
    private boolean showTimeRange = false; // 默认不显示时间区间
    private boolean timeDescOrder = true; // 默认按时间逆序显示

    private boolean isMultiSelectMode = false;
    private Set<Long> selectedNoteIds = new HashSet<>();
    private MenuItem multiSelectMenuItem = null;

    private boolean hasTimeRange = false;
    
    // 时间轴双击检测相关变量
    private Handler timelineClickHandler = new Handler(Looper.getMainLooper());
    private Runnable timelineClickRunnable;
    private long lastTimelineClickTime = 0;
    private int lastTimelineClickPosition = -1;
    private static final long DOUBLE_CLICK_DELAY = 500; // 双击间隔时间（毫秒），增加到500ms以提高检测成功率

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

    // 导入导出管理器
    private person.notfresh.noteplus.manager.ImportExportManager importExportManager;

    // 笔记列表管理器
    private NoteListManager noteListManager;

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
        
        // 初始化导入导出管理器
        importExportManager = new person.notfresh.noteplus.manager.ImportExportManager(this, dbHelper, projectManager);
        
        // 更新标题栏显示当前项目 - 在设置ActionBar之后执行
        updateTitle();

        // 初始化视图
        momentEditText = findViewById(R.id.momentEditText);
        saveButton = findViewById(R.id.saveButton);
        momentsListView = findViewById(R.id.momentsListView);
        
        // 初始化笔记列表管理器
        noteListManager = new NoteListManager();
        noteListManager.initialize(momentsListView, this);
        
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
        
        // 加载折叠状态
        if (noteListManager != null) {
            noteListManager.loadFoldedNoteIds();
        }

        // 加载现有记录
        if (noteListManager != null) {
            noteListManager.loadNotes();
        }

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

        // 添加输入模式切换功能（三档切换：普通 -> 展开 -> 全屏 -> 普通）
        ImageButton expandButton = findViewById(R.id.expandButton);
        expandButton.setOnClickListener(v -> toggleInputMode());
        
        // 初始化语音输入按钮
        ImageButton voiceInputButton = findViewById(R.id.voiceInputButton);
        voiceInputButton.setOnClickListener(v -> {
            Toast.makeText(this, "语音输入功能（待实现）", Toast.LENGTH_SHORT).show();
            // TODO: 实现语音输入功能
        });
        
        // 初始化图片输入按钮
        ImageButton imageInputButton = findViewById(R.id.imageInputButton);
        imageInputButton.setOnClickListener(v -> {
            Toast.makeText(this, "添加图片功能（待实现）", Toast.LENGTH_SHORT).show();
            // TODO: 实现图片选择/拍摄功能
        });

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
        // NoteListManager 会自己管理 Cursor 包装器的关闭
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
        
        // 加载时间区间显示设置
        showTimeRange = Boolean.parseBoolean(dbHelper.getSetting(NoteDbHelper.KEY_TIME_RANGE_DISPLAY, "false"));
        
        // 根据设置决定是否显示时间区间输入框
        if (!showTimeRange) {
            findViewById(R.id.timeRangeContainer).setVisibility(View.GONE);
        } else {
            findViewById(R.id.timeRangeContainer).setVisibility(View.VISIBLE);
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
            if (noteListManager != null) {
                noteListManager.refreshNotes();
            }
            
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
     * 加载已有记录
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void loadMoments() {
        if (noteListManager != null) {
            noteListManager.loadNotes();
        }
    }
    
    /**
     * 初始化音频播放控件（测试用）
     * @param view 列表项视图
     * @param noteId 笔记ID
     */
    private void initAudioControls(View view, long noteId) {
        LinearLayout audioContainer = view.findViewById(R.id.audioContainer);
        if (audioContainer == null) {
            return;
        }
        
        ImageButton playPauseButton = view.findViewById(R.id.audioPlayPauseButton);
        SeekBar audioSeekBar = view.findViewById(R.id.audioSeekBar);
        TextView currentTimeText = view.findViewById(R.id.audioCurrentTimeText);
        TextView totalTimeText = view.findViewById(R.id.audioTotalTimeText);
        
        if (playPauseButton == null || audioSeekBar == null || 
            currentTimeText == null || totalTimeText == null) {
            return;
        }
        
        // 设置测试数据：总时长 02:30，当前进度 50%
        totalTimeText.setText("02:30");
        currentTimeText.setText("01:15");
        audioSeekBar.setMax(150); // 150秒 = 2分30秒
        audioSeekBar.setProgress(75); // 75秒 = 1分15秒
        
        // 播放/暂停按钮状态（默认暂停状态）
        boolean[] isPlaying = {false};
        
        // 设置播放/暂停按钮点击事件
        playPauseButton.setOnClickListener(v -> {
            isPlaying[0] = !isPlaying[0];
            if (isPlaying[0]) {
                // 切换到暂停图标
                playPauseButton.setImageResource(R.drawable.ic_audio_pause);
                Toast.makeText(this, "播放音频 (笔记ID: " + noteId + ")", Toast.LENGTH_SHORT).show();
            } else {
                // 切换到播放图标
                playPauseButton.setImageResource(R.drawable.ic_audio_play);
                Toast.makeText(this, "暂停播放", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 设置进度条拖动事件
        audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 用户拖动时更新当前时间显示
                    int minutes = progress / 60;
                    int seconds = progress % 60;
                    currentTimeText.setText(String.format("%02d:%02d", minutes, seconds));
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时的处理
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                Toast.makeText(MainActivity.this, 
                    "跳转到: " + String.format("%02d:%02d", progress / 60, progress % 60), 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    
    
    /**
     * 显示评论选项菜单
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void showCommentOptionsMenu(long commentId, long noteId) {
        if (noteListManager != null) {
            noteListManager.showCommentOptionsMenu(commentId, noteId);
        }
    }
    
    /**
     * 显示删除追加内容确认对话框
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void showDeleteCommentDialog(long commentId, long noteId) {
        if (noteListManager != null) {
            noteListManager.showDeleteCommentDialog(commentId, noteId);
        }
    }
    
    /**
     * 刷新指定笔记的视图
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void refreshNoteView(long noteId) {
        if (noteListManager != null) {
            noteListManager.refreshNoteView(noteId);
        }
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
        
        // 延迟设置项目切换按钮的长按监听（确保 View 已创建）
        new Handler(Looper.getMainLooper()).post(() -> {
            setupProjectSwitchLongPress();
        });
        
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
        } else if (id == R.id.action_timeline) {
            showTimelineDialog();
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
        
        // 直接从projectManager获取项目列表（已排序）
        List<String> finalProjects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                finalProjects.addAll(existingProjects);
            } 
        } catch (Exception e) {
            // 如果获取失败，至少确保有默认项目
            e.printStackTrace();
        }
        
        // 确保默认项目存在（如果不存在则添加到末尾）
        if (!finalProjects.contains("default")) {
            finalProjects.add("default");
        }
        
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
        String[] options = new String[]{"创建新项目", "重命名项目", "删除项目", "设置默认项目", "项目排序", "回收站"};
        
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
                case 4: // 项目排序
                    showProjectOrderDialog();
                    break;
                case 5: // 回收站
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
        // 直接从projectManager获取项目列表（已排序）
        List<String> finalProjects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                finalProjects.addAll(existingProjects);
            } else {
                finalProjects.add("default");
            }
        } catch (Exception e) {
            e.printStackTrace();
            finalProjects.add("default");
        }
        
        // 确保默认项目存在
        if (!finalProjects.contains("default")) {
            finalProjects.add("default");
        }
        
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
     * 显示项目排序对话框
     */
    private void showProjectOrderDialog() {
        // 获取保存的顺序（用于调试）
        List<String> savedOrder = projectManager.getProjectOrderForDebug();
        android.util.Log.d("ProjectOrder", "保存的顺序: " + savedOrder.toString());
        
        // 获取当前项目列表（已排序，应该反映保存的顺序）
        List<String> projects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                projects.addAll(existingProjects);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        android.util.Log.d("ProjectOrder", "加载后的顺序: " + projects.toString());
        
        // 确保至少包含默认项目
        if (!projects.contains("default")) {
            projects.add("default");
        }
        
        if (projects.isEmpty()) {
            Toast.makeText(this, "没有可排序的项目", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 创建可编辑的项目列表副本（用于排序操作）
        // 注意：这个列表应该已经按照保存的顺序排列了
        final List<String> orderedProjects = new ArrayList<>(projects);
        
        // 创建自定义对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("项目排序");
        
        // 创建自定义布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_project_order, null);
        builder.setView(dialogView);
        
        // 获取 ListView
        ListView listView = dialogView.findViewById(R.id.projectOrderListView);
        
        // 创建适配器
        ProjectOrderAdapter adapter = new ProjectOrderAdapter(orderedProjects);
        listView.setAdapter(adapter);
        
        // 创建对话框
        AlertDialog dialog = builder.create();
        
        // 设置保存按钮
        Button saveButton = dialogView.findViewById(R.id.btnSaveProjectOrder);
        saveButton.setOnClickListener(v -> {
            // 保存排序（直接保存用户操作的顺序，不做任何修改）
            if (projectManager.setProjectOrder(orderedProjects)) {
                // 立即验证：重新获取保存的顺序
                List<String> savedOrderAfterSave = projectManager.getProjectOrderForDebug();
                
                // 验证保存的顺序是否与用户操作的顺序一致
                boolean orderMatches = true;
                if (savedOrderAfterSave.size() == orderedProjects.size()) {
                    for (int i = 0; i < orderedProjects.size(); i++) {
                        if (!orderedProjects.get(i).equals(savedOrderAfterSave.get(i))) {
                            orderMatches = false;
                            break;
                        }
                    }
                } else {
                    orderMatches = false;
                }
                
                if (orderMatches) {
                    Toast.makeText(this, "项目顺序已保存", Toast.LENGTH_SHORT).show();
                } else {
                    // 显示调试信息
                    android.util.Log.e("ProjectOrder", "保存顺序: " + savedOrderAfterSave.toString());
                    android.util.Log.e("ProjectOrder", "期望顺序: " + orderedProjects.toString());
                    Toast.makeText(this, "顺序已保存，但验证不匹配（请查看日志）", Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 设置取消按钮
        Button cancelButton = dialogView.findViewById(R.id.btnCancelProjectOrder);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
        
        // 设置对话框窗口大小
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.7)
            );
        }
    }
    
    /**
     * 项目排序列表适配器
     */
    private class ProjectOrderAdapter extends android.widget.BaseAdapter {
        private final List<String> projects;
        
        public ProjectOrderAdapter(List<String> projects) {
            this.projects = projects;
        }
        
        @Override
        public int getCount() {
            return projects.size();
        }
        
        @Override
        public String getItem(int position) {
            return projects.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_project_order, parent, false);
            }
            
            TextView projectNameText = convertView.findViewById(R.id.projectOrderNameText);
            ImageButton btnMoveUp = convertView.findViewById(R.id.btnMoveUp);
            ImageButton btnMoveDown = convertView.findViewById(R.id.btnMoveDown);
            
            String projectName = getItem(position);
            String displayName = projectName;
            
            // 标记默认项目
            if (projectManager.isDefaultProject(projectName)) {
                displayName = "★ " + projectName + " (默认)";
            }
            
            // 标记当前项目
            if (projectName.equals(projectManager.getCurrentProject())) {
                displayName = "✓ " + displayName;
            }
            
            projectNameText.setText(displayName);
            
            // 设置上移按钮状态和点击事件
            boolean canMoveUp = position > 0;
            btnMoveUp.setEnabled(canMoveUp);
            btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.3f);
            btnMoveUp.setOnClickListener(v -> {
                if (position > 0) {
                    // 交换位置
                    String temp = projects.get(position);
                    projects.set(position, projects.get(position - 1));
                    projects.set(position - 1, temp);
                    notifyDataSetChanged();
                }
            });
            
            // 设置下移按钮状态和点击事件
            boolean canMoveDown = position < projects.size() - 1;
            btnMoveDown.setEnabled(canMoveDown);
            btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.3f);
            btnMoveDown.setOnClickListener(v -> {
                if (position < projects.size() - 1) {
                    // 交换位置
                    String temp = projects.get(position);
                    projects.set(position, projects.get(position + 1));
                    projects.set(position + 1, temp);
                    notifyDataSetChanged();
                }
            });
            
            return convertView;
        }
    }
    
    /**
     * 显示选择要设置为默认的项目对话框
     */
    private void showSelectProjectForDefault() {
        // 直接从projectManager获取项目列表（已排序）
        List<String> finalProjects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                finalProjects.addAll(existingProjects);
            } else {
                finalProjects.add("default");
            }
        } catch (Exception e) {
            e.printStackTrace();
            finalProjects.add("default");
        }
        
        // 确保默认项目存在
        if (!finalProjects.contains("default")) {
            finalProjects.add("default");
        }
        
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
        // 直接从projectManager获取项目列表（已排序）
        List<String> allProjects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                allProjects.addAll(existingProjects);
            } else {
                allProjects.add("default");
            }
        } catch (Exception e) {
            e.printStackTrace();
            allProjects.add("default");
        }
        
        // 确保默认项目存在
        if (!allProjects.contains("default")) {
            allProjects.add("default");
        }
        
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
                            if (noteListManager != null) {
                noteListManager.loadNotes();
            }
                        } catch (Exception e) {
                            // 如果加载失败，重新初始化数据库连接
                            e.printStackTrace();
                            Toast.makeText(this, "正在恢复...", Toast.LENGTH_SHORT).show();
                            
                            // 重新初始化数据库连接
                            if (dbHelper != null) {
                                dbHelper.close();
                            }
                            dbHelper = projectManager.getCurrentDbHelper();
                            if (noteListManager != null) {
                noteListManager.loadNotes();
            }
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
                
                // 更新导入导出管理器
                importExportManager = new person.notfresh.noteplus.manager.ImportExportManager(
                    MainActivity.this, dbHelper, projectManager);
                
                // 回到主线程更新UI
                runOnUiThread(() -> {
                    updateTitle();
                    clearForm();
                    
                    // 重新加载新项目的设置
                    loadSettings();
                    
                    // 加载新项目的折叠状态
                    if (noteListManager != null) {
                        noteListManager.loadFoldedNoteIds();
                    }
                    
                    // 加载数据前更新提示
                    progressDialog.setMessage("正在加载数据...");
                    
                    // 再次使用后台线程加载数据
                    new Thread(() -> {
                        // 最后在UI线程中安全地加载
                        runOnUiThread(() -> {
                            if (noteListManager != null) {
                noteListManager.loadNotes();
            } // 使用现有的加载方法
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
     * 设置项目切换按钮的长按监听
     */
    private void setupProjectSwitchLongPress() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        
        // 延迟一点时间，确保菜单项 View 已经创建
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 通过遍历 Toolbar 的所有子 View 查找菜单项
            findAndSetupMenuItemView(toolbar, R.id.action_switch_project);
        }, 100);
    }

    /**
     * 递归查找指定 ID 的菜单项 View 并设置长按监听
     */
    private void findAndSetupMenuItemView(ViewGroup parent, int menuItemId) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            
            // 检查 View 的 tag
            Object tag = child.getTag();
            if (tag != null && tag.toString().contains(String.valueOf(menuItemId))) {
                setupLongPressListener(child);
                return;
            }
            
            // 检查 View 的 contentDescription
            CharSequence contentDesc = child.getContentDescription();
            if (contentDesc != null && contentDesc.toString().contains("切换项目")) {
                setupLongPressListener(child);
                return;
            }
            
            // 递归查找子 View
            if (child instanceof ViewGroup) {
                findAndSetupMenuItemView((ViewGroup) child, menuItemId);
            }
        }
    }

    /**
     * 为 View 设置长按监听（精确控制2秒）
     */
    private void setupLongPressListener(View view) {
        final long LONG_PRESS_DURATION = 1000; // 2秒
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable longPressRunnable = () -> {
            // 长按2秒后执行
            switchToPreviousProject();
        };
        
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 延迟2秒后执行长按操作
                        handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
                        // 显示提示
                        showLongPressHint();
                        return false; // 不消费事件，允许点击
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 取消长按操作
                        handler.removeCallbacks(longPressRunnable);
                        hideLongPressHint();
                        return false; // 不消费事件，允许点击
                }
                return false;
            }
        });
    }

    /**
     * 显示长按提示
     */
    private Toast longPressToast;

    private void showLongPressHint() {
        String previousProject = projectManager.getPreviousProject();
        if (previousProject == null) {
            longPressToast = Toast.makeText(this, "没有上一个项目", Toast.LENGTH_SHORT);
        } else {
            longPressToast = Toast.makeText(this, 
                "长按2秒返回: " + previousProject, 
                Toast.LENGTH_LONG);
        }
        longPressToast.show();
    }

    /**
     * 隐藏长按提示
     */
    private void hideLongPressHint() {
        if (longPressToast != null) {
            longPressToast.cancel();
        }
    }

    /**
     * 切换到上一个项目（长按触发）
     */
    private void switchToPreviousProject() {
        String previousProject = projectManager.getPreviousProject();
        
        // 如果变量为空，不予理睬
        if (previousProject == null) {
            Toast.makeText(this, "没有上一个项目", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查上一个项目是否还存在
        List<String> projects = projectManager.getProjectList();
        if (!projects.contains(previousProject)) {
            Toast.makeText(this, "上一个项目 \"" + previousProject + "\" 已不存在", 
                Toast.LENGTH_SHORT).show();
            // 清除无效的上一个项目记录
            projectManager.clearPreviousProject();
            return;
        }
        
        // 切换到上一个项目
        // 注意：switchProject 内部会更新 previousProjectName
        switchProject(previousProject);
        
        // 显示提示
        Toast.makeText(this, "已返回: " + previousProject, Toast.LENGTH_SHORT).show();
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
            fileName = projectManager.getCurrentProject() + "_noteplus_export.csv";
            mimeType = "text/csv";
            fileExtension = ".csv";
        } else if (format.contains("JSON")) {
            fileName = projectManager.getCurrentProject() + "_noteplus_export.json";
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
        importExportManager.writeCsvData(outputStream);
    }

    /**
     * 写入JSON数据
     */
    private void writeJsonData(OutputStream outputStream) throws IOException, JSONException {
        importExportManager.writeJsonData(outputStream);
    }

    /**
     * 三档切换输入模式：普通 -> 展开 -> 全屏 -> 普通
     */
    private void toggleInputMode() {
        ImageButton expandButton = findViewById(R.id.expandButton);
        
        // 循环切换到下一个模式
        inputMode = (inputMode + 1) % 3;
        
        switch (inputMode) {
            case INPUT_MODE_NORMAL:
                // 普通模式：单行输入框
                momentEditText.setMinLines(1);
                momentEditText.setMaxLines(3);
                expandButton.setImageResource(R.drawable.ic_expand);
                expandButton.setContentDescription("展开输入框");
                break;
                
            case INPUT_MODE_EXPANDED:
                // 展开模式：多行输入框
                momentEditText.setMinLines(4);
                momentEditText.setMaxLines(8);
                // 将光标定位到文本末尾
                momentEditText.setSelection(momentEditText.getText().length());
                // 显示键盘
                momentEditText.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(momentEditText, InputMethodManager.SHOW_IMPLICIT);
                }
                expandButton.setImageResource(R.drawable.ic_fullscreen);
                expandButton.setContentDescription("全屏编辑");
                break;
                
            case INPUT_MODE_FULLSCREEN:
                // 全屏模式：打开全屏编辑对话框
                expandButton.setImageResource(R.drawable.ic_collapse);
                expandButton.setContentDescription("收起输入框");
                showFullscreenEditor();
                break;
        }
    }

    /**
     * 显示全屏编辑对话框
     */
    private void showFullscreenEditor() {
        // 创建全屏对话框
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).create();
        View fullscreenView = getLayoutInflater().inflate(R.layout.dialog_fullscreen_edit, null);
        dialog.setView(fullscreenView);
        
        EditText fullscreenEditText = fullscreenView.findViewById(R.id.fullscreenEditText);
        ImageButton btnExitFullscreen = fullscreenView.findViewById(R.id.btnExitFullscreen);
        Button btnSaveFullscreen = fullscreenView.findViewById(R.id.btnSaveFullscreen);
        
        // 复制内容
        fullscreenEditText.setText(momentEditText.getText().toString());
        if (fullscreenEditText.getText().length() > 0) {
            fullscreenEditText.setSelection(fullscreenEditText.getText().length());
        }
        
        // 退出按钮
        btnExitFullscreen.setOnClickListener(v -> {
            momentEditText.setText(fullscreenEditText.getText().toString());
            dialog.dismiss();
        });
        
        // 保存按钮
        btnSaveFullscreen.setOnClickListener(v -> {
            momentEditText.setText(fullscreenEditText.getText().toString());
            dialog.dismiss();
            saveMoment();
        });
        
        // 关闭时重置状态
        dialog.setOnDismissListener(d -> {
            inputMode = INPUT_MODE_NORMAL;
            momentEditText.setMinLines(1);
            momentEditText.setMaxLines(3);
            ImageButton expandButton = findViewById(R.id.expandButton);
            expandButton.setImageResource(R.drawable.ic_expand);
            expandButton.setContentDescription("展开输入框");
        });
        
        // 设置窗口属性
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.white);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            // 使用ADJUST_RESIZE让系统自动处理键盘
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        dialog.show();
        
        // 显示键盘
        fullscreenEditText.post(() -> {
            fullscreenEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(fullscreenEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /**
     * 显示删除确认对话框
     * @param noteId 要删除的记录ID
     */
    /**
     * 显示删除确认对话框
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void showDeleteConfirmDialog(long noteId) {
        if (noteListManager != null) {
            noteListManager.showDeleteConfirmDialog(noteId);
        }
    }

    /**
     * 显示笔记选项菜单
     * 已迁移到 NoteListManager，此方法现在委托给 NoteListManager
     */
    private void showNoteOptionsMenu(View anchorView, long noteId) {
        if (noteListManager != null) {
            noteListManager.showNoteOptionsMenu(anchorView, noteId);
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
        
        // 初始化时间区间显示开关
        Switch timeRangeDisplaySwitch = settingsView.findViewById(R.id.switchTimeRangeDisplay);
        String currentTimeRangeDisplayValue = dbHelper.getSetting(NoteDbHelper.KEY_TIME_RANGE_DISPLAY, "false");
        timeRangeDisplaySwitch.setChecked(Boolean.parseBoolean(currentTimeRangeDisplayValue));
        
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
        
        // 初始化折叠显示字数输入框
        EditText foldDisplayLengthEdit = settingsView.findViewById(R.id.editTextFoldDisplayLength);
        String currentFoldLength = dbHelper.getSetting(NoteDbHelper.KEY_FOLD_DISPLAY_LENGTH, "300");
        foldDisplayLengthEdit.setText(currentFoldLength);
        
        // 保存按钮点击事件处理
        builder.setPositiveButton("保存", (dialog, which) -> {
            // 保存时间区间显示设置
            boolean isTimeRangeDisplay = timeRangeDisplaySwitch.isChecked();
            dbHelper.saveSetting(NoteDbHelper.KEY_TIME_RANGE_DISPLAY, String.valueOf(isTimeRangeDisplay));
            
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
            
            // 保存折叠显示字数设置
            String foldLengthStr = foldDisplayLengthEdit.getText().toString().trim();
            int foldLength;
            try {
                foldLength = Integer.parseInt(foldLengthStr);
                // 确保最小值为1
                if (foldLength < 1) {
                    foldLength = 1;
                }
            } catch (NumberFormatException e) {
                // 如果输入无效，使用默认值300
                foldLength = 300;
            }
            dbHelper.saveSetting(NoteDbHelper.KEY_FOLD_DISPLAY_LENGTH, String.valueOf(foldLength));
            
            // 重新加载设置以更新界面
            loadSettings();
            
            // 重新加载列表以应用新的排序设置和折叠设置
            if (noteListManager != null) {
                noteListManager.loadNotes();
            }
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        });
        
        // 取消按钮
        builder.setNegativeButton("取消", null);
        
        builder.show();
    }

    /**
     * 显示 Timeline 对话框
     * 用于查看跨项目的 Note 和 Comment 展平，按时间排序的展示
     */
    private void showTimelineDialog() {
        // 创建自定义对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_timeline, null);
        builder.setView(dialogView);

        // 获取对话框中的视图组件
        ListView timelineListView = dialogView.findViewById(R.id.timelineListView);
        TextView itemCountText = dialogView.findViewById(R.id.timelineItemCount);
        Button closeButton = dialogView.findViewById(R.id.btnCloseTimeline);
        Spinner rangeSpinner = dialogView.findViewById(R.id.timelineRangeSpinner);
        Button sortButton = dialogView.findViewById(R.id.timelineSortButton);

        // 创建对话框
        AlertDialog dialog = builder.create();
        
        // 设置关闭按钮点击事件
        closeButton.setOnClickListener(v -> dialog.dismiss());

        // 初始化时间范围选择器
        String[] rangeOptions = {"最近1周", "最近2周", "最近1个月", "最近3个月"};
        ArrayAdapter<String> rangeAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, rangeOptions);
        rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rangeSpinner.setAdapter(rangeAdapter);
        
        // 读取用户偏好（默认顺序，即false）
        SharedPreferences prefs = getSharedPreferences(PREFS_TIMELINE, MODE_PRIVATE);
        int savedRangePosition = prefs.getInt(KEY_TIMELINE_RANGE, 0); // 默认最近1周
        boolean savedSortDescending = prefs.getBoolean(KEY_TIMELINE_SORT, false); // 默认顺序
        
        // 应用用户偏好
        rangeSpinner.setSelection(savedRangePosition);
        updateSortButtonText(sortButton, savedSortDescending);
        
        // 获取初始时间范围和排序方向
        TimeRangeFilter initialRange = getTimeRangeFromPosition(savedRangePosition);

        // 监听时间范围变化
        rangeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TimeRangeFilter selectedRange = getTimeRangeFromPosition(position);
                // 从按钮文字判断当前排序方向
                boolean descending = sortButton.getText().toString().equals("逆序");
                
                // 保存偏好
                saveTimelinePreferences(position, descending);
                
                // 重新加载数据
                loadTimelineData(timelineListView, itemCountText, selectedRange, descending);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做处理
            }
        });
        
        // 监听排序按钮点击
        sortButton.setOnClickListener(v -> {
            // 切换排序方向
            boolean currentDescending = sortButton.getText().toString().equals("逆序");
            boolean newDescending = !currentDescending;
            
            // 更新按钮文字
            updateSortButtonText(sortButton, newDescending);
            
            int selectedPosition = rangeSpinner.getSelectedItemPosition();
            TimeRangeFilter selectedRange = getTimeRangeFromPosition(selectedPosition);
            
            // 保存偏好
            saveTimelinePreferences(selectedPosition, newDescending);
            
            // 重新加载数据
            loadTimelineData(timelineListView, itemCountText, selectedRange, newDescending);
        });

        // 初始加载数据（使用用户偏好）
        loadTimelineData(timelineListView, itemCountText, initialRange, savedSortDescending);
        
        // 显示对话框
        dialog.show();
        
        // 设置对话框窗口大小
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
    }
    
    /**
     * 更新排序按钮的文字
     * 
     * @param button 排序按钮
     * @param descending 是否逆序（true=逆序，false=顺序）
     */
    private void updateSortButtonText(Button button, boolean descending) {
        button.setText(descending ? "逆序" : "顺序");
    }
    
    /**
     * 保存时间线用户偏好
     * 
     * @param rangePosition 时间范围位置
     * @param descending 是否逆序
     */
    private void saveTimelinePreferences(int rangePosition, boolean descending) {
        SharedPreferences prefs = getSharedPreferences(PREFS_TIMELINE, MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_TIMELINE_RANGE, rangePosition)
            .putBoolean(KEY_TIMELINE_SORT, descending)
            .apply();
    }
    
    /**
     * 处理时间轴项的双击事件
     * 
     * @param item 被双击的时间轴项（Comment对象）
     */
    private void handleTimelineDoubleClick(Comment item) {
        // 跳过日期分割线项
        if (item.getItemType() == person.notfresh.noteplus.core.model.TimelineItemType.DATE_DIVIDER) {
            return;
        }
        
        // 添加调试日志
        android.util.Log.d("Timeline", "双击检测触发: NoteId=" + item.getNoteId() + ", Project=" + item.getProjectName());
        
        // 获取目标项目名称和Note ID
        String targetProjectName = item.getProjectName();
        long noteId = item.getNoteId();
        
        // 验证数据有效性
        if (targetProjectName == null || targetProjectName.isEmpty()) {
            Toast.makeText(this, "Timeline: 项目名称无效", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (noteId <= 0) {
            Toast.makeText(this, "Timeline: Note ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 直接加载详情，不切换项目（使用getDbHelperForProject查询，不改变当前项目）
        android.util.Log.d("Timeline", "加载详情，项目: " + targetProjectName);
        loadNoteDetail(noteId, targetProjectName);
    }
    
    /**
     * 根据ID加载Note基本信息
     * 
     * @param dbHelper 数据库Helper
     * @param noteId Note ID
     * @return Note对象，如果不存在返回null
     */
    private Note loadNoteById(NoteDbHelper dbHelper, long noteId) {
        if (dbHelper == null) {
            return null;
        }
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{
                    "_id",
                    NoteDbHelper.COLUMN_CONTENT,
                    NoteDbHelper.COLUMN_TIMESTAMP,
                    NoteDbHelper.COLUMN_COST,
                    NoteDbHelper.COLUMN_IS_PINNED
                },
                "_id = ?",
                new String[]{String.valueOf(noteId)},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndexOrThrow("_id");
                int contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
                int timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
                int costIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST);
                int pinnedIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_IS_PINNED);
                
                long id = cursor.getLong(idIndex);
                String content = cursor.getString(contentIndex);
                long timestamp = cursor.getLong(timestampIndex);
                double cost = cursor.getDouble(costIndex);
                boolean isPinned = pinnedIndex >= 0 && cursor.getInt(pinnedIndex) == 1;
                
                String projectName = projectManager.getCurrentProject();
                return new Note(id, content, timestamp, cost, isPinned, projectName);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
    
    /**
     * 加载Note的所有Comment
     * 
     * @param dbHelper 数据库Helper
     * @param noteId Note ID
     * @param projectName 项目名称
     * @return Comment列表，按时间正序排序
     */
    private List<Comment> loadCommentsForNote(NoteDbHelper dbHelper, long noteId, String projectName) {
        List<Comment> comments = new ArrayList<>();
        if (dbHelper == null) {
            return comments;
        }
        
        Cursor cursor = null;
        try {
            cursor = dbHelper.getCommentsForNote(noteId);
            if (cursor != null) {
                // 注意：getCommentsForNote 返回的 Cursor 不包含 COLUMN_COMMENT_NOTE_ID 列
                // 因为我们已经在查询条件中使用了 noteId，所以不需要从 Cursor 中读取
                int commentIdIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID);
                int parentCommentIdIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                int contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT);
                int timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP);
                int costIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST);
                
                while (cursor.moveToNext()) {
                    long commentId = cursor.getLong(commentIdIndex);
                    Long parentCommentId = null;
                    if (parentCommentIdIndex >= 0 && !cursor.isNull(parentCommentIdIndex)) {
                        parentCommentId = cursor.getLong(parentCommentIdIndex);
                    }
                    String content = cursor.getString(contentIndex);
                    long timestamp = cursor.getLong(timestampIndex);
                    double cost = cursor.getDouble(costIndex);
                    
                    // 使用传入的 noteId 参数，而不是从 Cursor 中读取
                    Comment comment = new Comment(
                        commentId,
                        noteId,  // 使用方法参数中的 noteId
                        parentCommentId,
                        content,
                        timestamp,
                        cost,
                        projectName,
                        person.notfresh.noteplus.core.model.TimelineItemType.COMMENT
                    );
                    comments.add(comment);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return comments;
    }
    
    /**
     * 加载Note的标签
     * 
     * @param dbHelper 数据库Helper
     * @param noteId Note ID
     * @return 标签Cursor，如果不存在返回null
     */
    private Cursor loadTagsForNote(NoteDbHelper dbHelper, long noteId) {
        if (dbHelper == null) {
            return null;
        }
        return dbHelper.getTagsForNote(noteId);
    }
    
    /**
     * 加载Note详情（包括Note基本信息、所有Comment和标签）
     * 
     * @param noteId Note ID
     * @param projectName 项目名称
     */
    private void loadNoteDetail(long noteId, String projectName) {
        // 直接在后台线程加载，不显示进度提示
        new Thread(() -> {
            try {
                // 获取数据库Helper
                NoteDbHelper dbHelper = projectManager.getDbHelperForProject(projectName);
                if (dbHelper == null) {
                    throw new Exception("Timeline: 项目不存在: " + projectName);
                }
                
                // 加载Note基本信息
                Note note = loadNoteById(dbHelper, noteId);
                if (note == null) {
                    throw new Exception("Timeline: Note不存在: " + noteId);
                }
                
                // 加载Comment列表
                List<Comment> comments = loadCommentsForNote(dbHelper, noteId, projectName);
                
                // 加载标签
                Cursor tagsCursor = loadTagsForNote(dbHelper, noteId);
                
                // 在UI线程中显示对话框
                final Note finalNote = note;
                final List<Comment> finalComments = comments;
                final Cursor finalTagsCursor = tagsCursor;
                
                runOnUiThread(() -> {
                    // 直接显示详情对话框，不延迟
                    showNoteDetailDialog(finalNote, finalComments, finalTagsCursor);
                });
            } catch (Exception e) {
                // 使用Log记录完整异常信息，包括堆栈，便于通过"Timeline"标签过滤
                android.util.Log.e("Timeline", "Timeline: 加载Note详情失败", e);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "Timeline: 加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * 显示Note详情对话框
     * 
     * @param note Note对象
     * @param comments Comment列表
     * @param tagsCursor 标签Cursor
     */
    private void showNoteDetailDialog(Note note, List<Comment> comments, Cursor tagsCursor) {
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_note_detail, null);
        builder.setView(dialogView);
        
        // 获取UI组件
        TextView detailTitle = dialogView.findViewById(R.id.detailTitle);
        Button btnCloseDetail = dialogView.findViewById(R.id.btnCloseDetail);
        Button btnCloseDetail2 = dialogView.findViewById(R.id.btnCloseDetail2);
        TextView detailProjectName = dialogView.findViewById(R.id.detailProjectName);
        TextView detailNoteContent = dialogView.findViewById(R.id.detailNoteContent);
        TextView detailNoteTime = dialogView.findViewById(R.id.detailNoteTime);
        TextView detailNoteCost = dialogView.findViewById(R.id.detailNoteCost);
        LinearLayout detailTagsContainer = dialogView.findViewById(R.id.detailTagsContainer);
        ListView detailCommentList = dialogView.findViewById(R.id.detailCommentList);
        Button btnAddComment = dialogView.findViewById(R.id.btnAddComment);
        // 查找ScrollView（使用View类型避免R.id未生成的编译错误）
        ScrollView noteContentScrollView = null;
        try {
            View scrollViewView = dialogView.findViewById(dialogView.getResources().getIdentifier("noteContentScrollView", "id", getPackageName()));
            if (scrollViewView instanceof ScrollView) {
                noteContentScrollView = (ScrollView) scrollViewView;
            }
        } catch (Exception e) {
            // 如果通过ID找不到，通过遍历查找
            ViewGroup rootView = (ViewGroup) dialogView;
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View child = rootView.getChildAt(i);
                if (child instanceof ScrollView) {
                    noteContentScrollView = (ScrollView) child;
                    break;
                }
            }
        }
        // 查找Comment列表容器
        LinearLayout commentListContainer = null;
        try {
            View containerView = dialogView.findViewById(dialogView.getResources().getIdentifier("commentListContainer", "id", getPackageName()));
            if (containerView instanceof LinearLayout) {
                commentListContainer = (LinearLayout) containerView;
            }
        } catch (Exception e) {
            // 如果通过ID找不到，通过ListView的父容器查找
            if (detailCommentList != null && detailCommentList.getParent() instanceof LinearLayout) {
                commentListContainer = (LinearLayout) detailCommentList.getParent();
            }
        }
        
        // 创建对话框
        AlertDialog dialog = builder.create();
        
        // 设置标题
        detailTitle.setText("Note详情");
        
        // 设置项目名称
        detailProjectName.setText(note.getProjectName());
        
        // 设置Note内容（如果有置顶标识，保留）
        String content = note.getContent();
        if (note.isPinned()) {
            content = "📌 " + content;
        }
        detailNoteContent.setText(content);
        
        // 设置时间
        detailNoteTime.setText(person.notfresh.noteplus.util.DisplayUtil.formatTimestamp(note.getTimestamp()));
        
        // 设置花费（如果有）
        if (note.getCost() > 0) {
            detailNoteCost.setText("花费：" + note.getCost() + "元");
            detailNoteCost.setVisibility(View.VISIBLE);
        } else {
            detailNoteCost.setVisibility(View.GONE);
        }
        
        // 加载并显示标签
        detailTagsContainer.removeAllViews();
        if (tagsCursor != null && tagsCursor.getCount() > 0) {
            detailTagsContainer.setVisibility(View.VISIBLE);
            tagsCursor.moveToPosition(-1); // 重置到开始位置
            while (tagsCursor.moveToNext()) {
                @SuppressLint("Range") String tagName = tagsCursor.getString(
                    tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                @SuppressLint("Range") String tagColor = tagsCursor.getString(
                    tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                
                // 创建标签 TextView
                TextView tagView = new TextView(this);
                tagView.setText(tagName);
                tagView.setPadding(
                    person.notfresh.noteplus.util.DisplayUtil.dpToPx(this, 6), 
                    person.notfresh.noteplus.util.DisplayUtil.dpToPx(this, 2), 
                    person.notfresh.noteplus.util.DisplayUtil.dpToPx(this, 6), 
                    person.notfresh.noteplus.util.DisplayUtil.dpToPx(this, 2)
                );
                tagView.setTextColor(Color.WHITE);
                tagView.setTextSize(11);
                
                try {
                    tagView.setBackgroundColor(Color.parseColor(tagColor));
                } catch (Exception e) {
                    tagView.setBackgroundColor(Color.GRAY);
                }
                
                // 设置margin
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 
                    person.notfresh.noteplus.util.DisplayUtil.dpToPx(this, 4), 0);
                tagView.setLayoutParams(params);
                
                detailTagsContainer.addView(tagView);
            }
        } else {
            detailTagsContainer.setVisibility(View.GONE);
        }
        
        // 设置Comment列表适配器
        NoteDetailCommentAdapter commentAdapter = new NoteDetailCommentAdapter(comments);
        detailCommentList.setAdapter(commentAdapter);
        
        // 设置关闭按钮事件
        btnCloseDetail.setOnClickListener(v -> {
            if (tagsCursor != null && !tagsCursor.isClosed()) {
                tagsCursor.close();
            }
            dialog.dismiss();
        });
        btnCloseDetail2.setOnClickListener(v -> {
            if (tagsCursor != null && !tagsCursor.isClosed()) {
                tagsCursor.close();
            }
            dialog.dismiss();
        });
        
        // 设置追加内容按钮事件
        btnAddComment.setOnClickListener(v -> {
            // TODO: 实现追加内容功能（可选）
            Toast.makeText(this, "追加内容功能待实现", Toast.LENGTH_SHORT).show();
        });
        
        // 显示对话框
        dialog.show();
        
        // 设置对话框窗口大小（与时间线对话框一致：90%宽度，80%高度）
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        // 现在整个内容都在ScrollView中，可以自然滚动
        // ListView在ScrollView中需要设置固定高度才能正确显示
        if (detailCommentList != null && commentListContainer != null) {
            dialogView.post(() -> {
                // 等待ListView渲染完成后计算高度
                detailCommentList.post(() -> {
                    if (detailCommentList.getAdapter() != null && detailCommentList.getAdapter().getCount() > 0) {
                        // 计算ListView的总高度
                        int totalHeight = 0;
                        int dividerHeight = detailCommentList.getDividerHeight();
                        for (int i = 0; i < detailCommentList.getChildCount(); i++) {
                            View child = detailCommentList.getChildAt(i);
                            if (child != null) {
                                totalHeight += child.getMeasuredHeight();
                                if (i > 0) {
                                    totalHeight += dividerHeight;
                                }
                            }
                        }
                        // 如果计算出的高度大于0，设置ListView的高度
                        if (totalHeight > 0) {
                            ViewGroup.LayoutParams params = detailCommentList.getLayoutParams();
                            if (params != null) {
                                params.height = totalHeight;
                                detailCommentList.setLayoutParams(params);
                            }
                        }
                    }
                });
            });
        }
    }
    
    /**
     * Note详情对话框的Comment列表适配器
     */
    private class NoteDetailCommentAdapter extends android.widget.BaseAdapter {
        private final List<Comment> comments;
        
        public NoteDetailCommentAdapter(List<Comment> comments) {
            this.comments = comments;
        }
        
        @Override
        public int getCount() {
            return comments.size();
        }
        
        @Override
        public Object getItem(int position) {
            return comments.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_note_detail_comment, parent, false);
            }
            
            Comment comment = comments.get(position);
            
            TextView commentTime = convertView.findViewById(R.id.commentTime);
            TextView commentContent = convertView.findViewById(R.id.commentContent);
            TextView commentCost = convertView.findViewById(R.id.commentCost);
            
            // 设置时间
            commentTime.setText(person.notfresh.noteplus.util.DisplayUtil.formatCommentTimestamp(comment.getTimestamp()));
            
            // 设置内容
            commentContent.setText(comment.getContent());
            
            // 设置花费（如果有）
            if (comment.getCost() > 0) {
                commentCost.setText("花费：" + comment.getCost() + "元");
                commentCost.setVisibility(View.VISIBLE);
            } else {
                commentCost.setVisibility(View.GONE);
            }
            
            return convertView;
        }
    }
    
    /**
     * 根据Spinner位置获取对应的时间范围枚举
     * 
     * @param position Spinner的选中位置
     * @return 对应的时间范围枚举
     */
    private TimeRangeFilter getTimeRangeFromPosition(int position) {
        switch (position) {
            case 0:
                return TimeRangeFilter.LAST_WEEK;      // 最近1周
            case 1:
                return TimeRangeFilter.LAST_TWO_WEEKS;  // 最近2周
            case 2:
                return TimeRangeFilter.LAST_MONTH;      // 最近1个月
            case 3:
                return TimeRangeFilter.LAST_THREE_MONTHS; // 最近3个月
            default:
                return TimeRangeFilter.LAST_WEEK;
        }
    }
    
    /**
     * 加载时间线数据
     * 
     * @param listView 列表视图
     * @param countText 项目数量文本视图
     * @param timeRange 时间范围
     * @param descending 是否逆序（true表示最新的在前）
     */
    private void loadTimelineData(ListView listView, TextView countText, 
                                  TimeRangeFilter timeRange, boolean descending) {
        // 加载时间线数据（在后台线程中执行）
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在加载时间线...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // 记录开始时间，确保至少显示0.5秒
        long startTime = System.currentTimeMillis();
        final long MIN_DISPLAY_TIME = 500; // 最少显示时间（毫秒）
        
        new Thread(() -> {
            try {
                // 创建 GlobalTimeline 实例
                GlobalTimeline globalTimeline = new GlobalTimeline(projectManager);
                
                // 加载指定时间范围的数据
                final List<Comment> timelineItems = globalTimeline.loadGlobalTimeline(timeRange, descending);
                
                // 计算已用时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                long remainingTime = Math.max(0, MIN_DISPLAY_TIME - elapsedTime);
                
                // 返回UI线程更新界面
                runOnUiThread(() -> {
                    // 更新UI的Runnable
                    Runnable updateUIRunnable = () -> {
                        progressDialog.dismiss();
                        
                        // 更新项目数量
                        countText.setText(timelineItems.size() + " 项");
                        
                        // 创建适配器
                        TimelineAdapter adapter = new TimelineAdapter(timelineItems);
                        listView.setAdapter(adapter);
                        
                        // 清除之前的延迟任务和状态（防止干扰）
                        timelineClickHandler.removeCallbacks(timelineClickRunnable);
                        lastTimelineClickTime = 0;
                        lastTimelineClickPosition = -1;
                        
                        // 用于存储待处理的点击项（用于双击检测）
                        final Comment[] pendingClickItem = {null};
                        
                        android.util.Log.d("Timeline", "设置点击事件监听器，数据项数量: " + timelineItems.size());
                        
                        // 确保ListView可以接收点击事件
                        listView.setClickable(true);
                        listView.setFocusable(true);
                        listView.setItemsCanFocus(false); // 确保item不会获取焦点，让ListView处理点击
                        
                        // 设置双击检测点击事件
                        listView.setOnItemClickListener((parent, view, position, id) -> {
                            android.util.Log.d("Timeline", "点击事件触发: position=" + position + ", listSize=" + timelineItems.size());
                            
                            // 检查位置是否有效
                            if (position < 0 || position >= timelineItems.size()) {
                                android.util.Log.w("Timeline", "无效的位置: " + position);
                                return;
                            }
                            
                            Comment item = timelineItems.get(position);
                            
                            // 跳过日期分割线项
                            if (item.getItemType() == person.notfresh.noteplus.core.model.TimelineItemType.DATE_DIVIDER) {
                                android.util.Log.d("Timeline", "跳过日期分割线项");
                                return;
                            }
                            
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastClick = lastTimelineClickTime > 0 ? currentTime - lastTimelineClickTime : Long.MAX_VALUE;
                            
                            android.util.Log.d("Timeline", "点击事件: position=" + position + 
                                ", lastPosition=" + lastTimelineClickPosition + 
                                ", timeSinceLastClick=" + timeSinceLastClick + 
                                ", DOUBLE_CLICK_DELAY=" + DOUBLE_CLICK_DELAY +
                                ", noteId=" + item.getNoteId() +
                                ", project=" + item.getProjectName());
                            
                            // 如果距离上次点击时间小于双击间隔且位置相同，认为是双击
                            if (lastTimelineClickTime > 0 && 
                                timeSinceLastClick < DOUBLE_CLICK_DELAY && 
                                lastTimelineClickPosition == position &&
                                pendingClickItem[0] != null) {
                                // 取消单击延迟执行
                                timelineClickHandler.removeCallbacks(timelineClickRunnable);
                                
                                android.util.Log.d("Timeline", "检测到双击！准备调用handleTimelineDoubleClick");
                                
                                // 执行双击操作
                                handleTimelineDoubleClick(item);
                                
                                // 重置状态
                                lastTimelineClickTime = 0;
                                lastTimelineClickPosition = -1;
                                pendingClickItem[0] = null;
                            } else {
                                // 取消之前的延迟任务（如果有）
                                timelineClickHandler.removeCallbacks(timelineClickRunnable);
                                
                                android.util.Log.d("Timeline", "第一次点击，等待双击...");
                                
                                // 记录点击时间和位置
                                lastTimelineClickTime = currentTime;
                                lastTimelineClickPosition = position;
                                pendingClickItem[0] = item;
                                
                                // 延迟执行单击操作（如果在这期间没有双击）
                                timelineClickRunnable = () -> {
                                    android.util.Log.d("Timeline", "单击延迟执行，未检测到双击");
                                    // 临时测试：单击也触发详情查看（用于调试）
                                    // TODO: 正式版本应该移除这个，只支持双击
                                    android.util.Log.d("Timeline", "临时测试：单击也触发详情查看");
                                    handleTimelineDoubleClick(pendingClickItem[0]);
                                    
                                    lastTimelineClickTime = 0;
                                    lastTimelineClickPosition = -1;
                                    pendingClickItem[0] = null;
                                };
                                timelineClickHandler.postDelayed(timelineClickRunnable, DOUBLE_CLICK_DELAY);
                            }
                        });
                        
                        android.util.Log.d("Timeline", "点击事件监听器设置完成");
                    };
                    
                    // 如果还没到最少显示时间，延迟关闭
                    if (remainingTime > 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(updateUIRunnable, remainingTime);
                    } else {
                        // 已经超过最少显示时间，立即关闭
                        updateUIRunnable.run();
                    }
                });
            } catch (Exception e) {
                // 使用Log记录完整异常信息，包括堆栈，便于通过"Timeline"标签过滤
                android.util.Log.e("Timeline", "Timeline: 加载时间线数据失败", e);
                
                // 计算已用时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                long remainingTime = Math.max(0, MIN_DISPLAY_TIME - elapsedTime);
                
                runOnUiThread(() -> {
                    Runnable errorRunnable = () -> {
                        progressDialog.dismiss();
                        
                        // 即使加载失败，也要设置一个空的点击事件监听器，防止后续点击无效
                        // 清除之前的延迟任务和状态
                        timelineClickHandler.removeCallbacks(timelineClickRunnable);
                        lastTimelineClickTime = 0;
                        lastTimelineClickPosition = -1;
                        
                        // 设置一个空的点击事件监听器（至少不会报错）
                        listView.setOnItemClickListener((parent, view, position, id) -> {
                            // 数据加载失败，无法处理点击
                        });
                        
                        Toast.makeText(this, "Timeline: 加载时间线失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    };
                    
                    if (remainingTime > 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(errorRunnable, remainingTime);
                    } else {
                        errorRunnable.run();
                    }
                });
            }
        }).start();
    }
    
    /**
     * Timeline 列表适配器
     */
    private class TimelineAdapter extends android.widget.BaseAdapter {
        private final List<Comment> items;
        
        public TimelineAdapter(List<Comment> items) {
            this.items = items;
        }
        
        @Override
        public int getCount() {
            return items.size();
        }
        
        @Override
        public Object getItem(int position) {
            return items.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Comment comment = items.get(position);
            
            // 判断是否为日期分割线
            boolean isDateDivider = comment.getItemType() == person.notfresh.noteplus.core.model.TimelineItemType.DATE_DIVIDER;
            
            View view = convertView;
            // 如果 convertView 类型不匹配，需要重新创建
            if (view == null || (isDateDivider && view.findViewById(R.id.dateDividerText) == null) ||
                (!isDateDivider && view.findViewById(R.id.timelineProjectName) == null)) {
                if (isDateDivider) {
                    view = getLayoutInflater().inflate(R.layout.item_timeline_date_divider, parent, false);
                } else {
                    view = getLayoutInflater().inflate(R.layout.item_timeline, parent, false);
                }
            }
            
            // 如果是日期分割线，直接设置日期文本并返回
            if (isDateDivider) {
                TextView dateDividerText = view.findViewById(R.id.dateDividerText);
                if (dateDividerText != null) {
                    String dateText = comment.getContent();
                    if (dateText == null || dateText.isEmpty()) {
                        dateText = DisplayUtil.formatTimestamp(comment.getTimestamp());
                    }
                    dateDividerText.setText(dateText);
                }
                return view;
            }
            
            // 普通时间线项的原有逻辑
            // 获取视图组件
            TextView projectNameText = view.findViewById(R.id.timelineProjectName);
            TextView itemTypeText = view.findViewById(R.id.timelineItemType);
            TextView timeText = view.findViewById(R.id.timelineTime);
            TextView contentText = view.findViewById(R.id.timelineContent);
            LinearLayout tagsContainer = view.findViewById(R.id.timelineTagsContainer);
            
            // 设置项目名称
            String projectName = comment.getProjectName();
            if (projectName == null || projectName.isEmpty()) {
                projectName = "默认项目";
            }
            projectNameText.setText(projectName);
            
            // 判断是 Note 还是 Comment
            // 使用 itemType 字段来判断，而不是 parentCommentId
            // 因为第一条 Comment 的 parentCommentId 也可能是 null
            boolean isNote = comment.getItemType() == person.notfresh.noteplus.core.model.TimelineItemType.NOTE;
            
            // 设置类型标签
            if (isNote) {
                itemTypeText.setText("Note");
                itemTypeText.setBackgroundColor(Color.parseColor("#2196F3")); // 蓝色
            } else {
                itemTypeText.setText("Comment");
                itemTypeText.setBackgroundColor(Color.parseColor("#4CAF50")); // 绿色
            }
            
            // 设置时间
            timeText.setText(DisplayUtil.formatTimestamp(comment.getTimestamp()));
            
            // 设置内容预览（最多200字符）
            String content = comment.getContent();
            if (content == null) {
                content = "";
            }
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            
            // 如果是置顶的Note，在内容前添加置顶标识
            if (isNote && comment.isPinned()) {
                // 检查是否已经包含置顶标识，避免重复添加
                if (!content.startsWith("📌 ")) {
                    content = "📌 " + content;
                }
            }
            
            contentText.setText(content);
            
            // 加载标签（只有 Note 才显示标签）
            tagsContainer.removeAllViews();
            if (isNote) {
                long noteId = comment.getNoteId();
                // 需要获取对应项目的 dbHelper
                String project = comment.getProjectName();
                if (project != null && !project.isEmpty()) {
                    Cursor tagsCursor = null;
                    try {
                        NoteDbHelper dbHelper = projectManager.getDbHelperForProject(project);
                        if (dbHelper != null) {
                            tagsCursor = dbHelper.getTagsForNote(noteId);
                            if (tagsCursor != null && tagsCursor.getCount() > 0) {
                                tagsContainer.setVisibility(View.VISIBLE);
                                while (tagsCursor.moveToNext()) {
                                    @SuppressLint("Range") String tagName = tagsCursor.getString(
                                        tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
                                    @SuppressLint("Range") String tagColor = tagsCursor.getString(
                                        tagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
                                    
                                    // 创建标签 TextView
                                    TextView tagView = new TextView(MainActivity.this);
                                    tagView.setText(tagName);
                                    tagView.setPadding(DisplayUtil.dpToPx(MainActivity.this, 6), DisplayUtil.dpToPx(MainActivity.this, 2), DisplayUtil.dpToPx(MainActivity.this, 6), DisplayUtil.dpToPx(MainActivity.this, 2));
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
                                    params.setMargins(0, 0, DisplayUtil.dpToPx(MainActivity.this, 4), 0);
                                    tagView.setLayoutParams(params);
                                    
                                    tagsContainer.addView(tagView);
                                }
                            } else {
                                tagsContainer.setVisibility(View.GONE);
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
            } else {
                tagsContainer.setVisibility(View.GONE);
            }
            
            return view;
        }
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
        // format字符串 "从CSV导入" 或 "从JSON导入"
        if (format.contains("CSV")) {
            // 使用通配符，让用户选择所有文件，然后通过文件扩展名.csv来识别
            // 这样可以确保CSV文件在任何文件管理器中都能被选择
            intent.setType("*/*");
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
                
                // 更新导入导出管理器
                importExportManager = new person.notfresh.noteplus.manager.ImportExportManager(
                    MainActivity.this, dbHelper, projectManager);
                
                // 回到主线程更新UI并开始导入
                runOnUiThread(() -> {
                    updateTitle();
                    clearForm();
                    
                    // 重新加载新项目的设置
                    loadSettings();
                    
                    // 加载新项目的折叠状态
                    if (noteListManager != null) {
                        noteListManager.loadFoldedNoteIds();
                    }
                    
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
                person.notfresh.noteplus.manager.ImportExportManager.ImportResult result = 
                    importExportManager.readCsvData(uri);
                
                final int finalImportedCount = result.importedCount;
                final int finalSkippedCount = result.skippedCount;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        String currentProject = projectManager.getCurrentProject();
                        String message = String.format("导入完成！成功导入 %d 条记录到项目 \"%s\"", finalImportedCount, currentProject);
                        if (finalSkippedCount > 0) {
                            message += String.format("，跳过 %d 条记录", finalSkippedCount);
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        if (noteListManager != null) {
                noteListManager.loadNotes();
            } // 刷新列表
                    });
                
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
                person.notfresh.noteplus.manager.ImportExportManager.ImportResult result = 
                    importExportManager.readJsonData(uri);
                
                final int finalImportedCount = result.importedCount;
                final int finalSkippedCount = result.skippedCount;
                    
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        String currentProject = projectManager.getCurrentProject();
                        String message = String.format("导入完成！成功导入 %d 条记录到项目 \"%s\"", finalImportedCount, currentProject);
                        if (finalSkippedCount > 0) {
                            message += String.format("，跳过 %d 条记录", finalSkippedCount);
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        if (noteListManager != null) {
                noteListManager.loadNotes();
            } // 刷新列表
                    });
                
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
     * 切换多选模式
     */
    private void toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode;
        
        // 如果退出多选模式，清空选中项
        if (!isMultiSelectMode) {
            selectedNoteIds.clear();
        }
        
        if (noteListManager != null) {
            noteListManager.setMultiSelectMode(isMultiSelectMode);
        }
        updateMultiSelectMenu();
        invalidateOptionsMenu(); // 刷新菜单
    }

    /**
     * 退出多选模式
     */
    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        if (noteListManager != null) {
            noteListManager.setMultiSelectMode(false);
        }
        updateMultiSelectMenu();
        invalidateOptionsMenu(); // 刷新菜单
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

        // 直接从projectManager获取项目列表（已排序）
        List<String> finalProjects = new ArrayList<>();
        try {
            List<String> existingProjects = projectManager.getProjectList();
            if (existingProjects != null && !existingProjects.isEmpty()) {
                finalProjects.addAll(existingProjects);
            } else {
                finalProjects.add("default");
            }
        } catch (Exception e) {
            e.printStackTrace();
            finalProjects.add("default");
        }
        
        // 确保默认项目存在
        if (!finalProjects.contains("default")) {
            finalProjects.add("default");
        }
        
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
                                    
                                    // 5. 复制评论（追加内容）
                                    // 由于评论可能有嵌套关系，需要维护ID映射
                                    Map<Long, Long> commentIdMap = new HashMap<>();
                                    Cursor commentsCursor = sourceDb.query(
                                        NoteDbHelper.TABLE_NOTE_COMMENTS,
                                        new String[]{
                                            NoteDbHelper.COLUMN_COMMENT_ID,
                                            NoteDbHelper.COLUMN_PARENT_COMMENT_ID,
                                            NoteDbHelper.COLUMN_COMMENT_CONTENT,
                                            NoteDbHelper.COLUMN_COMMENT_TIMESTAMP,
                                            NoteDbHelper.COLUMN_COMMENT_COST
                                        },
                                        NoteDbHelper.COLUMN_COMMENT_NOTE_ID + " = ?",
                                        new String[]{String.valueOf(noteId)},
                                        null, null,
                                        NoteDbHelper.COLUMN_COMMENT_TIMESTAMP + " ASC"  // 按时间正序，确保父评论先复制
                                    );
                                    
                                    while (commentsCursor.moveToNext()) {
                                        long oldCommentId = commentsCursor.getLong(
                                            commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID));
                                        int parentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                                        String commentContent = commentsCursor.getString(
                                            commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
                                        long commentTimestamp = commentsCursor.getLong(
                                            commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP));
                                        double commentCost = commentsCursor.getDouble(
                                            commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST));
                                        
                                        // 获取父评论ID（可能为NULL）
                                        Long oldParentCommentId = null;
                                        if (!commentsCursor.isNull(parentIdIndex)) {
                                            oldParentCommentId = commentsCursor.getLong(parentIdIndex);
                                        }
                                        
                                        // 映射父评论ID到新的ID
                                        Long newParentCommentId = null;
                                        if (oldParentCommentId != null) {
                                            newParentCommentId = commentIdMap.get(oldParentCommentId);
                                            // 如果找不到映射，说明数据有问题，跳过这条评论
                                            if (newParentCommentId == null) {
                                                continue;
                                            }
                                        }
                                        
                                        // 插入评论到目标数据库
                                        ContentValues commentValues = new ContentValues();
                                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_NOTE_ID, newNoteId);
                                        if (newParentCommentId != null) {
                                            commentValues.put(NoteDbHelper.COLUMN_PARENT_COMMENT_ID, newParentCommentId);
                                        }
                                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_CONTENT, commentContent);
                                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP, commentTimestamp);
                                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_COST, commentCost);
                                        
                                        long newCommentId = targetDb.insert(NoteDbHelper.TABLE_NOTE_COMMENTS, null, commentValues);
                                        if (newCommentId != -1) {
                                            // 保存ID映射
                                            commentIdMap.put(oldCommentId, newCommentId);
                                        }
                                    }
                                    commentsCursor.close();
                                    
                                    // 6. 从源数据库删除记录
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
                        if (noteListManager != null) {
                noteListManager.loadNotes();
            }
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
    
    // ========== INoteListCallback 接口实现 ==========
    
    @Override
    public NoteDbHelper getDbHelper() {
        return dbHelper;
    }
    
    @Override
    public ProjectContextManager getProjectManager() {
        return projectManager;
    }
    
    @Override
    public Context getContext() {
        return this;
    }
    
    @Override
    public boolean getShowCost() {
        return showCost;
    }
    
    @Override
    public boolean getShowTimeRange() {
        return showTimeRange;
    }
    
    @Override
    public boolean getTimeDescOrder() {
        return timeDescOrder;
    }
    
    @Override
    public String getSetting(String key, String defaultValue) {
        if (dbHelper != null) {
            return dbHelper.getSetting(key, defaultValue);
        }
        return defaultValue;
    }
    
    @Override
    public void onMultiSelectChanged(Set<Long> selectedIds) {
        this.selectedNoteIds = selectedIds;
        // 只在多选模式下才根据选中项更新状态
        // 如果已经退出多选模式，不要自动重新进入
        if (isMultiSelectMode) {
            // 多选模式下，如果选中项为空，保持多选模式状态（用户可能还没选择）
            // 状态由 toggleMultiSelectMode() 控制，这里只更新选中项
        }
        // 更新菜单显示
        updateMultiSelectMenu();
    }
    
    @Override
    public void onRequestRefreshMenu() {
        invalidateOptionsMenu();
    }
}