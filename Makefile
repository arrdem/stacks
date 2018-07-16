.PHONY: uberjar
uberjar:
	cp README.md doc/
	lein with-profiles uberjar,dev uberjar
