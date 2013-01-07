/**
 * 
 */
package com.chanapps.four.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.chanapps.four.activity.R;
import com.chanapps.four.data.ChanBoard;
import com.chanapps.four.data.ChanFileStorage;
import com.chanapps.four.data.ChanHelper;
import com.chanapps.four.data.ChanPost;
import com.chanapps.four.data.ChanThread;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

/**
 * @author "Grzegorz Nittner" <grzegorz.nittner@gmail.com>
 *
 */
public class ThreadLoadService extends BaseChanService {

    protected static final String TAG = ThreadLoadService.class.getName();

    protected static final long STORE_INTERVAL_MS = 2000;
    protected static final long MIN_THREAD_FORCE_INTERVAL = 10000; // 10 sec
    protected static final long MIN_THREAD_FETCH_INTERVAL = 300000; // 5 min

    private String boardCode;
    private long threadNo;
    private boolean force;
    private ChanThread thread;

    public static void startService(Context context, String boardCode, long threadNo, boolean force) {
        Log.i(TAG, "Start thread load service for " + boardCode + " thread " + threadNo );
        Intent intent = new Intent(context, ThreadLoadService.class);
        intent.putExtra(ChanHelper.BOARD_CODE, boardCode);
        intent.putExtra(ChanHelper.THREAD_NO, threadNo);
        if (force) {
            intent.putExtra(ChanHelper.FORCE_REFRESH, force);
        }
        context.startService(intent);
    }

    public static void startServiceWithPriority(Context context, String boardCode, long threadNo, boolean force) {
        Log.i(TAG, "Start thread load service for " + boardCode + " thread " + threadNo );
        Intent intent = new Intent(context, ThreadLoadService.class);
        intent.putExtra(ChanHelper.BOARD_CODE, boardCode);
        intent.putExtra(ChanHelper.THREAD_NO, threadNo);
        if (force) {
            intent.putExtra(ChanHelper.FORCE_REFRESH, force);
        }
        intent.putExtra(ChanHelper.PRIORITY_MESSAGE, 1);
        context.startService(intent);
    }

    public ThreadLoadService() {
   		super("thread");
   	}

    protected ThreadLoadService(String name) {
   		super(name);
   	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		boardCode = intent.getStringExtra(ChanHelper.BOARD_CODE);
        threadNo = intent.getLongExtra(ChanHelper.THREAD_NO, 0);
        force = intent.getBooleanExtra(ChanHelper.FORCE_REFRESH, false);

        Log.i(TAG, "Handling board=" + boardCode + " threadNo=" + threadNo + " force=" + force);

        if (threadNo == 0) {
            Log.e(TAG, "Board loading must be done via the BoardLoadService");
        }
        if (boardCode.equals(ChanBoard.WATCH_BOARD_CODE)) {
            Log.e(TAG, "Watching board must use ChanWatchlist instead of service");
            return;
        }

        BufferedReader in = null;
        HttpURLConnection tc = null;
		try {
            thread = ChanFileStorage.loadThreadData(this, boardCode, threadNo);
            long now = (new Date()).getTime();
            if (thread == null) {
                thread = new ChanThread();
                thread.board = boardCode;
                thread.no = threadNo;
                thread.isDead = false;
            }
            else {
                if (thread.isDead) {
                    Log.i(TAG, "Dead thread retrieved from storage, therefore service is terminating");
                    return;
                }
                long interval = now - thread.lastFetched;
                if (force && interval < MIN_THREAD_FORCE_INTERVAL) {
                    Log.i(TAG, "thread is forced but interval=" + interval + " is less than min=" + MIN_THREAD_FORCE_INTERVAL + " so exiting");
                    return;
                }
                if (!force && interval < MIN_THREAD_FETCH_INTERVAL) {
                    Log.i(TAG, "thread interval=" + interval + " less than min=" + MIN_THREAD_FETCH_INTERVAL + " and not force thus exiting service");
                    return;
                }
            }

            URL chanApi = new URL("http://api.4chan.org/" + boardCode + "/res/" + threadNo + ".json");
            tc = (HttpURLConnection) chanApi.openConnection();
            if (thread.lastFetched > 0)
                tc.setIfModifiedSince(thread.lastFetched);
            Log.i(TAG, "Calling API " + tc.getURL() + " response length=" + tc.getContentLength() + " code=" + tc.getResponseCode());

            thread.lastFetched = now;
            if (tc.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.i(TAG, "Got 404 on thread, thread no longer exists");
                markDead();
            }
            else {
                Log.i(TAG, "Received api response, parsing...");
                in = new BufferedReader(new InputStreamReader(tc.getInputStream()));
                parseThread(in);
            }

            ChanFileStorage.storeThreadData(getBaseContext(), thread);
        } catch (IOException e) {
            toastUI(R.string.board_service_couldnt_read);
            Log.e(TAG, "IO Error reading Chan board json. " + e.getMessage(), e);
		} catch (Exception e) {
            toastUI(R.string.board_service_couldnt_load);
			Log.e(TAG, "Error parsing Chan board json. " + e.getMessage(), e);
		} finally {
			try {
				if (in != null) {
					in.close();
				}
                if (tc != null) {
                    tc.disconnect();
                }
			} catch (Exception e) {
				Log.e(TAG, "Error closing reader", e);
			}
		}
	}

	protected void parseThread(BufferedReader in) throws IOException {
    	Log.i(TAG, "starting parsing thread " + boardCode + "/" + threadNo);
        long time = new Date().getTime();

    	List<ChanPost> posts = new ArrayList<ChanPost>();
        Gson gson = new GsonBuilder().create();

        JsonReader reader = new JsonReader(in);
        reader.setLenient(true);
        reader.beginObject(); // has "posts" as single property
        reader.nextName(); // "posts"
        reader.beginArray();

        while (reader.hasNext()) {
            ChanPost post = gson.fromJson(reader, ChanPost.class);
            post.board = boardCode;
            posts.add(post);
            Log.v(TAG, "Added post " + post.no + " to thread " + boardCode + "/" + threadNo);
            if (new Date().getTime() - time > STORE_INTERVAL_MS) {
            	thread.mergePosts(posts);
                ChanFileStorage.storeThreadData(getBaseContext(), thread);
        	}
        }
        thread.mergePosts(posts);

        Log.i(TAG, "finished parsing thread " + boardCode + "/" + threadNo);
    }

    private void markDead() {
        thread.isDead = true;
        Log.i(TAG, "marked thread as dead, will not be loaded again");
    }

}
