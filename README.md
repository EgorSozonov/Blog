# Blog
A simple CMS for an online blog

## Instructions

It has two ingestion folders: for core (global) stuff and for documents. Before first run, copy contents of 
the "web" folder from the repo into the Constant.ingestCoreSubfolder, and add the subfolder "images" into it,
with images "favicon.ico" and "404.png".

A document must be an HTML file. In this HTML file, all scripts must be external references of type="module".
These scripts may reference either adjacent files (like <script type="module" src="./module.mjs"/>)
or global scripts (like <script type="module" src="globalModule.mjs"/>).