/**
 * Copyright (c) 2016 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.taskomatic.task.sshpush.test;

import com.google.gson.JsonElement;
import com.redhat.rhn.domain.action.Action;
import com.redhat.rhn.domain.action.ActionFactory;
import com.redhat.rhn.domain.action.server.ServerAction;
import com.redhat.rhn.domain.action.test.ActionFactoryTest;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.test.MinionServerFactoryTest;
import com.redhat.rhn.taskomatic.task.sshpush.SSHPushWorkerSalt;
import com.redhat.rhn.testing.JMockBaseTestCaseWithUser;
import com.suse.manager.webui.controllers.utils.test.SSHMinionBootstrapperTest;
import com.suse.manager.webui.services.impl.SaltService;
import com.suse.salt.netapi.calls.LocalCall;
import org.apache.log4j.Logger;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;

import java.util.Collections;
import java.util.Optional;

/**
 * SSHPushWorkerSaltTest
 */
public class SSHPushWorkerSaltTest extends JMockBaseTestCaseWithUser {

    private Logger logger = Logger.getLogger(SSHMinionBootstrapperTest.class);

    private SSHPushWorkerSalt worker;
    private MinionServer minion;
    private SaltService saltServiceMock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setImposteriser(ClassImposteriser.INSTANCE);
        saltServiceMock = mock(SaltService.class);
        worker = new SSHPushWorkerSalt(logger, null, saltServiceMock); //yuck
        minion = MinionServerFactoryTest.createTestMinionServer(user);
    }

    /**
     * Tests that an attempt to execute action that has been already completed will not
     * invoke any salt calls and that the state of the action doesn't change.
     *
     * @throws Exception if anything goes wrong
     */
    public void testDontExecuteCompletedAction() throws Exception {
        expectNoSaltCalls();
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setStatus(ActionFactory.STATUS_COMPLETED);
        serverAction.setRemainingTries(5L);
        action.setServerActions(Collections.singleton(serverAction));

        worker.executeAction(action, minion);

        assertEquals(ActionFactory.STATUS_COMPLETED, serverAction.getStatus());
        assertEquals(Long.valueOf(5L), serverAction.getRemainingTries());
    }

    /**
     * Tests that an attempt to execute action that has already failed will not
     * invoke any salt calls.
     *
     * @throws Exception if anything goes wrong
     */
    public void testDontExecuteFailedAction() throws Exception {
        expectNoSaltCalls();
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setStatus(ActionFactory.STATUS_FAILED);
        serverAction.setRemainingTries(5L);
        action.setServerActions(Collections.singleton(serverAction));

        worker.executeAction(action, minion);

        assertEquals(ActionFactory.STATUS_FAILED, serverAction.getStatus());
        assertEquals(Long.valueOf(5L), serverAction.getRemainingTries());
    }

    /**
     * Tests that an action with no remaining tries will be set to the failed state
     * (with a corresponding message) and that it will not invoke any salt calls.
     *
     * @throws Exception if anything goes wrong
     */
    public void testExecuteActionNoRemainingTries() throws Exception {
        expectNoSaltCalls();
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        serverAction.setRemainingTries(0L);
        action.setServerActions(Collections.singleton(serverAction));
        worker.executeAction(action, minion);

        assertEquals(ActionFactory.STATUS_FAILED, serverAction.getStatus());
        assertEquals(
                "Action has been picked up multiple times" +
                        " without a successful transaction;" +
                        " This action is now failed for this system.",
                serverAction.getResultMsg());
        ActionFactory.getSession().flush();
        assertEquals(Long.valueOf(1L), action.getFailedCount());
    }

    /**
     * Tests that an action with a failed prerequisite will set be to the failed state
     * (with a corresponding message) and that it will not invoke any salt calls.
     *
     * @throws Exception if anything goes wrong
     */
    public void testDontExecuteActionWhenPrerequisiteFailed() throws Exception {
        expectNoSaltCalls();

        // prerequisite failed
        Action prereq = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction prereqServerAction =
                ActionFactoryTest.createServerAction(minion, prereq);
        prereqServerAction.setStatus(ActionFactory.STATUS_FAILED);
        prereq.setServerActions(Collections.singleton(prereqServerAction));

        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        action.setPrerequisite(prereq);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        serverAction.setRemainingTries(5L);
        action.setServerActions(Collections.singleton(serverAction));
        worker.executeAction(action, minion);

        assertEquals(ActionFactory.STATUS_FAILED, serverAction.getStatus());
        assertEquals("Prerequisite failed.", serverAction.getResultMsg());
        // this comes from the xmlrpc/queue.py
        assertEquals(Long.valueOf(-100L), serverAction.getResultCode());
        ActionFactory.getSession().flush();
        assertEquals(Long.valueOf(1L), action.getFailedCount());
    }

    /**
     * Tests that the successful execution of an action correctly sets the status and the
     * number of remaining tries.
     *
     * @throws Exception if anything goes wrong
     */
    public void testExecuteActionSuccess() throws Exception {
        worker = successWorker();

        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            Optional<JsonElement> result = Optional.of(mock(JsonElement.class));
            will(returnValue(result));
        }});

        // create action without servers
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setRemainingTries(5L);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        action.setServerActions(Collections.singleton(serverAction));
        worker.executeAction(action, minion);

        assertEquals(Long.valueOf(4L), serverAction.getRemainingTries());
        assertEquals(ActionFactory.STATUS_COMPLETED, serverAction.getStatus());
    }

    /**
     * Tests that an execution with empty result from salt keeps the action in the queued
     * state and decreases the number of tries.
     *
     * @throws Exception if anything goes wrong
     */
    public void testExecuteActionRetryOnEmptyResult() throws Exception {
        // expect salt returning empty result
        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            will(returnValue(Optional.empty()));
        }});
        assertActionWillBeRetried();
    }

    /**
     * Tests that an execution with exception from salt keeps the action in the queued
     * state and decreases the number of tries.
     *
     * @throws Exception if anything goes wrong
     */
    public void testExecuteActionRetryOnException() throws Exception {
        // expect salt returning empty result
        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            will(throwException(new RuntimeException()));
        }});
        try {
            assertActionWillBeRetried();
        } catch (RuntimeException e) {
            // expected
            return;
        }
        fail("Runtime exception should have been thrown.");
    }

    private void assertActionWillBeRetried() throws Exception {
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setRemainingTries(5L);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        action.setServerActions(Collections.singleton(serverAction));
        worker.executeAction(action, minion);

        assertEquals(Long.valueOf(4L), serverAction.getRemainingTries());
        assertEquals(ActionFactory.STATUS_QUEUED, serverAction.getStatus());
    }

    /**
     * Tests the following scenario:
     *  - execute an action, it fails (empty result from salt)
     *  - check that action is still queued and has decreased number of tries
     *  - execute the action again, now it succeeds
     *  - check that action is completed and has decreased number of tries
     *
     * @throws Exception if anything goes wrong
     */
    public void testSuccessRetryAfterEmptyResult() throws Exception {
        // firstly, let's simulate a unsuccessful call
        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            will(returnValue(Optional.empty()));
        }});
        successAfterRetryHelper();
    }

    /**
     * Tests the following scenario:
     *  - execute an action, it fails (exception from salt)
     *  - check that action is still queued and has decreased number of tries
     *  - execute the action again, now it succeeds
     *  - check that action is completed and has decreased number of tries
     *
     * @throws Exception if anything goes wrong
     */
    public void testSuccessRetryAfterException() throws Exception {
        // firstly, let's simulate a unsuccessful call
        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            will(throwException(new RuntimeException()));
        }});
        successAfterRetryHelper();
    }

    /**
     * Tests that execution skips server actions which still have queued prerequisite
     * server actions.
     *
     * @throws Exception if anything goes wrong
     */
    public void testSkipActionWhenPrerequisiteQueued() throws Exception {
        expectNoSaltCalls();
        worker = successWorker();

        // prerequisite is still queued
        Action prereq = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction prereqServerAction =
                ActionFactoryTest.createServerAction(minion, prereq);
        prereqServerAction.setRemainingTries(5L);
        prereqServerAction.setStatus(ActionFactory.STATUS_QUEUED);
        prereq.setServerActions(Collections.singleton(prereqServerAction));

        // action is queued as well
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        action.setPrerequisite(prereq);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        serverAction.setRemainingTries(5L);
        action.setServerActions(Collections.singleton(serverAction));

        worker.executeAction(action, minion);

        // both status and remaining tries should remain unchanged
        assertEquals(ActionFactory.STATUS_QUEUED, serverAction.getStatus());
        assertEquals(Long.valueOf(5L), serverAction.getRemainingTries());
    }

    private void successAfterRetryHelper() throws Exception {
        Action action = ActionFactoryTest.createAction(user, ActionFactory.TYPE_SCRIPT_RUN);
        ServerAction serverAction = ActionFactoryTest.createServerAction(minion, action);
        serverAction.setRemainingTries(5L);
        serverAction.setStatus(ActionFactory.STATUS_QUEUED);
        action.setServerActions(Collections.singleton(serverAction));

        try {
            worker.executeAction(action, minion);
        } catch (RuntimeException e) {
            // no-op
        }

        // should be still STATUS_QUEUED, number of tries is decreased
        assertEquals(Long.valueOf(4L), serverAction.getRemainingTries());
        assertEquals(ActionFactory.STATUS_QUEUED, serverAction.getStatus());

        // we create a salt service that succeeds
        context().checking(new Expectations() {{
            oneOf(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
            Optional<JsonElement> result = Optional.of(mock(JsonElement.class));
            will(returnValue(result));
        }});

        // repeat the execution with successful result
        worker = successWorker();
        worker.executeAction(action, minion);

        // should be still STATUS_COMPLETED, number of tries is decreased
        assertEquals(Long.valueOf(3L), serverAction.getRemainingTries());
        assertEquals(ActionFactory.STATUS_COMPLETED, serverAction.getStatus());
    }

    private void expectNoSaltCalls() {
        context().checking(new Expectations() {{
            // we never invoke call for actions that are out of remaining tries!
            never(saltServiceMock).callSync(
                    with(any(LocalCall.class)),
                    with(any(String.class)));
        }});
    }

    private SSHPushWorkerSalt successWorker() {
        return new SSHPushWorkerSalt(logger, null, saltServiceMock) {
            @Override
            public boolean shouldRefreshPackageList(String function,
                    Optional<JsonElement> result) {
                return false;
            }

            @Override
            public void updateServerAction(ServerAction sa, JsonElement r,
                    String function) {
                sa.setStatus(ActionFactory.STATUS_COMPLETED);
            }
        };
    }
}