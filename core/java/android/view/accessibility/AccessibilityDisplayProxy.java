/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view.accessibility;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.IAccessibilityServiceClient;
import android.accessibilityservice.IAccessibilityServiceConnection;
import android.accessibilityservice.MagnificationConfig;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Region;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;

import com.android.internal.inputmethod.IAccessibilityInputMethodSessionCallback;
import com.android.internal.inputmethod.RemoteAccessibilityInputConnection;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Allows a privileged app - an app with MANAGE_ACCESSIBILITY permission and SystemAPI access - to
 * interact with the windows in the display that this proxy represents. Proxying the default display
 * or a display that is not tracked by accessibility, such as private displays, will throw an
 * exception. Only the real user has access to global clients like SystemUI.
 *
 * <p>
 * To register and unregister a proxy, use
 * {@link AccessibilityManager#registerDisplayProxy(AccessibilityDisplayProxy)}
 * and {@link AccessibilityManager#unregisterDisplayProxy(AccessibilityDisplayProxy)}. If the app
 * that has registered the proxy dies, the system will remove the proxy.
 *
 * <p>
 * Avoid using the app's main thread. Proxy methods such as {@link #getWindows} and node methods
 * like {@link AccessibilityNodeInfo#getChild(int)} will happen frequently. Node methods may also
 * wait on the displayed app's UI thread to obtain accurate screen data.
 *
 * <p>
 * To get a list of {@link AccessibilityServiceInfo}s that have populated {@link ComponentName}s and
 * {@link ResolveInfo}s, retrieve the list using {@link #getInstalledAndEnabledServices()} after
 * {@link #onProxyConnected()} has been called.
 *
 * @hide
 */
@SystemApi
public abstract class AccessibilityDisplayProxy {
    private static final String LOG_TAG = "AccessibilityDisplayProxy";
    private static final int INVALID_CONNECTION_ID = -1;

    private List<AccessibilityServiceInfo> mInstalledAndEnabledServices;
    private Executor mExecutor;
    private int mConnectionId = INVALID_CONNECTION_ID;
    private int mDisplayId;
    IAccessibilityServiceClient mServiceClient;

    /**
     * Constructs an AccessibilityDisplayProxy instance.
     * @param displayId the id of the display to proxy.
     * @param executor the executor used to execute proxy callbacks.
     * @param installedAndEnabledServices the list of infos representing the installed and
     *                                    enabled a11y services.
     */
    public AccessibilityDisplayProxy(int displayId, @NonNull Executor executor,
            @NonNull List<AccessibilityServiceInfo> installedAndEnabledServices) {
        mDisplayId = displayId;
        mExecutor = executor;
        // Typically, the context is the Service context of an accessibility service.
        // Context is used for ResolveInfo check, which a proxy won't have, IME input
        // (FLAG_INPUT_METHOD_EDITOR), which the proxy doesn't need, and tracing
        // A11yInteractionClient methods.
        // TODO(254097475): Enable tracing, potentially without exposing Context.
        mServiceClient = new IAccessibilityServiceClientImpl(null, mExecutor);
        mInstalledAndEnabledServices = installedAndEnabledServices;
    }

    /**
     * Returns the id of the display being proxy-ed.
     */
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Handles {@link android.view.accessibility.AccessibilityEvent}s.
     * <p>
     * AccessibilityEvents represent changes to the UI, or what parts of the node tree have changed.
     * AccessibilityDisplayProxy should use these to query new UI and send appropriate feedback
     * to their users.
     * <p>
     * For example, a {@link AccessibilityEvent#TYPE_WINDOWS_CHANGED} indicates a change in windows,
     * so a proxy may query {@link #getWindows} to obtain updated UI and potentially inform of a new
     * window title. Or a proxy may emit an earcon on a
     * {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} event.
     */
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        // Default no-op
    }

    /**
     * Handles a successful system connection after
     * {@link AccessibilityManager#registerDisplayProxy(AccessibilityDisplayProxy)} is called.
     *
     * <p>
     * At this point, querying for UI is available and {@link AccessibilityEvent}s will begin being
     * sent. An AccessibilityDisplayProxy may instantiate core infrastructure components here.
     */
    public void onProxyConnected() {
        // Default no-op
    }

    /**
     * Handles a request to interrupt the accessibility feedback.
     * <p>
     * AccessibilityDisplayProxy should interrupt the accessibility activity occurring on its
     * display. For example, a screen reader may interrupt speech.
     *
     * @see AccessibilityManager#interrupt()
     * @see AccessibilityService#onInterrupt()
     */
    public void interrupt() {
        // Default no-op
    }

    /**
     * Gets the focus of the window specified by {@code windowInfo}.
     *
     * @param windowInfo the window to search
     * @param focus The focus to find. One of {@link AccessibilityNodeInfo#FOCUS_INPUT} or
     * {@link AccessibilityNodeInfo#FOCUS_ACCESSIBILITY}.
     * @return The node info of the focused view or null.
     * @hide
     * TODO(254545943): Do not expose until support for accessibility focus and/or input is in place
     */
    @Nullable
    public AccessibilityNodeInfo findFocus(@NonNull AccessibilityWindowInfo windowInfo, int focus) {
        AccessibilityNodeInfo windowRoot = windowInfo.getRoot();
        return windowRoot != null ? windowRoot.findFocus(focus) : null;
    }

    /**
     * Gets the windows of the tracked display.
     *
     * @see AccessibilityService#getWindows()
     */
    @NonNull
    public List<AccessibilityWindowInfo> getWindows() {
        return AccessibilityInteractionClient.getInstance().getWindowsOnDisplay(mConnectionId,
                mDisplayId);
    }

    /**
     * Sets the list of {@link AccessibilityServiceInfo}s describing the services interested in the
     * {@link AccessibilityDisplayProxy}'s display.
     *
     * <p>These represent a11y features and services that are installed and running. These should
     * not include {@link AccessibilityService}s installed on the phone.
     *
     * @param installedAndEnabledServices the list of installed and running a11y services.
     */
    public void setInstalledAndEnabledServices(
            @NonNull List<AccessibilityServiceInfo> installedAndEnabledServices) {
        mInstalledAndEnabledServices = installedAndEnabledServices;
        sendServiceInfos();
    }

    /**
     * Sets the {@link AccessibilityServiceInfo} for this service if the latter is
     * properly set and there is an {@link IAccessibilityServiceConnection} to the
     * AccessibilityManagerService.
     */
    private void sendServiceInfos() {
        IAccessibilityServiceConnection connection =
                AccessibilityInteractionClient.getInstance().getConnection(mConnectionId);
        if (mInstalledAndEnabledServices != null && mInstalledAndEnabledServices.size() > 0
                && connection != null) {
            try {
                connection.setInstalledAndEnabledServices(mInstalledAndEnabledServices);
                AccessibilityInteractionClient.getInstance().clearCache(mConnectionId);
            } catch (RemoteException re) {
                Log.w(LOG_TAG, "Error while setting AccessibilityServiceInfos", re);
                re.rethrowFromSystemServer();
            }
        }
        mInstalledAndEnabledServices = null;
    }

    /**
     * Gets the list of {@link AccessibilityServiceInfo}s describing the services interested in the
     * {@link AccessibilityDisplayProxy}'s display.
     *
     * @return The {@link AccessibilityServiceInfo}s of interested services.
     * @see AccessibilityServiceInfo
     */
    @NonNull
    public final List<AccessibilityServiceInfo> getInstalledAndEnabledServices() {
        IAccessibilityServiceConnection connection =
                AccessibilityInteractionClient.getInstance().getConnection(mConnectionId);
        if (connection != null) {
            try {
                return connection.getInstalledAndEnabledServices();
            } catch (RemoteException re) {
                Log.w(LOG_TAG, "Error while getting AccessibilityServiceInfo", re);
                re.rethrowFromSystemServer();
            }
        }
        return Collections.emptyList();
    }

    /**
     * An IAccessibilityServiceClient that handles interrupts, accessibility events, and system
     * connection.
     */
    private class IAccessibilityServiceClientImpl extends
            AccessibilityService.IAccessibilityServiceClientWrapper {

        IAccessibilityServiceClientImpl(Context context, Executor executor) {
            super(context, executor, new AccessibilityService.Callbacks() {
                @Override
                public void onAccessibilityEvent(AccessibilityEvent event) {
                    // TODO(254545943): Remove check when event processing is done more upstream in
                    // AccessibilityManagerService.
                    if (event.getDisplayId() == mDisplayId) {
                        AccessibilityDisplayProxy.this.onAccessibilityEvent(event);
                    }
                }

                @Override
                public void onInterrupt() {
                    AccessibilityDisplayProxy.this.interrupt();
                }

                @Override
                public void onServiceConnected() {
                    AccessibilityDisplayProxy.this.sendServiceInfos();
                    AccessibilityDisplayProxy.this.onProxyConnected();
                }

                @Override
                public void init(int connectionId, IBinder windowToken) {
                    mConnectionId = connectionId;
                }

                @Override
                public boolean onGesture(AccessibilityGestureEvent gestureInfo) {
                    return false;
                }

                @Override
                public boolean onKeyEvent(KeyEvent event) {
                    return false;
                }

                @Override
                public void onMagnificationChanged(int displayId, @NonNull Region region,
                        MagnificationConfig config) {
                }

                @Override
                public void onMotionEvent(MotionEvent event) {
                }

                @Override
                public void onTouchStateChanged(int displayId, int state) {
                }

                @Override
                public void onSoftKeyboardShowModeChanged(int showMode) {
                }

                @Override
                public void onPerformGestureResult(int sequence, boolean completedSuccessfully) {
                }

                @Override
                public void onFingerprintCapturingGesturesChanged(boolean active) {
                }

                @Override
                public void onFingerprintGesture(int gesture) {
                }

                @Override
                public void onAccessibilityButtonClicked(int displayId) {
                }

                @Override
                public void onAccessibilityButtonAvailabilityChanged(boolean available) {
                }

                @Override
                public void onSystemActionsChanged() {
                }

                @Override
                public void createImeSession(IAccessibilityInputMethodSessionCallback callback) {
                }

                @Override
                public void startInput(@Nullable RemoteAccessibilityInputConnection inputConnection,
                        @NonNull EditorInfo editorInfo, boolean restarting) {
                }
            });
        }
    }
}
