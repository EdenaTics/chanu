package com.chanapps.four.handler;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.chanapps.four.activity.ClickableLoaderActivity;

/**
* Created with IntelliJ IDEA.
* User: arley
* Date: 11/27/12
* Time: 2:41 PM
* To change this template use File | Settings | File Templates.
*/
public class LoaderHandler extends Handler {
    private ClickableLoaderActivity activity;
    private static final String TAG = LoaderHandler.class.getSimpleName();

    public LoaderHandler() {}
    public LoaderHandler(ClickableLoaderActivity activity) {
        this.activity = activity;
    }
    @Override
    public void handleMessage(Message msg) {
        try {
            super.handleMessage(msg);
            Log.i(activity.getClass().getSimpleName(), ">>>>>>>>>>> restart message received restarting loader");
            activity.getLoaderManager().restartLoader(0, null, activity);
        }
        catch (Exception e) {
            Log.e(TAG, "Couldn't handle message " + msg, e);
        }
    }
}