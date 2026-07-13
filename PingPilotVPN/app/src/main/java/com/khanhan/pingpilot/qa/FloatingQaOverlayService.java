package com.khanhan.pingpilot.qa;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.khanhan.pingpilot.MainActivity;
import com.khanhan.pingpilot.R;

/**
 * Floating debug controls for internal QA builds.
 *
 * <p>The native module remains process-local and only affects UDP sendto calls
 * made by this APK process.</p>
 */
public final class FloatingQaOverlayService extends Service {

    public static final String ACTION_START =
            "com.khanhan.pingpilot.qa.action.START";
    public static final String ACTION_STOP =
            "com.khanhan.pingpilot.qa.action.STOP";

    private static final String CHANNEL_ID = "network_qa_overlay";
    private static final int NOTIFICATION_ID = 2207;
    private static final int DEFAULT_LATENCY_MS = 300;

    private WindowManager windowManager;
    private View overlayView;
    private boolean nativeLibraryReady;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (overlayView == null && Settings.canDrawOverlays(this)) {
            showOverlay();
        }

        return START_STICKY;
    }

    private void showOverlay() {
        if (overlayView != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(
                R.layout.overlay_network_qa,
                null,
                false
        );

        final WindowManager.LayoutParams layoutParams =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 24;
        layoutParams.y = 180;

        final View bubble = overlayView.findViewById(R.id.qa_bubble);
        final View panel = overlayView.findViewById(R.id.qa_panel);
        final View collapseButton = overlayView.findViewById(R.id.button_collapse_overlay);
        final View dragHandle = overlayView.findViewById(R.id.qa_drag_handle);
        final TextView statusText = overlayView.findViewById(R.id.qa_status);
        final Switch latencySwitch = overlayView.findViewById(R.id.switch_latency);
        final Switch packetDropSwitch = overlayView.findViewById(R.id.switch_packet_drop);
        final Button closeButton = overlayView.findViewById(R.id.button_close_overlay);

        nativeLibraryReady = initializeNativeModule(statusText);
        latencySwitch.setEnabled(nativeLibraryReady);
        packetDropSwitch.setEnabled(nativeLibraryReady);

        latencySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!nativeLibraryReady) {
                buttonView.setChecked(false);
                return;
            }

            try {
                NativeBridge.setLatencyMs(DEFAULT_LATENCY_MS);
                NativeBridge.toggleLatencySim(isChecked);
                updateStatus(statusText, latencySwitch, packetDropSwitch);
            } catch (Throwable throwable) {
                nativeLibraryReady = false;
                disableControls(statusText, latencySwitch, packetDropSwitch, throwable);
            }
        });

        packetDropSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!nativeLibraryReady) {
                buttonView.setChecked(false);
                return;
            }

            try {
                NativeBridge.togglePacketDrop(isChecked);
                updateStatus(statusText, latencySwitch, packetDropSwitch);
            } catch (Throwable throwable) {
                nativeLibraryReady = false;
                disableControls(statusText, latencySwitch, packetDropSwitch, throwable);
            }
        });

        closeButton.setOnClickListener(view -> stopSelf());
        collapseButton.setOnClickListener(view -> {
            panel.setVisibility(View.GONE);
            bubble.setVisibility(View.VISIBLE);
            refreshOverlayLayout(layoutParams);
        });

        installBubbleTouchHandler(bubble, panel, layoutParams);
        installDragHandler(dragHandle, layoutParams);

        try {
            windowManager.addView(overlayView, layoutParams);
        } catch (RuntimeException exception) {
            overlayView = null;
            stopSelf();
        }
    }

    private boolean initializeNativeModule(TextView statusText) {
        try {
            NativeBridge.setLatencyMs(DEFAULT_LATENCY_MS);
            final boolean installed = NativeBridge.installHooks();
            statusText.setText(
                    installed
                            ? "NDK hook ready · process-local UDP"
                            : "NDK hook installation failed"
            );
            return installed;
        } catch (Throwable throwable) {
            statusText.setText("Native load failed: " + throwable.getClass().getSimpleName());
            return false;
        }
    }

    private void updateStatus(
            TextView statusText,
            Switch latencySwitch,
            Switch packetDropSwitch
    ) {
        if (packetDropSwitch.isChecked()) {
            statusText.setText("Packet drop simulation enabled");
        } else if (latencySwitch.isChecked()) {
            statusText.setText("Latency simulation enabled · 300 ms");
        } else {
            statusText.setText("NDK hook ready · normal network");
        }
    }

    private void disableControls(
            TextView statusText,
            Switch latencySwitch,
            Switch packetDropSwitch,
            Throwable throwable
    ) {
        latencySwitch.setEnabled(false);
        packetDropSwitch.setEnabled(false);
        statusText.setText("Native error: " + throwable.getClass().getSimpleName());
    }

    private void installBubbleTouchHandler(
            View bubble,
            View panel,
            WindowManager.LayoutParams layoutParams
    ) {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean dragged;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        dragged = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        final float deltaX = event.getRawX() - initialTouchX;
                        final float deltaY = event.getRawY() - initialTouchY;
                        if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                            dragged = true;
                        }

                        if (dragged) {
                            layoutParams.x = initialX + Math.round(deltaX);
                            layoutParams.y = initialY + Math.round(deltaY);
                            refreshOverlayLayout(layoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!dragged) {
                            bubble.setVisibility(View.GONE);
                            panel.setVisibility(View.VISIBLE);
                            refreshOverlayLayout(layoutParams);
                            view.performClick();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void installDragHandler(
            View dragHandle,
            WindowManager.LayoutParams layoutParams
    ) {
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX
                                + Math.round(event.getRawX() - initialTouchX);
                        layoutParams.y = initialY
                                + Math.round(event.getRawY() - initialTouchY);
                        refreshOverlayLayout(layoutParams);
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void refreshOverlayLayout(WindowManager.LayoutParams layoutParams) {
        if (windowManager == null || overlayView == null) {
            return;
        }

        try {
            windowManager.updateViewLayout(overlayView, layoutParams);
        } catch (RuntimeException ignored) {
            // The overlay may be detaching while the service stops.
        }
    }

    private void createNotificationChannel() {
        final NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            return;
        }

        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Network QA overlay",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Internal network emulation debug controls");
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        final Intent launchIntent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Network QA overlay active")
                .setContentText("Tap the floating QA bubble to open controls")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (nativeLibraryReady) {
            try {
                NativeBridge.toggleLatencySim(false);
                NativeBridge.togglePacketDrop(false);
            } catch (Throwable ignored) {
                // The process is shutting down or the native library failed.
            }
        }

        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
                // The view may already have been detached by the system.
            }
        }

        overlayView = null;
        windowManager = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
