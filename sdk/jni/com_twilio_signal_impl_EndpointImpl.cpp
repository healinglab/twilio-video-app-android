/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_twilio_signal_impl_EndpointImpl */

#include "com_twilio_signal_impl_EndpointImpl.h"
#include "TSCoreSDKTypes.h"
#include "TSCEndpoint.h"
#include "TSCSession.h"

using namespace twiliosdk;
/*
 * Class:     com_twilio_signal_impl_EndpointImpl
 * Method:    listen
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_twilio_signal_impl_EndpointImpl_listen
  (JNIEnv *env, jobject obj, jlong nativeEndpoint) {

	reinterpret_cast<TSCEndpoint*>(nativeEndpoint)->registerEndpoint();
}

/*
 * Class:     com_twilio_signal_impl_EndpointImpl
 * Method:    unlisten
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_twilio_signal_impl_EndpointImpl_unlisten
  (JNIEnv *env, jobject obj, jlong nativeEndpoint) {

	reinterpret_cast<TSCEndpoint*>(nativeEndpoint)->unregisterEndpoint();
}


/*
 * Class:     com_twilio_signal_impl_EndpointImpl
 * Method:    reject
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_com_twilio_signal_impl_EndpointImpl_reject
  (JNIEnv *env, jobject obj, jlong nativeEndpoint, jlong nativeSession) {

	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	reinterpret_cast<TSCEndpoint*>(nativeEndpoint)->reject(session);
}

/*
 * Class:     com_twilio_signal_impl_EndpointImpl
 * Method:    freeNativeHandle
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_twilio_signal_impl_EndpointImpl_freeNativeHandle
  (JNIEnv *env, jobject obj, jlong nativeEndpoint) {
	TSCEndpoint* endpoint = reinterpret_cast<TSCEndpoint*>(nativeEndpoint);
	if (endpoint != NULL) {
		delete endpoint;
		endpoint = NULL;
	}

}
