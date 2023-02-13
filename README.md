# Blog
A simple CMS for an online blog

### Deploying

To build, write the production config into config/application.conf (yes, apparently Ktor can't find .conf files at runtime), 
run the "build" task, restore the dev config in the application.conf file, and then deploy the _bin/distributions/ folder to the server.


### Usage

It has two ingestion folders: _ingestCore for core (global) stuff and _ingest for content. Before first run, copy the contents of 
the "web" folder from the repo into the _ingestCore, and add the subfolder "images" into it,
with an image "404.png" for 404 pages.

A document must be an HTML file with all styles internal in the <head> tag,
and all scripts must be external references of type="module". These references may either be to adjacent files 
(like <script type="module" src="./module.js"/>) 
or global script libraries (like <script type="module" src="Library.js"/>, notice the absence of the dot). 

In a script file, all imports must be at the start of the file, one line per import.

The CMS allows updating of all data with a delay of 5 minutes.