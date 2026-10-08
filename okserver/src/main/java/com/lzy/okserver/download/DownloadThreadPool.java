/*
 * Copyright 2016 jeasonlzy(廖子尧)
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
package com.lzy.okserver.download;

import com.lzy.okserver.task.XExecutor;
import com.lzy.okserver.task.PriorityBlockingQueue;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ================================================
 * Author: jeasonlzy (廖子尧). GitHub: https://github.com/jeasonlzy
 * Version: 1.0
 * Created: 2016/1/19
 * Description: Download manager thread pool
 * Revision history:
 * ================================================
 */
public class DownloadThreadPool {
    private static final int MAX_POOL_SIZE = 5;          //Maximum thread count
    private static final int KEEP_ALIVE_TIME = 1;        //Keep-alive duration
    private static final TimeUnit UNIT = TimeUnit.HOURS; //Time unit
    private int corePoolSize = 3;                        //Core thread count and concurrent task limit; defaults to three
    private XExecutor executor;               //Thread pool executor

    public XExecutor getExecutor() {
        if (executor == null) {
            synchronized (DownloadThreadPool.class) {
                if (executor == null) {
                    executor = new XExecutor(corePoolSize, MAX_POOL_SIZE, KEEP_ALIVE_TIME, UNIT, //
                                             new PriorityBlockingQueue<Runnable>(),   //Unbounded work queue
                                             Executors.defaultThreadFactory(),        //Thread factory
                                             new ThreadPoolExecutor.AbortPolicy());   //Reject tasks beyond the limit by blocking
                }
            }
        }
        return executor;
    }

    /** Set before the first task runs; otherwise ineffective. Valid range: 1–5. */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize <= 0) corePoolSize = 1;
        if (corePoolSize > MAX_POOL_SIZE) corePoolSize = MAX_POOL_SIZE;
        this.corePoolSize = corePoolSize;
    }

    /** Run a task */
    public void execute(Runnable runnable) {
        if (runnable != null) {
            getExecutor().execute(runnable);
        }
    }

    /** Remove a thread */
    public void remove(Runnable runnable) {
        if (runnable != null) {
            getExecutor().remove(runnable);
        }
    }
}
