#!/bin/bash
JFX=$(cat /tmp/jfx.txt); CP="/tmp/fx/out:/tmp/fc2/classes:/tmp/fc2/run:$JFX/tmp/sqlite-jdbc-3.46.1.3.jar:/usr/share/java/slf4j-api.jar:/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar"
cd "${ROOT:-/home/claude/p14/Lunavera Coffee}"
timeout 90 xvfb-run -a java -Xss16m -Djava.library.path=/usr/lib/x86_64-linux-gnu/jni -Dprism.order=sw $EXTRA -cp "$CP" com.coffeeshop.coffeeshopmanagement.controller.TryDialogs "$1" 2>&1 | grep -a "db connections\|UNCAUGHT\|THREW\|Exception" | head -4
