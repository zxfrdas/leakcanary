/*
 * Copyright (C) 2015 Square, Inc.
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
package com.squareup.leakcanary;

import java.io.File;
import org.junit.Test;

import static com.squareup.leakcanary.LeakTraceElement.Holder.THREAD;
import static com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class HeapAnalyzerTest {

  static final ExcludedRefs NONE = new ExcludedRefs.Builder().build();

  static final String ASYNC_TASK_THREAD = "AsyncTask #1";
  static final String ASYNC_TASK_CLASS = "android.os.AsyncTask";
  static final String EXECUTOR_FIELD = "SERIAL_EXECUTOR";

  @Test public void leakFound() {
    AnalysisResult result = analyze(new HeapAnalyzer(NONE));
    LeakTraceElement gcRoot = result.leakTrace.elements.get(0);
    assertTrue(result.leakFound);
    assertFalse(result.excludedLeak);
    assertEquals(Thread.class.getName(), gcRoot.className);
    assertEquals(THREAD, gcRoot.holder);
    assertThat(gcRoot.extra, containsString(ASYNC_TASK_THREAD));
  }

  @Test public void excludeThread() {
    ExcludedRefs.Builder excludedRefs = new ExcludedRefs.Builder();
    excludedRefs.thread(ASYNC_TASK_THREAD);
    AnalysisResult result = analyze(new HeapAnalyzer(excludedRefs.build()));
    assertTrue(result.leakFound);
    assertFalse(result.excludedLeak);
    LeakTraceElement gcRoot = result.leakTrace.elements.get(0);
    assertEquals(ASYNC_TASK_CLASS, gcRoot.className);
    assertEquals(STATIC_FIELD, gcRoot.type);
    assertEquals(EXECUTOR_FIELD, gcRoot.referenceName);
  }

  @Test public void excludeStatic() {
    ExcludedRefs.Builder excludedRefs = new ExcludedRefs.Builder();
    excludedRefs.thread(ASYNC_TASK_THREAD);
    excludedRefs.staticField(ASYNC_TASK_CLASS, EXECUTOR_FIELD);
    AnalysisResult result = analyze(new HeapAnalyzer(excludedRefs.build()));
    assertTrue(result.leakFound);
    assertTrue(result.excludedLeak);
  }

  @Test public void excludeStaticForBase() {
    ExcludedRefs.Builder excludedRefs = new ExcludedRefs.Builder();
    excludedRefs.thread(ASYNC_TASK_THREAD);
    excludedRefs.staticField(ASYNC_TASK_CLASS, EXECUTOR_FIELD);
    AnalysisResult result = analyze(new HeapAnalyzer(excludedRefs.build(), excludedRefs.build()));
    assertFalse(result.leakFound);
  }

  @Test public void repro() throws Exception {
    ExcludedRefs.Builder excluded = new ExcludedRefs.Builder();
    excluded.instanceField("android.app.ActivityThread$ActivityClientRecord", "nextIdle");
    excluded.instanceField("android.widget.Editor$EasyEditSpanController", "this$0");
    excluded.instanceField("android.widget.Editor$SpanController", "this$0");
    excluded.staticField("android.media.session.MediaSessionLegacyHelper", "sInstance");
    excluded.staticField("android.text.TextLine", "sCached");
    excluded.instanceField("android.os.Message", "obj");
    excluded.instanceField("android.os.Message", "next");
    excluded.instanceField("android.os.Message", "target");
    excluded.instanceField("android.view.inputmethod.InputMethodManager", "mNextServedView");
    excluded.instanceField("android.view.inputmethod.InputMethodManager", "mServedView");
    excluded.instanceField("android.view.inputmethod.InputMethodManager",
        "mServedInputConnection");
    excluded.instanceField("android.view.inputmethod.InputMethodManager", "mCurRootView");
    excluded.instanceField("android.animation.LayoutTransition$1", "val$parent");
    excluded.instanceField("android.view.textservice.SpellCheckerSession$1", "this$0");
    excluded.staticField("android.support.v7.internal.widget.ActivityChooserModel",
        "mActivityChoserModelPolicy");
    excluded.staticField("android.widget.ActivityChooserModel", "mActivityChoserModelPolicy");
    excluded.instanceField("android.speech.SpeechRecognizer$InternalListener", "this$0");
    excluded.instanceField("android.accounts.AccountManager$AmsTask$Response", "this$1");
    excluded.instanceField("android.media.MediaScannerConnection", "mContext");
    excluded.instanceField("android.os.UserManager", "mContext");
    excluded.instanceField("android.app.admin.DevicePolicyManager$SettingsObserver", "this$0");
    excluded.staticField("com.samsung.android.smartclip.SpenGestureManager", "mContext");
    excluded.staticField("android.sec.clipboard.ClipboardUIManager", "sInstance");
    excluded.staticField("android.widget.BubblePopupHelper", "sHelper");
    excluded.staticField("com.android.org.chromium.android_webview.AwResource", "sResources");
    excluded.instanceField("com.nvidia.ControllerMapper.MapperClient$ServiceClient", "this$0");
    excluded.staticField("android.widget.TextView", "mLastHoveredView");
    excluded.instanceField("android.os.PersonaManager", "mContext");
    excluded.instanceField("android.content.res.Resources", "mContext");
    excluded.instanceField("android.view.ViewConfiguration", "mContext");
    excluded.staticField("android.media.AudioManager", "mContext_static");
    excluded.thread("FinalizerWatchdogDaemon");
    excluded.thread("main");
    excluded.thread("LeakCanary-Heap-Dump");
    excluded.instanceField("android.view.Choreographer.FrameDisplayEventReceiver",
        "mMessageQueue");
    excluded.instanceField("android.app.ActivityThread$ActivityClientRecord", "nextIdle");


    HeapAnalyzer heapAnalyzer = new HeapAnalyzer(excluded.build());
    File heapDumpFile = new File("/Users/py/dev/leakcanary/leakcanary-analyzer/src/test/resources/heap_dump_0.hprof");
    AnalysisResult result =
        heapAnalyzer.checkForLeak(heapDumpFile, "ee5aac58-f9ad-4b50-86b7-0d0ce10a1f1e");

    throw result.failure;
  }

  private AnalysisResult analyze(HeapAnalyzer heapAnalyzer) {
    File heapDumpFile = new File(Thread.currentThread()
        .getContextClassLoader()
        .getResource("leak_asynctask.hprof")
        .getPath());
    return heapAnalyzer.checkForLeak(heapDumpFile, "dc983a12-d029-4003-8890-7dd644c664c5");
  }
}
