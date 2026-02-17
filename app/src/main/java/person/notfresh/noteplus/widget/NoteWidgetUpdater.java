package person.notfresh.noteplus.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.noteplus.MainActivity;
import person.notfresh.noteplus.R;

public class NoteWidgetUpdater {
    public static final String PREFS_NAME = "note_widget_prefs";

    public static final String EXTRA_NOTE_ID = "widget_note_id";
    public static final String EXTRA_PROJECT_NAME = "widget_project_name";

    public static final int RANGE_CURRENT_PROJECT = 0;
    public static final int RANGE_ALL_PROJECTS = 1;
    public static final int RANGE_SELECTED_PROJECTS = 2;

    public static final int HEIGHT_COMPACT = 0;
    public static final int HEIGHT_STANDARD = 1;
    public static final int HEIGHT_RELAXED = 2;

    public static final int CLICK_ACTION_MAIN_PAGE = 0;
    public static final int CLICK_ACTION_NOTE_DETAIL = 1;

    public static final int REFRESH_MANUAL = 0;
    public static final int REFRESH_AUTO = 1;

    private static final String KEY_NOTE_IDS_PREFIX = "widget_note_ids_";
    private static final String KEY_NOTE_PROJECTS_PREFIX = "widget_note_projects_";
    private static final String KEY_RANGE_PREFIX = "widget_range_";
    private static final String KEY_HEIGHT_PREFIX = "widget_height_";
    private static final String KEY_SELECTED_PROJECTS_PREFIX = "widget_selected_projects_";
    private static final String KEY_CLICK_ACTION_PREFIX = "widget_click_action_";
    private static final String KEY_REFRESH_STRATEGY_PREFIX = "widget_refresh_strategy_";
    private static final String KEY_NOTE_COUNT_PREFIX = "widget_note_count_";

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        if (context == null || appWidgetManager == null) {
            return;
        }

        Config config = loadConfig(context, appWidgetId);
        int noteCount = Math.min(config.noteCount, 5); // 最多5条
        List<NoteWidgetItem> items = new ArrayList<>();
        boolean[] configuredSlots = new boolean[5];
        for (int i = 0; i < config.noteIds.size() && i < noteCount; i++) {
            long noteId = config.noteIds.get(i);
            String projectName = i < config.projectNames.size() ? config.projectNames.get(i) : "";
            NoteWidgetItem item = NoteWidgetDataSource.loadNoteById(context, projectName, noteId);
            items.add(item);
            configuredSlots[i] = true;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note_3);
        applyHeightStyle(context, views, config.heightStyle);

        // 配置按钮 - 始终显示，点击重新打开配置页面
        Intent configIntent = new Intent(context, NoteWidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int configFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            configFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId * 10000, configIntent, configFlags);
        views.setOnClickPendingIntent(R.id.widgetConfigButton, configPendingIntent);

