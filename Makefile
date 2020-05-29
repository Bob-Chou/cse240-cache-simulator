JC = javac
SRC = src/
CLASS_PATH = out/production/cse240-cache-simulator
JFLAGS = -g -d $(CLASS_PATH) -cp $(CLASS_PATH) -sourcepath $(CLASS_PATH) -encoding UTF-8

vpath %.class $(CLASS_PATH)
vpath %.java $(SRC)

TARGET = \
	com/cse240/hierarchy/Cache.java \
	com/cse240/Main.java

all: $(TARGET:.java=.class)

%.class: %.java $(CLASS_PATH)
	$(JC) $(JFLAGS) $<

$(CLASS_PATH):
	mkdir -p $(CLASS_PATH)

.PHONY: clean
clean:
		rm -rf $(CLASS_PATH)