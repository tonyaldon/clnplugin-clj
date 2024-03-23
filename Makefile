.PHONY: pytest cljtest test

CLN_TAG=v23.11

cljtest:
	clojure -X:test

pytest:
	pytest -n 6 pytest

test: cljtest pytest
