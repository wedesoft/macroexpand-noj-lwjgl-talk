.SUFFIXES: .pdf .tex .svg .png .jpg

SVGS = $(wildcard *.svg)

IMAGES = $(wildcard *.png) $(wildcard *.jpg) $(SVGS:.svg=.pdf)

all: clojure-lwjgl.pdf lroc_color_poles_2k.tif ldem_4.tif

clojure-lwjgl.pdf: clojure-lwjgl.tex $(IMAGES)
	pdflatex -shell-escape $<
	pdflatex -shell-escape $<

lroc_color_poles_2k.tif:
	curl -o $@ https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_2k.tif

ldem_4.tif:
	curl -o $@ https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/ldem_4.tif

clean:
	rm -Rf clojure-lwjgl.pdf _minted-clojure-lwjgl *.aux *.log *.out *.nav *.snm *.toc *.vrb

.svg.pdf:
	rsvg-convert -f pdf -o $@ $<
