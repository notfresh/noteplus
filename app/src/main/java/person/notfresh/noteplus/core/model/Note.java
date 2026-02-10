package person.notfresh.noteplus.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记数据模型 - 平台无关的POJO类
 * 用于在不同平台和模块间传递笔记数据
 */
public class Note {
    private long id;
    private String content;
    private long timestamp;
    private double cost;
    private boolean isPinned;
    private String projectName;  // 所属项目名称
    private List<Comment> comments;
    private List<String> images;
    
    public Note() {
        this.comments = new ArrayList<>();
        this.images = new ArrayList<>();
    }
    
    public Note(long id, String content, long timestamp, double cost, boolean isPinned) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.cost = cost;
        this.isPinned = isPinned;
        this.comments = new ArrayList<>();
        this.images = new ArrayList<>();
    }
    
    public Note(long id, String content, long timestamp, double cost, boolean isPinned, String projectName) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.cost = cost;
        this.isPinned = isPinned;
        this.projectName = projectName;
        this.comments = new ArrayList<>();
        this.images = new ArrayList<>();
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
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
    
    public boolean isPinned() {
        return isPinned;
    }
    
    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * 获取图片路径列表
     * @return 图片路径列表
     */
    public List<String> getImages() {
        if (images == null) {
            images = new ArrayList<>();
        }
        return images;
    }

    /**
     * 设置图片路径列表
     * @param images 图片路径列表
     */
    public void setImages(List<String> images) {
        this.images = images;
    }
    
    /**
     * 获取评论列表
     * @return 评论列表，如果未设置则返回空列表
     */
    public List<Comment> getComments() {
        if (comments == null) {
            comments = new ArrayList<>();
        }
        return comments;
    }
    
    /**
     * 设置评论列表
     * @param comments 评论列表
     */
    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }
    
    /**
     * 添加一条评论
     * @param comment 评论对象
     */
    public void addComment(Comment comment) {
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(comment);
    }
    
    /**
     * 将另一个Note合并到当前Note中作为附属Comment
     * 源Note的所有内容会被转换为一个Comment对象，并添加到当前Note的评论列表中
     * 如果源Note有评论，这些评论也会被合并，并且会正确更新它们的parentCommentId
     * 
     * @param sourceNote 要合并的源Note对象，不能为null
     * @throws IllegalArgumentException 如果sourceNote为null
     */
    public void mergeNoteAsComment(Note sourceNote) {
        if (sourceNote == null) {
            throw new IllegalArgumentException("源Note不能为null");
        }
        
        // 确保comments列表已初始化
        if (comments == null) {
            comments = new ArrayList<>();
        }
        
        // 创建Comment对象，将源Note的内容转换为Comment
        // 使用工具方法转换，然后更新noteId为当前Note的id
        Comment mergedComment = modelUtil.convertNoteToComment(sourceNote, this.projectName);
        mergedComment.setNoteId(this.id);  // 更新noteId为当前Note的id（而不是源Note的id）
        
        // 先将转换后的Comment添加到当前Note的评论列表
        comments.add(mergedComment);
        
        // 如果源Note有评论，也需要合并过来
        // 这些评论应该成为mergedComment的子评论
        if (sourceNote.getComments() != null && !sourceNote.getComments().isEmpty()) {
            long mergedCommentId = mergedComment.getId(); // 新创建的Comment的id
            
            for (Comment sourceComment : sourceNote.getComments()) {
                // 创建新的Comment
                Comment childComment = new Comment(
                    sourceComment.getId(),
                    this.id,              // 更新noteId为当前Note的id
                    sourceComment.getParentCommentId() == null 
                        ? mergedCommentId  // 如果原本是直接回复源Note，现在回复mergedComment
                        : sourceComment.getParentCommentId(), // 否则保持原有的parentCommentId（回复源Note的其他评论）
                    sourceComment.getContent(),
                    sourceComment.getTimestamp(),
                    sourceComment.getCost(),
                    sourceComment.getProjectName(),
                    sourceComment.getItemType()
                );
                comments.add(childComment);
            }
        }
    }
    
    @Override
    public String toString() {
        return "Note{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", cost=" + cost +
                ", isPinned=" + isPinned +
                ", projectName='" + projectName + '\'' +
                ", comments: 数量" + (comments != null ? comments.size() + "条" : 0) +
                '}';
    }
}
