package person.notfresh.noteplus.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import person.notfresh.noteplus.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 搜索结果适配器
 */
public class SearchResultAdapter extends ArrayAdapter<SearchResult> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private static class ViewHolder {
        TextView contentView;
        TextView timeView;
    }

    public SearchResultAdapter(@NonNull Context context, @NonNull List<SearchResult> objects) {
        super(context, R.layout.item_search_result, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_search_result, parent, false);
            holder = new ViewHolder();
            holder.contentView = convertView.findViewById(R.id.search_result_content);
            holder.timeView = convertView.findViewById(R.id.search_result_time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        SearchResult result = getItem(position);
        if (result != null) {
            holder.contentView.setText(result.getHighlightedContent());
            holder.timeView.setText(DATE_FORMAT.format(new Date(result.getNote().getTimestamp())));
        }

        return convertView;
    }
}