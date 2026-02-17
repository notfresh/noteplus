package person.notfresh.noteplus.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import person.notfresh.noteplus.R;
import person.notfresh.noteplus.db.ProjectContextManager;

public class NoteWidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner rangeSpinner;
    private Spinner heightSpinner;
    private Spinner clickActionSpinner;
    private Spinner refreshStrategySpinner;
    private Spinner noteCountSpinner;
    private Button selectProjectsButton;
    private TextView selectedCountText;
    private ListView notesListView;

    private final Map<String, NoteWidgetItem> selectedItems = new LinkedHashMap<>();
    private Set<String> selectedProjects = new HashSet<>();
    private List<NoteWidgetItem> currentNotes = new ArrayList<>();
    private NoteWidgetConfigAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_config);

        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        if (intent != null) {
            appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        rangeSpinner = findViewById(R.id.widgetRangeSpinner);
        heightSpinner = findViewById(R.id.widgetHeightSpinner);
        clickActionSpinner = findViewById(R.id.widgetClickActionSpinner);
        refreshStrategySpinner = findViewById(R.id.widgetRefreshStrategySpinner);
        noteCountSpinner = findViewById(R.id.widgetNoteCountSpinner);
        selectProjectsButton = findViewById(R.id.widgetSelectProjectsButton);
        selectedCountText = findViewById(R.id.widgetSelectedCountText);
        notesListView = findViewById(R.id.widgetNotesList);

        setupSpinners();
        setupButtons();

        loadExistingConfig();
        reloadNotes();
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> rangeAdapter = ArrayAdapter.createFromResource(
                this, R.array.widget_range_options, android.R.layout.simple_spinner_item);
        rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rangeSpinner.setAdapter(rangeAdapter);

        ArrayAdapter<CharSequence> heightAdapter = ArrayAdapter.createFromResource(
                this, R.array.widget_height_options, android.R.layout.simple_spinner_item);
        heightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        heightSpinner.setAdapter(heightAdapter);

        ArrayAdapter<CharSequence> clickActionAdapter = ArrayAdapter.createFromResource(
                this, R.array.widget_click_action_options, android.R.layout.simple_spinner_item);
        clickActionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clickActionSpinner.setAdapter(clickActionAdapter);

        ArrayAdapter<CharSequence> refreshStrategyAdapter = ArrayAdapter.createFromResource(
                this, R.array.widget_refresh_strategy_options, android.R.layout.simple_spinner_item);
        refreshStrategyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        refreshStrategySpinner.setAdapter(refreshStrategyAdapter);

        ArrayAdapter<CharSequence> noteCountAdapter = ArrayAdapter.createFromResource(
                this, R.array.widget_note_count_options, android.R.layout.simple_spinner_item);
        noteCountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        noteCountSpinner.setAdapter(noteCountAdapter);
        noteCountSpinner.setSelection(2); // 默认3条

        noteCountSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int maxCount = position + 1; // 1-5条
                if (adapter != null) {
                    adapter.setMaxSelectionCount(maxCount);
                }
                updateSelectedCount();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        rangeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean needProjectSelection = position == NoteWidgetUpdater.RANGE_SELECTED_PROJECTS;
                selectProjectsButton.setVisibility(needProjectSelection ? View.VISIBLE : View.GONE);
                if (!needProjectSelection) {
                    selectedProjects.clear();
                }
                reloadNotes();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void setupButtons() {
        Button cancelButton = findViewById(R.id.widgetCancelButton);
        Button saveButton = findViewById(R.id.widgetSaveButton);

        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveConfig());

        selectProjectsButton.setOnClickListener(v -> showProjectSelectionDialog());
    }

    private void loadExistingConfig() {
        NoteWidgetUpdater.Config config = NoteWidgetUpdater.loadConfig(this, appWidgetId);
        if (config == null) {
            return;
        }

        rangeSpinner.setSelection(config.rangeType);
        heightSpinner.setSelection(config.heightStyle);
        clickActionSpinner.setSelection(config.clickAction);
        refreshStrategySpinner.setSelection(config.refreshStrategy);
        noteCountSpinner.setSelection(config.noteCount - 1); // 0-based index
        selectedProjects = new HashSet<>(config.selectedProjects);

        for (int i = 0; i < config.noteIds.size(); i++) {
            if (i >= config.projectNames.size()) {
                break;
            }
            NoteWidgetItem item = new NoteWidgetItem(
                    config.noteIds.get(i),
                    config.projectNames.get(i),
                    "",
                    0L
            );
            selectedItems.put(item.getKey(), item);
        }
        updateSelectedCount();
    }

    private void reloadNotes() {
        int rangeType = rangeSpinner.getSelectedItemPosition();
        currentNotes = NoteWidgetDataSource.loadSelectableNotes(this, rangeType, selectedProjects);
        
        // 清理已选择但不存在的笔记（已删除或归档）
        cleanupInvalidSelections();
        
        if (adapter == null) {
            adapter = new NoteWidgetConfigAdapter(this, currentNotes, selectedItems, () -> {
                updateSelectedCount();
            });
            // 设置初始最大数量
            int maxCount = noteCountSpinner.getSelectedItemPosition() + 1;
            adapter.setMaxSelectionCount(maxCount);
            notesListView.setAdapter(adapter);
        } else {
            adapter.updateItems(currentNotes);
        }
    }
    
    private void cleanupInvalidSelections() {
        if (currentNotes == null || selectedItems.isEmpty()) {
            return;
        }
        // 构建当前可用笔记的key集合
        Set<String> availableKeys = new HashSet<>();
        for (NoteWidgetItem note : currentNotes) {
            availableKeys.add(note.getKey());
        }
        // 移除不存在的笔记
        selectedItems.keySet().removeIf(key -> !availableKeys.contains(key));
        updateSelectedCount();
    }

    private void showProjectSelectionDialog() {
        ProjectContextManager projectManager = new ProjectContextManager(this);
        List<String> projects = projectManager.getProjectList();
        if (projects == null || projects.isEmpty()) {
            Toast.makeText(this, "暂无项目可选", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean[] checked = new boolean[projects.size()];
        for (int i = 0; i < projects.size(); i++) {
            checked[i] = selectedProjects.contains(projects.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("选择项目")
                .setMultiChoiceItems(projects.toArray(new String[0]), checked, (dialog, which, isChecked) -> {
                    String projectName = projects.get(which);
                    if (isChecked) {
                        selectedProjects.add(projectName);
                    } else {
                        selectedProjects.remove(projectName);
                    }
                })
                .setPositiveButton("确定", (dialog, which) -> reloadNotes())
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSelectedCount() {
        int count = selectedItems.size();
        int requiredCount = noteCountSpinner.getSelectedItemPosition() + 1;
        selectedCountText.setText("已选择 " + count + " / " + requiredCount);
    }

    private void saveConfig() {
        int noteCount = noteCountSpinner.getSelectedItemPosition() + 1;
        if (selectedItems.size() != noteCount) {
            Toast.makeText(this, "请选择 " + noteCount + " 条笔记", Toast.LENGTH_SHORT).show();
            return;
        }

        List<NoteWidgetItem> items = new ArrayList<>(selectedItems.values());
        int rangeType = rangeSpinner.getSelectedItemPosition();
        int heightStyle = heightSpinner.getSelectedItemPosition();
        int clickAction = clickActionSpinner.getSelectedItemPosition();
        int refreshStrategy = refreshStrategySpinner.getSelectedItemPosition();

        NoteWidgetUpdater.saveConfig(this, appWidgetId, items, rangeType, heightStyle, selectedProjects,
                clickAction, refreshStrategy, noteCount);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        NoteWidgetUpdater.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
