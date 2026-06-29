package person.notfresh.noteplus.search;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 搜索服务
 * 负责分词和搜索查询
 */
public class SearchService {
    private static final String TAG = "SearchService";
    private static final String INDEX_DIR = "search_index";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final int MAX_SEARCH_RESULTS = 100;

    private final Context context;
    private final NoteDbHelper dbHelper;
    private Analyzer searchAnalyzer;  // 搜索时用智能分词
    private Directory indexDirectory;
    private boolean indexInitialized = false;

    public SearchService(Context context, NoteDbHelper dbHelper) {
        this.context = context.getApplicationContext();
        this.dbHelper = dbHelper;
        initIndex();
    }

    private void initIndex() {
        try {
            File indexDir = new File(context.getFilesDir(), INDEX_DIR);
            if (!indexDir.exists()) {
                indexDir.mkdirs();
            }
            indexDirectory = new org.apache.lucene.store.NIOFSDirectory(indexDir.toPath());
            searchAnalyzer = new StandardAnalyzer();
            indexInitialized = true;
        } catch (IOException e) {
            Log.e(TAG, "搜索服务初始化失败", e);
            indexInitialized = false;
        }
    }

    /**
     * 对搜索词进行分词
     * @param query 搜索词
     * @return 分词后的词列表
     */
    public List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return tokens;
        }
        try {
            // IKAnalyzer 的 tokenStream 方法
            org.apache.lucene.analysis.TokenStream tokenStream = searchAnalyzer.tokenStream(FIELD_CONTENT, query);
            org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAttr = tokenStream.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                String term = termAttr.toString();
                if (!term.isEmpty()) {
                    tokens.add(term);
                }
            }
            tokenStream.end();
            tokenStream.close();
        } catch (IOException e) {
            Log.e(TAG, "分词失败", e);
        }
        return tokens;
    }

    /**
     * 搜索笔记
     * @param query 搜索词
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        if (!indexInitialized) {
            Log.e(TAG, "索引未初始化");
            return results;
        }

        IndexReader reader = null;
        Cursor noteCursor = null;
        try {
            // 1. 分词
            List<String> tokens = tokenize(query);
            if (tokens.isEmpty()) {
                Log.d(TAG, "分词结果为空");
                return results;
            }
            Log.d(TAG, "分词结果: " + tokens);

            // 2. 构建 BooleanQuery（OR 匹配）
            BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
            for (String token : tokens) {
                // 在 content 字段中搜索每个分词
                boolQueryBuilder.add(new BooleanClause(
                        new org.apache.lucene.search.TermQuery(new Term(FIELD_CONTENT, token)),
                        BooleanClause.Occur.SHOULD
                ));
            }
            BooleanQuery booleanQuery = boolQueryBuilder.build();

            // 3. 执行搜索
            reader = DirectoryReader.open(indexDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(booleanQuery, MAX_SEARCH_RESULTS);

            Log.d(TAG, "找到 " + topDocs.totalHits + " 条匹配结果");

            // 4. 处理结果
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                long noteId = doc.getField(FIELD_ID).numericValue().longValue();
                String content = doc.getField(FIELD_CONTENT).stringValue();
                long timestamp = doc.getField(FIELD_TIMESTAMP).numericValue().longValue();

                // 获取对应的 Note 对象
                noteCursor = dbHelper.getWritableDatabase().query(
                        NoteDbHelper.TABLE_NOTES,
                        new String[]{NoteDbHelper.COLUMN_ID, NoteDbHelper.COLUMN_CONTENT,
                                NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST,
                                NoteDbHelper.COLUMN_IS_PINNED},
                        NoteDbHelper.COLUMN_ID + "=?",
                        new String[]{String.valueOf(noteId)},
                        null, null, null
                );

                person.notfresh.noteplus.core.model.Note note = null;
                if (noteCursor != null && noteCursor.moveToFirst()) {
                    note = new person.notfresh.noteplus.core.model.Note(
                            noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_ID)),
                            noteCursor.getString(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT)),
                            noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP)),
                            noteCursor.getDouble(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST)),
                            noteCursor.getInt(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_IS_PINNED)) == 1
                    );
                }

                // 无论如何都要关闭 cursor
                if (noteCursor != null) {
                    noteCursor.close();
                    noteCursor = null;
                }

                if (note != null) {
                    // 高亮处理：简单替换匹配词
                    String highlighted = highlightContent(content, tokens);
                    results.add(new SearchResult(note, highlighted, scoreDoc.score));
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "搜索失败", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭 reader 失败", e);
                }
            }
            if (noteCursor != null) {
                noteCursor.close();
            }
        }

        return results;
    }

    /**
     * 简单高亮处理：将匹配的词用 【】 包裹
     * @param content 原始内容
     * @param tokens 分词列表
     * @return 高亮后的内容
     */
    private String highlightContent(String content, List<String> tokens) {
        if (content == null || tokens.isEmpty()) {
            return content;
        }
        // 预处理：收集所有需要高亮的区间（去重）
        java.util.Set<String> tokenSet = new java.util.HashSet<>(tokens);
        List<int[]> highlights = new java.util.ArrayList<>();
        for (String token : tokenSet) {
            int index = 0;
            while ((index = content.indexOf(token, index)) != -1) {
                highlights.add(new int[]{index, index + token.length()});
                index += token.length();
            }
        }
        // 按起始位置排序
        highlights.sort((a, b) -> Integer.compare(a[0], b[0]));
        // 构建结果
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        for (int[] range : highlights) {
            result.append(content, lastEnd, range[0]);
            result.append("【").append(content, range[0], range[1]).append("】");
            lastEnd = range[1];
        }
        result.append(content.substring(lastEnd));
        // 限制显示长度
        String finalResult = result.toString();
        if (finalResult.length() > 200) {
            int highlightIndex = finalResult.indexOf("【");
            if (highlightIndex > 100) {
                finalResult = "..." + finalResult.substring(highlightIndex - 50);
            } else {
                finalResult = finalResult.substring(0, 200) + "...";
            }
        }
        return finalResult;
    }

    /**
     * 关闭资源
     */
    public void close() {
        try {
            if (searchAnalyzer != null) {
                searchAnalyzer.close();
            }
            if (indexDirectory != null) {
                indexDirectory.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭搜索服务失败", e);
        }
    }
}