package person.notfresh.noteplus.core.model;

/**
 * 音频附件模型
 */
public class AudioAttachment {
    private long id;
    private String path;
    private long durationMs;

    public AudioAttachment(long id, String path, long durationMs) {
        this.id = id;
        this.path = path;
        this.durationMs = durationMs;
    }

    public AudioAttachment(String path, long durationMs) {
        this(-1, path, durationMs);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
