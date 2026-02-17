package person.notfresh.noteplus.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class NoteWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        NoteWidgetUpdater.updateAll(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        if (context == null || appWidgetIds == null) {
            return;
        }
        for (int appWidgetId : appWidgetIds) {
            NoteWidgetUpdater.clearConfig(context, appWidgetId);
        }
    }
}
