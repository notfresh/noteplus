package person.notfresh.noteplus.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

import person.notfresh.noteplus.R;
import person.notfresh.noteplus.util.DisplayUtil;

public class NoteWidgetConfigAdapter extends BaseAdapter {

    private final Context context;
    private List<NoteWidgetItem> items;
    private final Map<String, NoteWidgetItem> selectedItems;
    private final Runnable onSelectionChanged;
    private int maxSelectionCount = 3;

    public NoteWidgetConfigAdapter(Context context, List<NoteWidgetItem> items,
                                   Map<String, NoteWidgetItem> selectedItems,
                                   Runnable onSelectionChanged) {
        this.context = context;
        this.items = items;
        this.selectedItems = selectedItems;
        this.onSelectionChanged = onSelectionChanged;
    }

    public void updateItems(List<NoteWidgetItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void setMaxSelectionCount(int maxCount) {
        this.maxSelectionCount = maxCount;
    }

    @Override
    public int getCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public NoteWidgetItem getItem(int position) {
        if (items == null || position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        NoteWidgetItem item = getItem(position);
        return item != null ? item.getNoteId() : position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_widget_note_select, parent, false);
        }

        NoteWidgetItem item = getItem(position);
        if (item == null) {
            return view;
        }

        TextView contentText = view.findViewById(R.id.widgetNoteContent);
        TextView metaText = view.findViewById(R.id.widgetNoteMeta);
        CheckBox checkBox = view.findViewById(R.id.widgetNoteCheckbox);

        contentText.setText(item.getContent());
        String meta = item.getProjectName() + " · " + DisplayUtil.formatTimestamp(item.getTimestamp());
        metaText.setText(meta);

        String key = item.getKey();
        boolean isSelected = selectedItems.containsKey(key);
        checkBox.setOnCheckedChangeListener(null);
        checkBox.setChecked(isSelected);

        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (selectedItems.size() >= maxSelectionCount && !selectedItems.containsKey(key)) {
                    Toast.makeText(context, "最多选择 " + maxSelectionCount + " 条", Toast.LENGTH_SHORT).show();
                    checkBox.setChecked(false);
                    return;
                }
                selectedItems.put(key, item);
            } else {
                selectedItems.remove(key);
            }
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        });

        view.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
        return view;
    }
}
