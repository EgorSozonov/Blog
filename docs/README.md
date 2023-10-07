# Blog
A simple CMS for an online blog

### Deploying

To run the app, pass 3 env variables to Node:
1. STATIC_DIR = the folder where all the content will be served from, like "/var/www/blst/"
1. INGEST_DIR = the dir fresh content is ingested from, like "/var/www/blogIngest/"
2. PORT = port number to run on (10100 by default)

run "make build" task, then deploy the _bin/dist/ folder to the server. Run it with

   STATIC_DIR=/var/www/blst/ INGEST_DIR=... PORT=1234 node blog.js

Example Nginx config excerpt to set up the app:

   location /blst {
      root /var/www/blst;
      index i.html;
   }
   location /blog {
      proxy_pass http://localhost:10100;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection 'upgrade';
      proxy_set_header Host $host;
      proxy_cache_bypass $http_upgrade;	
   }


### Usage

This CMS operates in two folders: the ingest folder and the static folder. The latter must be
exposed for static file serving by e.g. Nginx.

Documents for ingestion must be structured like this:

$ingestDir/topic.subtopic.subsubtopic/
  -- i.html
  -- img.png
  -- littleScript.js

$ingestDir/anotherTopic/
  -- i.html
  -- img.png

Inside i.html, you can link to images, other media and scripts from same folder,
and also to the global Jokescript modules using addresses like `src="/_c/module.js"


The special subfolder "_core" is used to update core built-ins and upload core script modules.

$ingestDir/_core/
  -- core.js
  -- core.css
  -- 404.png

A document must be an HTML file with no styles,
and all scripts must be external references of type="module". These references may either be to adjacent files
(like <script type="module" src="./module.js"/>)
or global script libraries (like <script type="module" src="Library.js"/>, notice the absence of the dot).

In a script file, all imports must be at the start of the file, one line per import.

The CMS allows updating of all data with a delay of 5 minutes.
