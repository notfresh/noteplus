package person.notfresh.noteplus.model;

public class TimeRange {
    private long id;
    private long noteId;
    private long startTime;
    private long endTime;
    
    public TimeRange() {}
    
    public TimeRange(long noteId, long startTime, long endTime) {
        this.noteId = noteId;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getNoteId() {
        return noteId;
    }
    
    public void setNoteId(long noteId) {
        this.noteId = noteId;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
} 