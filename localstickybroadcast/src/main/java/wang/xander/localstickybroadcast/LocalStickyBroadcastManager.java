package wang.xander.localstickybroadcast;

/**
 * Created by wangxiaoyang on 2017/4/15.
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;


/**
 * Helper to register for and send broadcasts of Intents to local objects
 * within your process.  This has a number of advantages over sending
 * global broadcasts with {@link android.content.Context#sendBroadcast}:
 * <ul>
 * <li> You know that the data you are broadcasting won't leave your app, so
 * don't need to worry about leaking private data.
 * <li> It is not possible for other applications to send these broadcasts to
 * your app, so you don't need to worry about having security holes they can
 * exploit.
 * <li> It is more efficient than sending a global broadcast through the
 * system.
 * </ul>
 */
public final class LocalStickyBroadcastManager {

    private static class ReceiverRecord {
        final IntentFilter filter; // 广播接收器接受的广播
        final BroadcastReceiver receiver; //对应的广播接收器
        boolean broadcasting;

        ReceiverRecord(IntentFilter _filter, BroadcastReceiver _receiver) {
            filter = _filter;
            receiver = _receiver;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(128);
            builder.append("Receiver{");
            builder.append(receiver);
            builder.append(" filter=");
            builder.append(filter);
            builder.append("}");
            return builder.toString();
        }
    }

    private static class BroadcastRecord {
        final Intent intent;//广播
        final ArrayList<ReceiverRecord> receivers;// 对应的广播接收器列表

        BroadcastRecord(Intent _intent, ArrayList<ReceiverRecord> _receivers) {
            intent = _intent;
            receivers = _receivers;
        }
    }

    private static final String TAG = "LocalStickyBroadcastManager";
    private static final boolean DEBUG = false;

    private final Context mAppContext;

    // 注册的广播
    private final HashMap<BroadcastReceiver, ArrayList<IntentFilter>> mReceivers
            = new HashMap<BroadcastReceiver, ArrayList<IntentFilter>>();
    // action 和广播的映射
    private final HashMap<String, ArrayList<ReceiverRecord>> mActions
            = new HashMap<String, ArrayList<ReceiverRecord>>();
    // 等待被处理的广播
    private final ArrayList<BroadcastRecord> mPendingBroadcasts
            = new ArrayList<BroadcastRecord>();

    // 暂时没有找到合适的广播接收器的 intent
    private final ArrayList<Intent> mStickerIntents =
            new ArrayList<Intent>();

    static final int MSG_EXEC_PENDING_BROADCASTS = 1;

    private final Handler mHandler;

    private static final Object mLock = new Object();
    private static LocalStickyBroadcastManager mInstance;

    public static LocalStickyBroadcastManager getInstance(Context context) {
        synchronized (mLock) {
            if (mInstance == null) {
                mInstance = new LocalStickyBroadcastManager(context.getApplicationContext());
            }
            return mInstance;
        }
    }

