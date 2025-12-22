package person.notfresh.noteplus.core.model;

/**
 * 模型工具类 - 提供模型转换等工具方法
 */
public class modelUtil {
    
    /**
     * 将 Note 转换为 Comment
     * Note 被视为特殊的 Comment，作为主题的第一个条目
     * 
     * @param note 笔记对象
     * @param defaultProjectName 默认项目名称（当 note 的 projectName 为 null 时使用）
     * @return 转换后的 Comment 对象
     */
    public static Comment convertNoteToComment(Note note, String defaultProjectName) {
        // Note 转换为 Comment：
        // - id: 使用 note.getId()（作为 commentId）
        // - noteId: 使用 note.getId()（指向自己）
        // - parentCommentId: null（表示这是主题的第一个条目）
        // - content: note.getContent()
        // - timestamp: note.getTimestamp()
        // - cost: note.getCost()
        // - itemType: TimelineItemType.NOTE（表示这是 Note 转换来的）
        String noteProjectName = note.getProjectName() != null ? note.getProjectName() : defaultProjectName;
        Comment comment = new Comment(
            note.getId(),           // commentId = noteId
            note.getId(),           // noteId = noteId（指向自己）
            null,                   // parentCommentId = null（表示主题的第一个条目）
            note.getContent(),      // content
            note.getTimestamp(),    // timestamp
            note.getCost(),         // cost
            noteProjectName,        // projectName
            TimelineItemType.NOTE   // itemType = NOTE（表示这是 Note 转换来的）
        );
        return comment;
    }
}

