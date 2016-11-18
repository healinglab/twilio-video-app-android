#ifndef VIDEO_ANDROID_ANDROID_VIDEO_CAPTURER_JNI_H_
#define VIDEO_ANDROID_ANDROID_VIDEO_CAPTURER_JNI_H_

#include <memory>
#include <string>

#include "android_video_capturer.h"
#include "webrtc/api/android/jni/jni_helpers.h"
#include "webrtc/base/asyncinvoker.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/thread_checker.h"
#include "webrtc/common_video/include/i420_buffer_pool.h"
#include "webrtc/api/android/jni/surfacetexturehelper_jni.h"

namespace twilio_video_jni {

// AndroidVideoCapturerJni implements AndroidVideoCapturerDelegate.
// The purpose of the delegate is to hide the JNI specifics from the C++ only
// AndroidVideoCapturer.
class AndroidVideoCapturerJni : public AndroidVideoCapturerDelegate {
public:
    static int SetAndroidObjects(JNIEnv *jni, jobject appliction_context);

    AndroidVideoCapturerJni(JNIEnv *jni,
                            jobject j_video_capturer,
                            jobject j_egl_context,
                            jboolean is_screencast);

    void Start(const cricket::VideoFormat& capture_format, AndroidVideoCapturer *capturer) override;

    void Stop() override;

    std::vector<cricket::VideoFormat> GetSupportedFormats() override;
    bool IsScreencast() override;

    // Called from VideoCapturer::NativeObserver on a Java thread.
    void OnCapturerStarted(bool success);

    virtual void OnMemoryBufferFrame(void *video_frame, int length, int width,
                             int height, int rotation, int64_t timestamp_ns);

    void OnTextureFrame(int width, int height, int rotation, int64_t timestamp_ns,
                        const webrtc_jni::NativeHandleImpl &handle);

    void OnOutputFormatRequest(int width, int height, int fps);

    ~AndroidVideoCapturerJni();

    JNIEnv *jni();

    // To avoid deducing Args from the 3rd parameter of AsyncCapturerInvoke.
    template<typename T>
    struct Identity {
        typedef T type;
    };

    // Helper function to make safe asynchronous calls to |capturer_|. The calls
    // are not guaranteed to be delivered.
    template<typename... Args>
    void AsyncCapturerInvoke(
            const rtc::Location &posted_from,
            void (AndroidVideoCapturer::*method)(Args...),
            typename Identity<Args>::type... args);

    const webrtc_jni::ScopedGlobalRef<jobject> j_video_capturer_;
    const webrtc_jni::ScopedGlobalRef<jclass> j_video_capturer_class_;
    const webrtc_jni::ScopedGlobalRef<jclass> j_observer_class_;
    const bool is_screencast_;

    // Used on the Java thread running the camera.
    webrtc::I420BufferPool pre_scale_pool_;
    webrtc::I420BufferPool post_scale_pool_;
    rtc::scoped_refptr<webrtc_jni::SurfaceTextureHelper> surface_texture_helper_;
    rtc::ThreadChecker thread_checker_;

    // |capturer| is a guaranteed to be a valid pointer between a call to
    // AndroidVideoCapturerDelegate::Start
    // until AndroidVideoCapturerDelegate::Stop.
    rtc::CriticalSection capturer_lock_;
    AndroidVideoCapturer *capturer_ GUARDED_BY(capturer_lock_);
    // |invoker_| is used to communicate with |capturer_| on the thread Start() is
    // called on.
    std::unique_ptr<rtc::GuardedAsyncInvoker> invoker_ GUARDED_BY(capturer_lock_);

    static jobject application_context_;

    RTC_DISALLOW_COPY_AND_ASSIGN(AndroidVideoCapturerJni);
};
}

#endif // VIDEO_ANDROID_ANDROID_VIDEO_CAPTURER_JNI_H_