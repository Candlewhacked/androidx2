/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.extensions.internal;

import android.graphics.Rect;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureResult;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.Logger;
import androidx.camera.core.impl.CameraCaptureMetaData;
import androidx.camera.core.impl.CameraCaptureResult;
import androidx.camera.core.impl.TagBundle;
import androidx.camera.core.impl.utils.ExifData;

import java.nio.BufferUnderflowException;

/**
 * The camera2 implementation for the capture result of a single image capture.
 *
 * <p>Copied from camera-camera2 since we don't want the camera-camera2 dependency but we need to
 * generate the {@link CameraCaptureResult} from a
 * {@link android.hardware.camera2.CaptureResult} instance.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public class Camera2CameraCaptureResult implements CameraCaptureResult {
    private static final String TAG = "C2CameraCaptureResult";

    private final TagBundle mTagBundle;

    /** The actual camera2 {@link CaptureResult}. */
    private final CaptureResult mCaptureResult;

    public Camera2CameraCaptureResult(@NonNull TagBundle tagBundle,
            @NonNull CaptureResult captureResult) {
        mTagBundle = tagBundle;
        mCaptureResult = captureResult;
    }

    public Camera2CameraCaptureResult(@NonNull CaptureResult captureResult) {
        this(TagBundle.emptyBundle(), captureResult);
    }

    /**
     * Converts the camera2 {@link CaptureResult#CONTROL_AF_MODE} to
     * {@link CameraCaptureMetaData.AfMode}.
     *
     * @return the {@link CameraCaptureMetaData.AfMode}.
     */
    @NonNull
    @Override
    public CameraCaptureMetaData.AfMode getAfMode() {
        Integer mode = mCaptureResult.get(CaptureResult.CONTROL_AF_MODE);
        if (mode == null) {
            return CameraCaptureMetaData.AfMode.UNKNOWN;
        }
        switch (mode) {
            case CaptureResult.CONTROL_AF_MODE_OFF:
            case CaptureResult.CONTROL_AF_MODE_EDOF:
                return CameraCaptureMetaData.AfMode.OFF;
            case CaptureResult.CONTROL_AF_MODE_AUTO:
            case CaptureResult.CONTROL_AF_MODE_MACRO:
                return CameraCaptureMetaData.AfMode.ON_MANUAL_AUTO;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                return CameraCaptureMetaData.AfMode.ON_CONTINUOUS_AUTO;
            default: // fall out
        }
        Logger.e(TAG, "Undefined af mode: " + mode);
        return CameraCaptureMetaData.AfMode.UNKNOWN;
    }

    /**
     * Converts the camera2 {@link CaptureResult#CONTROL_AF_STATE} to
     * {@link CameraCaptureMetaData.AfState}.
     *
     * @return the {@link CameraCaptureMetaData.AfState}.
     */
    @NonNull
    @Override
    public CameraCaptureMetaData.AfState getAfState() {
        Integer state = mCaptureResult.get(CaptureResult.CONTROL_AF_STATE);
        if (state == null) {
            return CameraCaptureMetaData.AfState.UNKNOWN;
        }
        switch (state) {
            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                return CameraCaptureMetaData.AfState.INACTIVE;
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                return CameraCaptureMetaData.AfState.SCANNING;
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                return CameraCaptureMetaData.AfState.LOCKED_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                return CameraCaptureMetaData.AfState.LOCKED_NOT_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                return CameraCaptureMetaData.AfState.PASSIVE_NOT_FOCUSED;
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                return CameraCaptureMetaData.AfState.PASSIVE_FOCUSED;

            default: // fall out
        }
        Logger.e(TAG, "Undefined af state: " + state);
        return CameraCaptureMetaData.AfState.UNKNOWN;
    }

    /**
     * Converts the camera2 {@link CaptureResult#CONTROL_AE_STATE} to
     * {@link CameraCaptureMetaData.AeState}.
     *
     * @return the {@link CameraCaptureMetaData.AeState}.
     */
    @NonNull
    @Override
    public CameraCaptureMetaData.AeState getAeState() {
        Integer state = mCaptureResult.get(CaptureResult.CONTROL_AE_STATE);
        if (state == null) {
            return CameraCaptureMetaData.AeState.UNKNOWN;
        }
        switch (state) {
            case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                return CameraCaptureMetaData.AeState.INACTIVE;
            case CaptureResult.CONTROL_AE_STATE_SEARCHING:
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                return CameraCaptureMetaData.AeState.SEARCHING;
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                return CameraCaptureMetaData.AeState.FLASH_REQUIRED;
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                return CameraCaptureMetaData.AeState.CONVERGED;
            case CaptureResult.CONTROL_AE_STATE_LOCKED:
                return CameraCaptureMetaData.AeState.LOCKED;
            default: // fall out
        }
        Logger.e(TAG, "Undefined ae state: " + state);
        return CameraCaptureMetaData.AeState.UNKNOWN;
    }

    /**
     * Converts the camera2 {@link CaptureResult#CONTROL_AWB_STATE} to
     * {@link CameraCaptureMetaData.AwbState}.
     *
     * @return the {@link CameraCaptureMetaData.AwbState}.
     */
    @NonNull
    @Override
    public CameraCaptureMetaData.AwbState getAwbState() {
        Integer state = mCaptureResult.get(CaptureResult.CONTROL_AWB_STATE);
        if (state == null) {
            return CameraCaptureMetaData.AwbState.UNKNOWN;
        }
        switch (state) {
            case CaptureResult.CONTROL_AWB_STATE_INACTIVE:
                return CameraCaptureMetaData.AwbState.INACTIVE;
            case CaptureResult.CONTROL_AWB_STATE_SEARCHING:
                return CameraCaptureMetaData.AwbState.METERING;
            case CaptureResult.CONTROL_AWB_STATE_CONVERGED:
                return CameraCaptureMetaData.AwbState.CONVERGED;
            case CaptureResult.CONTROL_AWB_STATE_LOCKED:
                return CameraCaptureMetaData.AwbState.LOCKED;
            default: // fall out
        }
        Logger.e(TAG, "Undefined awb state: " + state);
        return CameraCaptureMetaData.AwbState.UNKNOWN;
    }

    /**
     * Converts the camera2 {@link CaptureResult#FLASH_STATE} to
     * {@link CameraCaptureMetaData.FlashState}.
     *
     * @return the {@link CameraCaptureMetaData.FlashState}.
     */
    @NonNull
    @Override
    public CameraCaptureMetaData.FlashState getFlashState() {
        Integer state = mCaptureResult.get(CaptureResult.FLASH_STATE);
        if (state == null) {
            return CameraCaptureMetaData.FlashState.UNKNOWN;
        }
        switch (state) {
            case CaptureResult.FLASH_STATE_UNAVAILABLE:
            case CaptureResult.FLASH_STATE_CHARGING:
                return CameraCaptureMetaData.FlashState.NONE;
            case CaptureResult.FLASH_STATE_READY:
                return CameraCaptureMetaData.FlashState.READY;
            case CaptureResult.FLASH_STATE_FIRED:
            case CaptureResult.FLASH_STATE_PARTIAL:
                return CameraCaptureMetaData.FlashState.FIRED;
            default: // fall out
        }
        Logger.e(TAG, "Undefined flash state: " + state);
        return CameraCaptureMetaData.FlashState.UNKNOWN;
    }

    /** {@inheritDoc} */
    @Override
    public long getTimestamp() {
        Long timestamp = mCaptureResult.get(CaptureResult.SENSOR_TIMESTAMP);
        if (timestamp == null) {
            return -1L;
        }

        return timestamp;
    }

    @NonNull
    @Override
    public TagBundle getTagBundle() {
        return mTagBundle;
    }

    @Override
    public void populateExifData(@NonNull ExifData.Builder exifData) {
        // Call interface default to set flash mode
        CameraCaptureResult.super.populateExifData(exifData);

        // Set dimensions
        Rect cropRegion = mCaptureResult.get(CaptureResult.SCALER_CROP_REGION);
        if (cropRegion != null) {
            exifData.setImageWidth(cropRegion.width())
                    .setImageHeight(cropRegion.height());
        }

        // Set orientation
        try {
            Integer jpegOrientation = mCaptureResult.get(CaptureResult.JPEG_ORIENTATION);
            if (jpegOrientation != null) {
                exifData.setOrientationDegrees(jpegOrientation);
            }
        } catch (BufferUnderflowException exception) {
            // On certain devices, e.g. Pixel 3 XL API 31, getting JPEG orientation on YUV stream
            // throws BufferUnderflowException. The value will be overridden in post-processing
            // anyway, so it's safe to ignore.
            Logger.w(TAG, "Failed to get JPEG orientation.");
        }

        // Set exposure time
        Long exposureTimeNs = mCaptureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposureTimeNs != null) {
            exifData.setExposureTimeNanos(exposureTimeNs);
        }

        // Set the aperture
        Float aperture = mCaptureResult.get(CaptureResult.LENS_APERTURE);
        if (aperture != null) {
            exifData.setLensFNumber(aperture);
        }

        // Set the ISO
        Integer iso = mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY);
        if (iso != null) {
            if (Build.VERSION.SDK_INT >= 24) {
                Integer postRawSensitivityBoost =
                        mCaptureResult.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
                if (postRawSensitivityBoost != null) {
                    iso *= (int) (postRawSensitivityBoost / 100f);
                }
            }
            exifData.setIso(iso);
        }

        // Set the focal length
        Float focalLength = mCaptureResult.get(CaptureResult.LENS_FOCAL_LENGTH);
        if (focalLength != null) {
            exifData.setFocalLength(focalLength);
        }

        // Set white balance MANUAL/AUTO
        Integer whiteBalanceMode = mCaptureResult.get(CaptureResult.CONTROL_AWB_MODE);
        if (whiteBalanceMode != null) {
            ExifData.WhiteBalanceMode wbMode = ExifData.WhiteBalanceMode.AUTO;
            if (whiteBalanceMode == CameraMetadata.CONTROL_AWB_MODE_OFF) {
                wbMode = ExifData.WhiteBalanceMode.MANUAL;
            }
            exifData.setWhiteBalanceMode(wbMode);
        }
    }

    @NonNull
    @Override
    public CaptureResult getCaptureResult() {
        return mCaptureResult;
    }
}