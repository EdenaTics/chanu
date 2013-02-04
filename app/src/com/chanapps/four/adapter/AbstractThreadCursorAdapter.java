package com.chanapps.four.adapter;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.chanapps.four.activity.R;
import com.chanapps.four.component.ChanGridSizer;
import com.chanapps.four.data.ChanHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: johnarleyburns
 * Date: 12/21/12
 * Time: 12:14 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractThreadCursorAdapter extends AbstractBoardCursorAdapter {

    protected static final String TAG = AbstractThreadCursorAdapter.class.getSimpleName();
    protected static final boolean DEBUG = false;

    protected long highlightPostNo = 0;
    protected Set<Long> highlightPrevPostNos = new HashSet<Long>();
    protected Set<Long> highlightNextPostNos = new HashSet<Long>();

    public AbstractThreadCursorAdapter(Context context, int layout, ViewBinder viewBinder, String[] from, int[] to) {
        super(context, layout, viewBinder, from, to);
    }

    public void setHighlightPosts(long highlightPostNo, long[] prevPostNos, long[] nextPostNos) {
        this.highlightPostNo = highlightPostNo;
        highlightPrevPostNos.clear();
        highlightNextPostNos.clear();
        if (prevPostNos != null)
            for (long postNo : prevPostNos)
                highlightPrevPostNos.add(postNo);
        if (nextPostNos != null)
            for (long postNo : nextPostNos)
                highlightNextPostNos.add(postNo);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Cursor cursor = getCursor();
        if (cursor == null) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!cursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        String tag = null;
        String imageUrl = cursor.getString(cursor.getColumnIndex(ChanHelper.POST_IMAGE_URL));
        int loadItem = cursor.getInt(cursor.getColumnIndex(ChanHelper.LOADING_ITEM));
        int lastItem = cursor.getInt(cursor.getColumnIndex(ChanHelper.LAST_ITEM));
        int adItem = cursor.getInt(cursor.getColumnIndex(ChanHelper.AD_ITEM));
        long postNo = cursor.getLong(cursor.getColumnIndex(ChanHelper.POST_ID));
        if (DEBUG) Log.d(TAG, "getView called for position="+position + " postNo=" + postNo);
        if (loadItem > 0) {
            tag = ChanHelper.LOADING_ITEM;
        }
        else if (lastItem > 0) {
            tag = ChanHelper.LAST_ITEM;
        }
        else if (adItem > 0) {
            tag = ChanHelper.AD_ITEM;
        }
        else if (position == 0) { // thread header
            tag = ChanHelper.POST_RESTO;
        }
        else if (postNo == 0) { // null spacer to give room for thread header
            tag = ChanHelper.POST_OMITTED_POSTS;
        }
        else if (imageUrl != null && imageUrl.length() > 0) {
            tag = ChanHelper.POST_IMAGE_URL;
        } else {
            tag = ChanHelper.POST_SHORT_TEXT;
        }

        View v;
        if (convertView == null || !tag.equals(convertView.getTag()) || ChanHelper.POST_RESTO.equals(tag)) {
            v = newView(context, parent, tag, position);
            v.setTag(tag);
            if (imageUrl != null && imageUrl.length() > 0) {
                ImageView imageView = (ImageView)v.findViewById(R.id.grid_item_image);
                if (imageView != null)
                    imageView.setTag(imageUrl);
            }
        } else {
            if (DEBUG) Log.d(TAG, "Reusing existing " + tag + " layout for " + position);
            v = convertView;
        }

        if (v != null && v instanceof RelativeLayout && postNo != 0)
            setHighlightViews(v, tag, postNo);

        bindView(v, context, cursor);
        return v;
    }

    abstract protected View newView(Context context, ViewGroup parent, String tag, int position);

    abstract protected void setHighlightViews(View v, String tag, long postNo);

}
