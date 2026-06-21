# Builds the OmniGraph native DSP library.
#
#   make            build the shared library for this platform
#   make clean      remove build artifacts
#
# JAVA_HOME must point at a JDK (it is used to locate <jni.h>).

JAVA_HOME ?= $(shell dirname $(shell dirname $(shell readlink -f $(shell which javac))))

CC      ?= cc
CFLAGS  ?= -O3 -fPIC -Wall -Wextra

UNAME_S := $(shell uname -s)

ifeq ($(UNAME_S),Darwin)
    LIB     := libomnigraph_dsp.dylib
    LDFLAGS := -dynamiclib
    JNI_INC := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/darwin
else
    LIB     := libomnigraph_dsp.so
    LDFLAGS := -shared
    JNI_INC := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
endif

all: $(LIB)

$(LIB): omnigraph_dsp.c
	$(CC) $(CFLAGS) $(JNI_INC) $(LDFLAGS) -o $@ $< -lm

clean:
	rm -f libomnigraph_dsp.so libomnigraph_dsp.dylib

.PHONY: all clean
