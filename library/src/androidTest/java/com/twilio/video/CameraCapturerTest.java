/*
 * Copyright (C) 2017 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video;

import static com.twilio.video.AspectRatio.ASPECT_RATIO_16_9;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.hardware.Camera;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.twilio.video.base.BaseCameraCapturerTest;
import com.twilio.video.util.DeviceUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CameraCapturerTest extends BaseCameraCapturerTest {
    /*
     * The camera freeze timeout in WebRTC is 4000 MS. Added 500 MS buffer to prevent
     * false failures.
     */
    private static final int CAMERA_FREEZE_TIMEOUT_MS = 4500;

    @After
    public void teardown() {
        super.teardown();
        assertTrue(MediaFactory.isReleased());
    }

    @Test
    public void shouldAllowSourceSupportedCheck() {
        int sourcesSupported = 0;
        boolean frontCameraSupported =
                CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA);
        boolean backCameraSupported =
                CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.BACK_CAMERA);

        // Update supported sources
        sourcesSupported = frontCameraSupported ? (sourcesSupported + 1) : (sourcesSupported);
        sourcesSupported = backCameraSupported ? (sourcesSupported + 1) : (sourcesSupported);

        assertEquals(Camera.getNumberOfCameras(), sourcesSupported);
    }

    @Test
    public void shouldAllowNullListener() {
        cameraCapturer = new CameraCapturer(cameraCapturerActivity, supportedCameraSource, null);
    }

    @Test
    public void shouldAllowSubsequentInstances() throws InterruptedException {
        int numInstances = 4;
        final CountDownLatch completed = new CountDownLatch(numInstances);
        for (int i = 0; i < numInstances; i++) {
            final CountDownLatch firstFrameReceived = new CountDownLatch(1);
            CameraCapturer cameraCapturer =
                    new CameraCapturer(
                            cameraCapturerActivity,
                            supportedCameraSource,
                            new CameraCapturer.Listener() {
                                @Override
                                public void onFirstFrameAvailable() {
                                    firstFrameReceived.countDown();
                                }

                                @Override
                                public void onCameraSwitched() {}

                                @Override
                                public void onError(@CameraCapturer.Error int errorCode) {}
                            });
            LocalVideoTrack localVideoTrack =
                    LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

            // Validate we got our first frame
            assertTrue(firstFrameReceived.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

            localVideoTrack.release();
            completed.countDown();
        }
        assertTrue(completed.await(20, TimeUnit.SECONDS));
    }

    @Test
    public void shouldAllowSettingFpsVideoConstraints() throws InterruptedException {
        final CountDownLatch firstFrameReceived = new CountDownLatch(1);
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        supportedCameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameReceived.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        VideoConstraints videoConstraints =
                new VideoConstraints.Builder().minFps(5).maxFps(15).build();
        localVideoTrack =
                LocalVideoTrack.create(
                        cameraCapturerActivity, true, cameraCapturer, videoConstraints);

        // Validate we got our first frame
        assertTrue(firstFrameReceived.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Validate our constraints are applied
        assertEquals(videoConstraints, localVideoTrack.getVideoConstraints());
    }

    @Test
    public void cameraShouldBeAvailableAfterVideoTrackReleased() throws InterruptedException {
        final CountDownLatch firstFrameReceived = new CountDownLatch(1);
        final CameraCapturerFormatProvider formatProvider = new CameraCapturerFormatProvider();
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        supportedCameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameReceived.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        },
                        formatProvider);
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Validate we got our first frame
        assertTrue(firstFrameReceived.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Release track
        localVideoTrack.release();

        // Now validate that the camera service is available for the same camera ID
        int cameraId = formatProvider.getCameraId(supportedCameraSource);
        Camera camera = null;
        try {
            camera = Camera.open(cameraId);
            assertNotNull(camera);
        } catch (RuntimeException e) {
            fail("Failed to connect to camera service after video track was released");
        } finally {
            if (camera != null) {
                camera.release();
            }
        }
    }

    @Test
    public void shouldCaptureFramesAfterCameraSwitchAndRelease() throws InterruptedException {
        assumeTrue(bothCameraSourcesAvailable());
        final CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.FRONT_CAMERA;
        final AtomicReference<CountDownLatch> firstFrameReceived =
                new AtomicReference<>(new CountDownLatch(1));
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameReceived.get().countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Validate we got our first frame
        assertTrue(firstFrameReceived.get().await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Switch camera
        cameraCapturer.switchCamera();

        // Release track
        localVideoTrack.release();

        // Create new camera capturer with front camera
        firstFrameReceived.set(new CountDownLatch(1));
        CameraCapturer newFrontCameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameReceived.get().countDown();
                            }

                            @Override
                            public void onCameraSwitched() {
                                fail();
                            }

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {
                                fail();
                            }
                        });
        localVideoTrack =
                LocalVideoTrack.create(cameraCapturerActivity, true, newFrontCameraCapturer);

        // Validate we got our first frame
        assertTrue(firstFrameReceived.get().await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Release track
        localVideoTrack.release();
    }

    @Test
    public void shouldCreateLocalVideoTrackIfVideoConstraintsCompatible() {
        cameraCapturer = new CameraCapturer(cameraCapturerActivity, supportedCameraSource, null);

        VideoConstraints videoConstraints =
                new VideoConstraints.Builder()
                        .minFps(0)
                        .maxFps(30)
                        .minVideoDimensions(VideoDimensions.WVGA_VIDEO_DIMENSIONS)
                        .maxVideoDimensions(VideoDimensions.HD_540P_VIDEO_DIMENSIONS)
                        .aspectRatio(ASPECT_RATIO_16_9)
                        .build();

        assumeTrue(LocalVideoTrack.constraintsCompatible(cameraCapturer, videoConstraints));

        localVideoTrack =
                LocalVideoTrack.create(
                        cameraCapturerActivity, true, cameraCapturer, videoConstraints);
        assertNotNull(localVideoTrack);
        localVideoTrack.release();
    }

    @Test
    public void shouldAllowCameraSwitch() throws InterruptedException {
        assumeTrue(bothCameraSourcesAvailable());
        final CountDownLatch cameraSwitched = new CountDownLatch(1);
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        CameraCapturer.CameraSource.FRONT_CAMERA,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {}

                            @Override
                            public void onCameraSwitched() {
                                cameraSwitched.countDown();
                            }

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Add renderer
        localVideoTrack.addRenderer(frameCountRenderer);

        // Validate we get a frame
        assertTrue(frameCountRenderer.waitForFrame(CAMERA_CAPTURE_DELAY_MS));

        // Validate front camera source
        assertEquals(CameraCapturer.CameraSource.FRONT_CAMERA, cameraCapturer.getCameraSource());

        // Perform camera switch
        cameraCapturer.switchCamera();

        // Validate our switch happened
        assertTrue(cameraSwitched.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Validate we get a frame after camera switch
        assertTrue(frameCountRenderer.waitForFrame(CAMERA_CAPTURE_DELAY_MS));

        // Validate back camera source
        assertEquals(CameraCapturer.CameraSource.BACK_CAMERA, cameraCapturer.getCameraSource());
    }

    @Test
    public void shouldAllowCameraSwitchWhileNotOnLocalVideo() throws InterruptedException {
        assumeTrue(bothCameraSourcesAvailable());
        final CountDownLatch cameraSwitched = new CountDownLatch(1);
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        CameraCapturer.CameraSource.FRONT_CAMERA,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {}

                            @Override
                            public void onCameraSwitched() {
                                cameraSwitched.countDown();
                            }

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });

        // Switch our camera
        cameraCapturer.switchCamera();

        // Now add our video track
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Validate our switch happened
        assertTrue(cameraSwitched.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Add renderer
        localVideoTrack.addRenderer(frameCountRenderer);

        // Validate we get a frame
        assertTrue(frameCountRenderer.waitForFrame(CAMERA_CAPTURE_DELAY_MS));

        // Validate we are on back camera source
        assertEquals(CameraCapturer.CameraSource.BACK_CAMERA, cameraCapturer.getCameraSource());
    }

    @Test
    public void switchCamera_shouldFailWithSwitchPending() throws InterruptedException {
        assumeTrue(bothCameraSourcesAvailable());
        final CountDownLatch cameraSwitchError = new CountDownLatch(1);
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        CameraCapturer.CameraSource.FRONT_CAMERA,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {}

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {
                                assertEquals(CameraCapturer.ERROR_CAMERA_SWITCH_FAILED, errorCode);
                                cameraSwitchError.countDown();
                            }
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Switch our cameras quickly
        cameraCapturer.switchCamera();
        cameraCapturer.switchCamera();

        // Wait for callback
        assertTrue(cameraSwitchError.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldAllowUpdatingCameraParametersBeforeCapturing() throws InterruptedException {
        // Back camera used for tests that require changes to camera parameters
        CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.BACK_CAMERA;
        assumeTrue(CameraCapturer.isSourceAvailable(cameraSource));

        // TODO: Fix issue setting parameters on Nexus 7 GSDK-1281
        assumeFalse(DeviceUtils.isNexus7());

        CountDownLatch cameraParametersSet = new CountDownLatch(1);
        String expectedFlashMode = Camera.Parameters.FLASH_MODE_TORCH;
        AtomicReference<Camera.Parameters> actualCameraParameters = new AtomicReference<>();
        cameraCapturer = new CameraCapturer(cameraCapturerActivity, cameraSource);

        // Set our camera parameters
        scheduleCameraParameterFlashModeUpdate(
                cameraParametersSet, expectedFlashMode, actualCameraParameters);

        // Now add our video track
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for parameters to be set
        assertTrue(cameraParametersSet.await(10, TimeUnit.SECONDS));

        // Assume that flash is supported
        assumeNotNull(actualCameraParameters.get().getFlashMode());

        // Validate our flash mode
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());
    }

    @Test
    public void shouldAllowUpdatingCameraParametersWhileCapturing() throws InterruptedException {
        // Back camera used for tests that require changes to camera parameters
        CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.BACK_CAMERA;
        assumeTrue(CameraCapturer.isSourceAvailable(cameraSource));

        // TODO: Fix issue setting parameters on Nexus 7 GSDK-1281
        assumeFalse(DeviceUtils.isNexus7());

        CountDownLatch cameraParametersUpdated = new CountDownLatch(1);
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        String expectedFlashMode = Camera.Parameters.FLASH_MODE_TORCH;
        AtomicReference<Camera.Parameters> actualCameraParameters = new AtomicReference<>();
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });

        // Begin capturing
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for first frame
        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Schedule camera parameter update
        scheduleCameraParameterFlashModeUpdate(
                cameraParametersUpdated, expectedFlashMode, actualCameraParameters);

        // Wait for parameters to be set
        assertTrue(cameraParametersUpdated.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Assume that flash is supported
        assumeNotNull(actualCameraParameters.get().getFlashMode());

        // Validate our flash mode
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());
    }

    @Test
    public void updateCameraParameters_shouldNotCauseCameraFreeze() throws InterruptedException {
        // Back camera used for tests that require changes to camera parameters
        CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.BACK_CAMERA;
        assumeTrue(CameraCapturer.isSourceAvailable(cameraSource));

        // TODO: Fix issue setting parameters on Nexus 7 GSDK-1281
        assumeFalse(DeviceUtils.isNexus7());

        CountDownLatch cameraParametersSet = new CountDownLatch(1);
        final CountDownLatch cameraFroze = new CountDownLatch(1);
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        String expectedFlashMode = Camera.Parameters.FLASH_MODE_TORCH;
        AtomicReference<Camera.Parameters> actualCameraParameters = new AtomicReference<>();
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {
                                if (errorCode == CameraCapturer.ERROR_CAMERA_FREEZE) {
                                    cameraFroze.countDown();
                                }
                            }
                        });

        // Begin capturing
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for first frame
        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Schedule camera parameter update
        scheduleCameraParameterFlashModeUpdate(
                cameraParametersSet, expectedFlashMode, actualCameraParameters);

        // Wait for parameters to be set
        assertTrue(cameraParametersSet.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Assume that flash is supported
        assumeNotNull(actualCameraParameters.get().getFlashMode());

        // Validate our flash mode
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());

        // Validate we do not get a camera freeze
        assertFalse(cameraFroze.await(CAMERA_FREEZE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void updateCameraParameters_shouldManifestAfterCaptureCycle()
            throws InterruptedException {
        // Back camera used for tests that require changes to camera parameters
        CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.BACK_CAMERA;
        assumeTrue(CameraCapturer.isSourceAvailable(cameraSource));

        // TODO: Fix issue setting parameters on Nexus 7 GSDK-1281
        assumeFalse(DeviceUtils.isNexus7());

        CountDownLatch cameraParametersUpdated = new CountDownLatch(1);
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        String expectedFlashMode = Camera.Parameters.FLASH_MODE_TORCH;
        AtomicReference<Camera.Parameters> actualCameraParameters = new AtomicReference<>();
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });

        // Begin capturing and validate our flash mode is set
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for first frame
        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        scheduleCameraParameterFlashModeUpdate(
                cameraParametersUpdated, expectedFlashMode, actualCameraParameters);

        // Wait for parameters to be set
        assertTrue(cameraParametersUpdated.await(10, TimeUnit.SECONDS));

        // Assume that flash is supported
        assumeNotNull(actualCameraParameters.get().getFlashMode());

        // Validate our flash mode
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());

        // Release the video track
        localVideoTrack.release();

        // Set our flash mode to something else
        cameraParametersUpdated = new CountDownLatch(1);
        expectedFlashMode = Camera.Parameters.FLASH_MODE_ON;
        scheduleCameraParameterFlashModeUpdate(
                cameraParametersUpdated, expectedFlashMode, actualCameraParameters);

        // Recreate track
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for parameters to be set
        assertTrue(cameraParametersUpdated.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Validate our flash mode is actually different
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());
    }

    @Test
    public void updateCameraParameters_shouldReturnFalseIfUpdateIsPending()
            throws InterruptedException {
        // Back camera used for tests that require changes to camera parameters
        CameraCapturer.CameraSource cameraSource = CameraCapturer.CameraSource.BACK_CAMERA;
        assumeTrue(CameraCapturer.isSourceAvailable(cameraSource));

        // TODO: Fix issue setting parameters on Nexus 7 GSDK-1281
        assumeFalse(DeviceUtils.isNexus7());

        CountDownLatch cameraParametersUpdated = new CountDownLatch(1);
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        String expectedFlashMode = Camera.Parameters.FLASH_MODE_TORCH;
        AtomicReference<Camera.Parameters> actualCameraParameters = new AtomicReference<>();
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        cameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for first frame to be available
        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        // Schedule our camera parameter update
        scheduleCameraParameterFlashModeUpdate(
                cameraParametersUpdated, expectedFlashMode, actualCameraParameters);

        /*
         * Not every parameter update fails so we string together a few calls and ensure that
         * one of them fails to validate the scenario
         */
        boolean parameterUpdateScheduled = true;
        final int updateIterations = 4;
        for (int i = 0; i < updateIterations; i++) {
            parameterUpdateScheduled &=
                    cameraCapturer.updateCameraParameters(cameraParameters -> {});
        }

        // With update pending this should have failed
        assertFalse(parameterUpdateScheduled);

        // Wait for original parameters to be set
        assertTrue(cameraParametersUpdated.await(10, TimeUnit.SECONDS));

        // Assume that flash is supported
        assumeNotNull(actualCameraParameters.get().getFlashMode());

        // Validate our flash mode
        assertEquals(expectedFlashMode, actualCameraParameters.get().getFlashMode());
    }

    @Test
    public void takePicture_shouldFailWithPicturePending() throws InterruptedException {
        final CountDownLatch onPictureTakenLatch = new CountDownLatch(1);
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        supportedCameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);
        CameraCapturer.PictureListener pictureListener =
                new CameraCapturer.PictureListener() {
                    @Override
                    public void onShutter() {}

                    @Override
                    public void onPictureTaken(byte[] pictureData) {
                        onPictureTakenLatch.countDown();
                    }
                };

        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));
        assertTrue(cameraCapturer.takePicture(pictureListener));
        assertFalse(cameraCapturer.takePicture(pictureListener));
        assertTrue(onPictureTakenLatch.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shouldInvokePictureListenerOnCallingThread() throws InterruptedException {
        final CountDownLatch firstFrameAvailable = new CountDownLatch(1);
        final CountDownLatch shutterCallback = new CountDownLatch(1);
        final CountDownLatch pictureTaken = new CountDownLatch(1);

        cameraCapturer =
                new CameraCapturer(
                        cameraCapturerActivity,
                        supportedCameraSource,
                        new CameraCapturer.Listener() {
                            @Override
                            public void onFirstFrameAvailable() {
                                firstFrameAvailable.countDown();
                            }

                            @Override
                            public void onCameraSwitched() {}

                            @Override
                            public void onError(@CameraCapturer.Error int errorCode) {}
                        });
        localVideoTrack = LocalVideoTrack.create(cameraCapturerActivity, true, cameraCapturer);

        // Wait for capturer to actually start
        assertTrue(firstFrameAvailable.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));

        /*
         * Run on UI thread to avoid thread hopping between the test runner thread and the UI
         * thread.
         */
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            final long callingThreadId = Thread.currentThread().getId();
                            CameraCapturer.PictureListener pictureListener =
                                    new CameraCapturer.PictureListener() {
                                        @Override
                                        public void onShutter() {
                                            assertEquals(
                                                    callingThreadId,
                                                    Thread.currentThread().getId());
                                            shutterCallback.countDown();
                                        }

                                        @Override
                                        public void onPictureTaken(byte[] pictureData) {
                                            assertEquals(
                                                    callingThreadId,
                                                    Thread.currentThread().getId());
                                            pictureTaken.countDown();
                                        }
                                    };
                            assertTrue(cameraCapturer.takePicture(pictureListener));
                        });

        assertTrue(shutterCallback.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));
        assertTrue(pictureTaken.await(CAMERA_CAPTURE_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private void scheduleCameraParameterFlashModeUpdate(
            final CountDownLatch cameraParametersSet,
            final String expectedFlashMode,
            final AtomicReference<Camera.Parameters> actualCameraParameters) {
        boolean parameterUpdateScheduled =
                cameraCapturer.updateCameraParameters(
                        cameraParameters -> {
                            // Turn the flash only if supported
                            if (cameraParameters.getFlashMode() != null) {
                                cameraParameters.setFlashMode(expectedFlashMode);
                            }
                            actualCameraParameters.set(cameraParameters);

                            // Continue test
                            cameraParametersSet.countDown();
                        });

        assertTrue(parameterUpdateScheduled);
    }
}
