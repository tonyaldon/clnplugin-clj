.PHONY: pytest cljtest test

CLN_TAG=v23.11

cljtest:
	clojure -X:test

pytest:
	pytest -n auto pytest

test: cljtest pytest
