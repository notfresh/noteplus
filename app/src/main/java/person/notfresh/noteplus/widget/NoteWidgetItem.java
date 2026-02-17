package person.notfresh.noteplus.widget;

import java.util.Objects;

public class NoteWidgetItem {
    private final long noteId;
    private final String projectName;
    private final String content;
    private final long timestamp;

    public NoteWidgetItem(long noteId, String projectName, String content, long timestamp) {
        this.noteId = noteId;
        this.projectName = projectName;
        this.content = content;
        this.timestamp = timestamp;
    }

    public long getNoteId() {
        return noteId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return projectName + ":" + noteId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoteWidgetItem that = (NoteWidgetItem) o;
        return noteId == that.noteId && Objects.equals(projectName, that.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noteId, projectName);
    }
}
