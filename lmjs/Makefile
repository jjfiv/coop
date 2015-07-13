SRC:=$(wildcard src/*.js)

lm.js: $(SRC)
	babel src --out-file $@ -s

.PHONY: watch serve
watch:
	babel src --watch --out-file lm.js -s

serve:
	python2 -m SimpleHTTPServer

