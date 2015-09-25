SRC:=$(wildcard src/*.js)

compiled.js: $(SRC)
	babel src --out-file $@ -s

.PHONY: watch serve deps
watch:
	babel src --watch --out-file compiled.js -s

deps:
	sudo npm install babel

