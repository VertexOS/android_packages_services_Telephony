/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.phone.vvm.omtp.scheduling;

import com.android.phone.vvm.omtp.scheduling.BaseTask.Clock;

import org.junit.After;
import org.junit.Before;

public class BaseTaskTestBase extends TaskSchedulerServiceTestBase {

    /**
     * "current time" of the deterministic clock.
     */
    public long mTime;

    @Before
    public void setUpBaseTaskTest() {
        mTime = 0;
        BaseTask.setClockForTesting(new TestClock());
    }

    @After
    public void tearDownBaseTaskTest() {
        BaseTask.setClockForTesting(new Clock());
    }


    private class TestClock extends Clock {

        @Override
        public long getTimeMillis() {
            return mTime;
        }
    }
}
