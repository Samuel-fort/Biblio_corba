# Java 8 requis (CORBA et idlj ont été retirés du JDK à partir de Java 11).
# Utilise le JDK 8 s'il est installé à l'emplacement Debian/Ubuntu habituel,
# sinon le java/javac du PATH (ou : make JDK=/chemin/vers/jdk8)
JDK ?= $(firstword $(wildcard /usr/lib/jvm/java-8-openjdk-*))
ifneq ($(JDK),)
BIN = $(JDK)/bin/
endif
JAVAC = $(BIN)javac -encoding UTF-8
JAVA  = $(BIN)java -Dfile.encoding=UTF-8
IDLJ  = $(BIN)idlj

IDL  = ../bibliotheque.idl
SRC  = $(wildcard *.java)
NS   = -ORBInitRef NameService=corbaloc::localhost:2809/NameService

all: classes/.built

gen/.stamp: $(IDL)
	mkdir -p gen
	$(IDLJ) -fclient -td gen $(IDL)
	touch $@

classes/.built: gen/.stamp $(SRC)
	mkdir -p classes
	$(JAVAC) -d classes gen/Biblio/*.java $(SRC)
	touch $@

run: all
	$(JAVA) -cp classes Client $(NS)

test: all
	$(JAVA) -cp classes ConsoleTest $(NS)

clean:
	rm -rf gen classes

.PHONY: all run test clean
