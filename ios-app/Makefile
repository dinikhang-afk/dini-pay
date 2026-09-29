TARGET := iphone:clang:latest:16.0
ARCHS := arm64 arm64e

THEOS_PACKAGE_SCHEME ?= rootless

include $(THEOS)/makefiles/common.mk

APPLICATION_NAME = DiniPay

DiniPay_FILES = $(wildcard Sources/**/*.swift)
DiniPay_FRAMEWORKS = UIKit SwiftUI Foundation CoreServices
DiniPay_CODESIGN_FLAGS = -Sentitlements.plist


include $(THEOS_MAKE_PATH)/application.mk
