package person.notfresh.noteplus.core.model;

/**
 * 评论数据模型 - 平台无关的POJO类
 * 用于表示笔记的追加内容（评论/回复）
 */
public class Comment {
    private long id;
    private long noteId;
    private Long parentCommentId;  // null表示直接回复笔记，非null表示回复某个评论
    private String content;
    private long timestamp;
    private double cost;
    private String projectName;  // 所属项目名称
    private TimelineItemType itemType;  // 时间线项类型：NOTE（Note转换来的）或 COMMENT（真正的Comment）
    
    public Comment() {
        this.itemType = TimelineItemType.COMMENT;  // 默认为 COMMENT
    }
    
    public Comment(long id, long noteId, Long parentCommentId, String content, long timestamp, double cost) {
        this.id = id;
        this.noteId = noteId;
        this.parentCommentId = parentCommentId;
        this.content = content;
        this.timestamp = timestamp;
        this.cost = cost;
        this.itemType = TimelineItemType.COMMENT;  // 默认为 COMMENT
    }
    
    public Comment(long id, long noteId, Long parentCommentId, String content, long timestamp, double cost, String projectName) {
        this.id = id;
        this.noteId = noteId;
        this.parentCommentId = parentCommentId;
        this.content = content;
        this.timestamp = timestamp;
        this.cost = cost;
        this.projectName = projectName;
        this.itemType = TimelineItemType.COMMENT;  // 默认为 COMMENT
    }
    
    public Comment(long id, long noteId, Long parentCommentId, String content, long timestamp, double cost, String projectName, TimelineItemType itemType) {
        this.id = id;
        this.noteId = noteId;
        this.parentCommentId = parentCommentId;
        this.content = content;
        this.timestamp = timestamp;
        this.cost = cost;
        this.projectName = projectName;
        this.itemType = itemType;
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
    
    public Long getParentCommentId() {
        return parentCommentId;
    }
    
    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public double getCost() {
        return cost;
    }
    
    public void setCost(double cost) {
        this.cost = cost;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    public TimelineItemType getItemType() {
        return itemType;
    }
    
    public void setItemType(TimelineItemType itemType) {
        this.itemType = itemType;
    }
    
    /**
     * 判断是否为直接回复笔记（不是回复其他评论）
     * @return true表示直接回复笔记，false表示回复其他评论
     */
    public boolean isDirectReply() {
        return parentCommentId == null;
    }
    
    /**
     * 判断是否为 Note 转换来的（不是真正的 Comment）
     * @return true表示是 Note 转换来的，false表示是真正的 Comment
     */
    public boolean isNote() {
        return itemType == TimelineItemType.NOTE;
    }
    
    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", noteId=" + noteId +
                ", parentCommentId=" + parentCommentId +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", cost=" + cost +
                ", projectName='" + projectName + '\'' +
                ", itemType=" + itemType +
                '}';
    }
}
