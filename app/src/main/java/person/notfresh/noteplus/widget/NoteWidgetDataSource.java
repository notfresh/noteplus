package person.notfresh.noteplus.widget;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import person.notfresh.noteplus.core.GlobalTimeline;
import person.notfresh.noteplus.core.TimeRangeFilter;
import person.notfresh.noteplus.core.model.Comment;
import person.notfresh.noteplus.core.model.TimelineItemType;
import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;

public class NoteWidgetDataSource {

    public static List<NoteWidgetItem> loadSelectableNotes(Context context, int rangeType, Set<String> selectedProjects) {
        if (context == null) {
            return new ArrayList<>();
        }

        ProjectContextManager projectManager = new ProjectContextManager(context);
        List<NoteWidgetItem> result = new ArrayList<>();

        if (rangeType == NoteWidgetUpdater.RANGE_CURRENT_PROJECT) {
            String currentProject = projectManager.getCurrentProject();
            NoteDbHelper dbHelper = projectManager.getDbHelperForProject(currentProject);
            result.addAll(loadNotesForProject(dbHelper, currentProject));
        } else if (rangeType == NoteWidgetUpdater.RANGE_SELECTED_PROJECTS) {
            Set<String> projects = selectedProjects == null ? new HashSet<>() : selectedProjects;
            if (projects.isEmpty()) {
                projects = new HashSet<>(projectManager.getProjectList());
            }
            for (String projectName : projects) {
                NoteDbHelper dbHelper = projectManager.getDbHelperForProject(projectName);
                result.addAll(loadNotesForProject(dbHelper, projectName));
            }
        } else {
            GlobalTimeline timeline = new GlobalTimeline(projectManager);
            List<Comment> items = timeline.loadGlobalTimeline(TimeRangeFilter.LAST_MONTH, true);
            Set<String> seen = new HashSet<>();
            for (Comment item : items) {
                if (item == null || item.getItemType() != TimelineItemType.NOTE) {
                    continue;
                }
                String projectName = item.getProjectName();
                if (projectName == null || projectName.trim().isEmpty()) {
                    projectName = projectManager.getCurrentProject();
                }
                String key = projectName + ":" + item.getNoteId();
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                result.add(new NoteWidgetItem(
                        item.getNoteId(),
                        projectName,
                        item.getContent(),
                        item.getTimestamp()
                ));
            }
        }

        sortByTimestampDesc(result);
        return result;
    }

    public static NoteWidgetItem loadNoteById(Context context, String projectName, long noteId) {
        if (context == null || projectName == null || projectName.isEmpty() || noteId <= 0) {
            return null;
        }
        ProjectContextManager projectManager = new ProjectContextManager(context);
        NoteDbHelper dbHelper = projectManager.getDbHelperForProject(projectName);
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
                            NoteDbHelper.COLUMN_IS_ARCHIVED
                    },
                    "_id = ? AND " + NoteDbHelper.COLUMN_IS_ARCHIVED + " = 0",
                    new String[]{String.valueOf(noteId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndexOrThrow("_id");
                int contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
                int timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
                int archivedIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_IS_ARCHIVED);
                int isArchived = cursor.getInt(archivedIndex);
                if (isArchived != 0) {
                    return null; // 归档的笔记返回 null
                }
                long id = cursor.getLong(idIndex);
                String content = cursor.getString(contentIndex);
                long timestamp = cursor.getLong(timestampIndex);
                return new NoteWidgetItem(id, projectName, content, timestamp);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static List<NoteWidgetItem> loadNotesForProject(NoteDbHelper dbHelper, String projectName) {
        List<NoteWidgetItem> items = new ArrayList<>();
        if (dbHelper == null || projectName == null) {
            return items;
        }

        Cursor cursor = null;
        try {
            cursor = dbHelper.loadNotes(true);
            if (cursor == null) {
                return items;
            }
            int idIndex = cursor.getColumnIndexOrThrow("_id");
            int contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
            int timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(idIndex);
                String content = cursor.getString(contentIndex);
                long timestamp = cursor.getLong(timestampIndex);
                items.add(new NoteWidgetItem(noteId, projectName, content, timestamp));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return items;
    }

    private static void sortByTimestampDesc(List<NoteWidgetItem> items) {
        Collections.sort(items, new Comparator<NoteWidgetItem>() {
            @Override
            public int compare(NoteWidgetItem a, NoteWidgetItem b) {
                return Long.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
    }
}
