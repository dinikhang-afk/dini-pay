TARGET := iphone:clang:latest:15.0
ARCHS := arm64 arm64e
INSTALL_TARGET_PROCESSES := Preferences

THEOS_PACKAGE_SCHEME ?= rootless

include $(THEOS)/makefiles/common.mk

TWEAK_NAME = IAPCheck

IAPCheck_FILES = Tweak.x IAPProductModel.m IAPOverlayViewController.m
IAPCheck_CFLAGS = -fobjc-arc -Wno-deprecated-declarations -Wno-incomplete-implementation -Wno-unused-variable
IAPCheck_FRAMEWORKS = UIKit StoreKit Foundation CoreGraphics

include $(THEOS_MAKE_PATH)/tweak.mk