        // Handle refresh button visibility based on refresh strategy
        if (config.refreshStrategy == REFRESH_MANUAL) {
            views.setViewVisibility(R.id.widgetRefreshButton, android.view.View.VISIBLE);
            Intent refreshIntent = new Intent(context, NoteWidgetProvider.class);
            refreshIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                    context, appWidgetId * 1000, refreshIntent, flags);
            views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPendingIntent);
        } else {
            views.setViewVisibility(R.id.widgetRefreshButton, android.view.View.GONE);
        }

        // 根据配置的笔记条数显示/隐藏相应的TextView
        int[] noteViewIds = {R.id.widgetNote1, R.id.widgetNote2, R.id.widgetNote3, R.id.widgetNote4, R.id.widgetNote5};
        for (int i = 0; i < noteViewIds.length; i++) {
            if (i < noteCount) {
                views.setViewVisibility(noteViewIds[i], android.view.View.VISIBLE);
                bindNoteItem(context, views, appWidgetId, noteViewIds[i],
                    i < items.size() ? items.get(i) : null, configuredSlots[i], config.clickAction);
            } else {
                views.setViewVisibility(noteViewIds[i], android.view.View.GONE);
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAll(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (context == null || appWidgetManager == null || appWidgetIds == null) {
            return;
        }
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void saveConfig(Context context, int appWidgetId, List<NoteWidgetItem> selectedItems,
                                  int rangeType, int heightStyle, Set<String> selectedProjects,
                                  int clickAction, int refreshStrategy, int noteCount) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        List<String> noteIds = new ArrayList<>();
        List<String> projectNames = new ArrayList<>();
        for (NoteWidgetItem item : selectedItems) {
            noteIds.add(String.valueOf(item.getNoteId()));
            projectNames.add(item.getProjectName());
        }

        editor.putString(KEY_NOTE_IDS_PREFIX + appWidgetId, TextUtils.join(",", noteIds));
        editor.putString(KEY_NOTE_PROJECTS_PREFIX + appWidgetId, TextUtils.join(",", projectNames));
        editor.putInt(KEY_RANGE_PREFIX + appWidgetId, rangeType);
        editor.putInt(KEY_HEIGHT_PREFIX + appWidgetId, heightStyle);
        editor.putInt(KEY_CLICK_ACTION_PREFIX + appWidgetId, clickAction);
        editor.putInt(KEY_REFRESH_STRATEGY_PREFIX + appWidgetId, refreshStrategy);
        editor.putInt(KEY_NOTE_COUNT_PREFIX + appWidgetId, noteCount);

        if (selectedProjects != null) {
            editor.putStringSet(KEY_SELECTED_PROJECTS_PREFIX + appWidgetId, selectedProjects);
        } else {
            editor.remove(KEY_SELECTED_PROJECTS_PREFIX + appWidgetId);
        }

        editor.apply();
    }

    public static void clearConfig(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_NOTE_IDS_PREFIX + appWidgetId);
        editor.remove(KEY_NOTE_PROJECTS_PREFIX + appWidgetId);
        editor.remove(KEY_CLICK_ACTION_PREFIX + appWidgetId);
        editor.remove(KEY_REFRESH_STRATEGY_PREFIX + appWidgetId);
        editor.remove(KEY_RANGE_PREFIX + appWidgetId);
        editor.remove(KEY_HEIGHT_PREFIX + appWidgetId);
        editor.remove(KEY_SELECTED_PROJECTS_PREFIX + appWidgetId);
        editor.remove(KEY_NOTE_COUNT_PREFIX + appWidgetId);
        editor.apply();
    }

    public static Config loadConfig(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String noteIds = prefs.getString(KEY_NOTE_IDS_PREFIX + appWidgetId, "");
        String projectNames = prefs.getString(KEY_NOTE_PROJECTS_PREFIX + appWidgetId, "");
        int rangeType = prefs.getInt(KEY_RANGE_PREFIX + appWidgetId, RANGE_ALL_PROJECTS);
        int heightStyle = prefs.getInt(KEY_HEIGHT_PREFIX + appWidgetId, HEIGHT_STANDARD);
        int clickAction = prefs.getInt(KEY_CLICK_ACTION_PREFIX + appWidgetId, CLICK_ACTION_MAIN_PAGE);
        int refreshStrategy = prefs.getInt(KEY_REFRESH_STRATEGY_PREFIX + appWidgetId, REFRESH_AUTO);
        int noteCount = prefs.getInt(KEY_NOTE_COUNT_PREFIX + appWidgetId, 3);
        Set<String> selectedProjects = prefs.getStringSet(KEY_SELECTED_PROJECTS_PREFIX + appWidgetId, new HashSet<>());

        List<Long> parsedNoteIds = new ArrayList<>();
        List<String> parsedProjects = new ArrayList<>();
        if (!TextUtils.isEmpty(noteIds)) {
            for (String value : noteIds.split(",")) {
                if (!TextUtils.isEmpty(value)) {
                    try {
                        parsedNoteIds.add(Long.parseLong(value));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(projectNames)) {
            for (String value : projectNames.split(",")) {
                if (!TextUtils.isEmpty(value)) {
                    parsedProjects.add(value);
                }
            }
        }

        return new Config(parsedNoteIds, parsedProjects, rangeType, heightStyle, selectedProjects,
                clickAction, refreshStrategy, noteCount);
    }

    private static void bindNoteItem(Context context, RemoteViews views, int appWidgetId, int viewId,
                                     NoteWidgetItem item, boolean configured, int clickAction) {
        if (item == null) {
            views.setTextViewText(viewId, configured ? "已删除" : "未设置");
            return;
        }
        String projectName = item.getProjectName() == null ? "" : item.getProjectName();
        String content = item.getContent() == null ? "" : item.getContent();
        String displayText = projectName.isEmpty() ? content : projectName + " · " + content;
        views.setTextViewText(viewId, displayText);

        Intent intent;
        if (clickAction == CLICK_ACTION_NOTE_DETAIL) {
            // TODO: Open NoteDetailActivity once it exists
            // For now, fall back to MainActivity behavior
            intent = new Intent(context, MainActivity.class);
            intent.putExtra(EXTRA_NOTE_ID, item.getNoteId());
            intent.putExtra(EXTRA_PROJECT_NAME, item.getProjectName());
        } else {
            // CLICK_ACTION_MAIN_PAGE
            intent = new Intent(context, MainActivity.class);
            intent.putExtra(EXTRA_NOTE_ID, item.getNoteId());
            intent.putExtra(EXTRA_PROJECT_NAME, item.getProjectName());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                buildRequestCode(appWidgetId, viewId),
                intent,
                flags
        );
        views.setOnClickPendingIntent(viewId, pendingIntent);
    }

    private static int buildRequestCode(int appWidgetId, int viewId) {
        int index = 0;
        if (viewId == R.id.widgetNote2) {
            index = 1;
        } else if (viewId == R.id.widgetNote3) {
            index = 2;
        }
        return appWidgetId * 10 + index;
    }

    private static void applyHeightStyle(Context context, RemoteViews views, int heightStyle) {
        float textSize;
        int padding;
        if (heightStyle == HEIGHT_COMPACT) {
            textSize = 12f;
            padding = dpToPx(context, 6);
        } else if (heightStyle == HEIGHT_RELAXED) {
            textSize = 16f;
            padding = dpToPx(context, 10);
        } else {
            textSize = 14f;
            padding = dpToPx(context, 8);
        }

        views.setTextViewTextSize(R.id.widgetTitle, android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(10f, textSize - 2));
        views.setTextViewTextSize(R.id.widgetNote1, android.util.TypedValue.COMPLEX_UNIT_SP, textSize);
        views.setTextViewTextSize(R.id.widgetNote2, android.util.TypedValue.COMPLEX_UNIT_SP, textSize);
        views.setTextViewTextSize(R.id.widgetNote3, android.util.TypedValue.COMPLEX_UNIT_SP, textSize);
        views.setViewPadding(R.id.widgetRoot, padding, padding, padding, padding);
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    public static class Config {
        public final List<Long> noteIds;
        public final List<String> projectNames;
        public final int rangeType;
        public final int heightStyle;
        public final Set<String> selectedProjects;
        public final int clickAction;
        public final int refreshStrategy;
        public final int noteCount;

        public Config(List<Long> noteIds, List<String> projectNames, int rangeType, int heightStyle,
                     Set<String> selectedProjects, int clickAction, int refreshStrategy, int noteCount) {
            this.noteIds = noteIds == null ? new ArrayList<>() : noteIds;
            this.projectNames = projectNames == null ? new ArrayList<>() : projectNames;
            this.rangeType = rangeType;
            this.heightStyle = heightStyle;
            this.selectedProjects = selectedProjects == null ? new HashSet<>() : selectedProjects;
            this.clickAction = clickAction;
            this.refreshStrategy = refreshStrategy;
            this.noteCount = noteCount;
        }
    }
}
