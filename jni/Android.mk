LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := getroot
LOCAL_SRC_FILES := \
	getroot.c \
	getaddr.c

include $(BUILD_SHARED_LIBRARY)