    private LocalStickyBroadcastManager(Context context) {
        mAppContext = context;
        mHandler = new Handler(context.getMainLooper()) {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_EXEC_PENDING_BROADCASTS:
                        executePendingBroadcasts();
                        break;
                    default:
                        super.handleMessage(msg);
                }
            }
        };
    }

    /**
     * Register a receive for any local broadcasts that match the given IntentFilter.
     *
     * @param receiver The BroadcastReceiver to handle the broadcast.
     * @param filter   Selects the Intent broadcasts to be received.
     * @see #unregisterReceiver
     */
    public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        synchronized (mReceivers) {
            // 生成接收器
            ReceiverRecord entry = new ReceiverRecord(filter, receiver);
            ArrayList<IntentFilter> filters = mReceivers.get(receiver);
            if (filters == null) {
                filters = new ArrayList<IntentFilter>(1);
                mReceivers.put(receiver, filters);
            }
            filters.add(filter);
            // action 和接收器绑定
            for (int i = 0; i < filter.countActions(); i++) {
                String action = filter.getAction(i);
                ArrayList<ReceiverRecord> entries = mActions.get(action);
                if (entries == null) {
                    entries = new ArrayList<ReceiverRecord>(1);
                    mActions.put(action, entries);
                }
                entries.add(entry);
            }
            // 如果有广播未被处理，应该处理
            for (Intent intent : mStickerIntents) {
                sendBroadcast(intent);
            }
            mStickerIntents.clear();
        }
    }

    /**
     * Unregister a previously registered BroadcastReceiver.  <em>All</em>
     * filters that have been registered for this BroadcastReceiver will be
     * removed.
     *
     * @param receiver The BroadcastReceiver to unregister.
     * @see #registerReceiver
     */
    public void unregisterReceiver(BroadcastReceiver receiver) {
        synchronized (mReceivers) {
            ArrayList<IntentFilter> filters = mReceivers.remove(receiver);
            if (filters == null) {
                return;
            }
            for (int i = 0; i < filters.size(); i++) {
                IntentFilter filter = filters.get(i);
                for (int j = 0; j < filter.countActions(); j++) {
                    String action = filter.getAction(j);
                    ArrayList<ReceiverRecord> receivers = mActions.get(action);
                    if (receivers != null) {
                        for (int k = 0; k < receivers.size(); k++) {
                            if (receivers.get(k).receiver == receiver) {
                                receivers.remove(k);
                                k--;
                            }
                        }
                        if (receivers.size() <= 0) {
                            mActions.remove(action);
                        }
                    }
                }
            }
        }
    }

    /**
     * Broadcast the given intent to all interested BroadcastReceivers.  This
     * call is asynchronous; it returns immediately, and you will continue
     * executing while the receivers are run.
     *
     * @param intent The Intent to broadcast; all receivers matching this
     *               Intent will receive the broadcast.
     * @see #registerReceiver
     */
    public boolean sendBroadcast(Intent intent) {
        return sendBroadcast(intent, false);
    }

    public boolean sendStickerBroadcast(Intent intent) {
        return sendBroadcast(intent, true);
    }

    private boolean sendBroadcast(Intent intent, boolean isSticker) {
        synchronized (mReceivers) {
            final String action = intent.getAction();
            final String type = intent.resolveTypeIfNeeded(mAppContext.getContentResolver());
            final Uri data = intent.getData();
            final String scheme = intent.getScheme();
            final Set<String> categories = intent.getCategories();

            final boolean debug = DEBUG ||
                    ((intent.getFlags() & Intent.FLAG_DEBUG_LOG_RESOLUTION) != 0);
            if (debug) Log.v(
                    TAG, "Resolving type " + type + " scheme " + scheme
                            + " of intent " + intent);

            // 获取接收器列表
            ArrayList<ReceiverRecord> entries = mActions.get(action);
            if (entries != null) {
                if (debug) Log.v(TAG, "Action list: " + entries);

                ArrayList<ReceiverRecord> receivers = null;
                for (int i = 0; i < entries.size(); i++) {
                    ReceiverRecord receiver = entries.get(i);
                    if (debug) Log.v(TAG, "Matching against filter " + receiver.filter);

                    if (receiver.broadcasting) {
                        if (debug) {
                            Log.v(TAG, "  Filter's target already added");
                        }
                        continue;
                    }

                    int match = receiver.filter.match(
                            action,
                            type,
                            scheme,
                            data,
                            categories,
                            TAG
                    );
                    if (match >= 0) {
                        if (debug) Log.v(TAG, "  Filter matched!  match=0x" +
                                Integer.toHexString(match));
                        if (receivers == null) {
                            receivers = new ArrayList<ReceiverRecord>();
                        }
                        // 加入接收器
                        receivers.add(receiver);
                        receiver.broadcasting = true;
                    } else {
                        if (debug) {
                            String reason;
                            switch (match) {
                                case IntentFilter.NO_MATCH_ACTION:
                                    reason = "action";
                                    break;
                                case IntentFilter.NO_MATCH_CATEGORY:
                                    reason = "category";
                                    break;
                                case IntentFilter.NO_MATCH_DATA:
                                    reason = "data";
                                    break;
                                case IntentFilter.NO_MATCH_TYPE:
                                    reason = "type";
                                    break;
                                default:
                                    reason = "unknown reason";
                                    break;
                            }
                            Log.v(TAG, "  Filter did not match: " + reason);
                        }
                    }
                }

                if (receivers != null) {
                    for (int i = 0; i < receivers.size(); i++) {
                        receivers.get(i).broadcasting = false;
                    }
                    mPendingBroadcasts.add(new BroadcastRecord(intent, receivers));
                    if (!mHandler.hasMessages(MSG_EXEC_PENDING_BROADCASTS)) {
                        mHandler.sendEmptyMessage(MSG_EXEC_PENDING_BROADCASTS);
                    }
                    return true;
                }
            } else {
                // 到这里说明没有广播接收器接收
                mStickerIntents.add(intent);
            }
        }
        return false;
    }

    /**
     * Like {@link #sendBroadcast(Intent)}, but if there are any receivers for
     * the Intent this function will block and immediately dispatch them before
     * returning.
     */
    public void sendBroadcastSync(Intent intent) {
        if (sendBroadcast(intent)) {
            executePendingBroadcasts();
        }
    }

    private void executePendingBroadcasts() {
        while (true) {
            BroadcastRecord[] brs = null;
            synchronized (mReceivers) {
                final int N = mPendingBroadcasts.size();
                if (N <= 0) {
                    return;
                }
                brs = new BroadcastRecord[N];
                mPendingBroadcasts.toArray(brs);
                mPendingBroadcasts.clear();
            }
            for (int i = 0; i < brs.length; i++) {
                BroadcastRecord br = brs[i];
                for (int j = 0; j < br.receivers.size(); j++) {
                    br.receivers.get(j).receiver.onReceive(mAppContext, br.intent);
                }
            }
        }
    }
}
