.SUFFIXES: .pdf .tex .svg .png .jpg

SVGS = $(wildcard *.svg)

IMAGES = $(wildcard *.png) $(wildcard *.jpg) $(SVGS:.svg=.pdf)

all: clojure-lwjgl.pdf

clojure-lwjgl.pdf: clojure-lwjgl.tex $(IMAGES)
	pdflatex -shell-escape $<
	pdflatex -shell-escape $<

clean:
	rm -Rf clojure-lwjgl.pdf _minted-clojure-lwjgl *.aux *.log *.out *.nav *.snm *.toc *.vrb

.svg.pdf:
	rsvg-convert -f pdf -o $@ $<
