package person.notfresh.noteplus.search;

import person.notfresh.noteplus.core.model.Note;

/**
 * 搜索结果模型
 */
public class SearchResult {
    private final Note note;
    private final String highlightedContent;  // 高亮处理后的内容摘要
    private final float score;  // 相关性得分

    public SearchResult(Note note, String highlightedContent, float score) {
        this.note = note;
        this.highlightedContent = highlightedContent;
        this.score = score;
    }

    public Note getNote() {
        return note;
    }

    public String getHighlightedContent() {
        return highlightedContent;
    }

    public float getScore() {
        return score;
    }
}