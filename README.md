# Blog
A simple CMS for an online blog

## Instructions

To build, write the production config into config/application.conf (yes, apparently Ktor can't find .conf files at runtime), 
run the "build" task, restore the dev config the .conf file, and then deploy the _bin/distributions/....tar file to the server.

It has two ingestion folders: for core (global) stuff and for documents. Before first run, copy contents of 
the "web" folder from the repo into the Constant.ingestCoreSubfolder, and add the subfolder "images" into it,
with images "favicon.ico" and "404.png".

A document must be an HTML file. In this HTML file, there may be no links to CSS files (all styles must be internal)
and all scripts must be external references of type="module". These scripts may reference either adjacent files 
(like <script type="module" src="./module.js"/>) 
or global script libraries (like <script type="module" src="global/Library.js"/>). 

In a script file, all imports must be at the start of the file, one line per import.

The CMS allows updating of all data with a delay of 5 minutes, and correctly handles the script modules' versioning.