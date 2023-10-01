//{{{ Package

#![allow(non_snake_case)]
#![allow(unused_imports)]

use axum::{
    routing::{get, post},
    http::StatusCode,
    response::IntoResponse,
    Json, Router,
    extract::Path
};
use serde::{Deserialize, Serialize};
use std::net::SocketAddr;
use std::time::{Duration, Instant};

//}}}
//{{{ Constants

const appSubfolder = "blog/"
const ingestSubfolder = "_ingest/"
const ingestCoreSubfolder = "_ingestCore/"
const docsSubfolder = "_d/"
const mediaSubfolder = "_m/"
const scriptsSubfolder = "_s/"
const globalsSubfolder = "_g/"
const coreSubfolder = "_core/"

//}}}
//{{{ Blog core

struct Blog {
    docCache: &DocCache,
    navTopic: &NavTree,
    navTime: &NavTree,
    updated: Instant,
    rootPath: str
}


struct DocCache {
    cache: HashMap<&str, &Document>,
    scriptCache: HashSet<&str>,
    coreJs: &str,
    coreCss: &str,
    notFound: &Document,
    footer: &Document,
    rootPage: &Document,
    termsUse: &Document
}


struct Document {
    cont: &str,
    scriptDeps: Vec<&str>,
    hasCss: bool,
    pathCaseSen: &str,
    modified: Instant,
    pageId: i32,
    wasAddedDb: bool
}


impl Blog {
    async fn getHandler(&self, subUrl: &str, queryParams: Vec<(&str, &str)>): &str {
        checkNUpdate(rootPath);
        let docContent = if (subUrl.isEmpty()) {
            docCache.rootPage;
        } else if (subUrl.toLower() == "termsofuse") {
            docCache.termsUse;
        } else {
            docCache.getDocument(subUrl);
        }
        if (docContent.is_null()) {
            docContent = docCache.notFound;
        }

        let modeTemporal = queryParams.iter(|&x| x.f1 == "temp");
        self.navTree = if (modeTemporal) {
            self.navTime;
        } else {
            self.navTopic;
        }
        let res: Vec<&str> = Vec::new();
        buildResponse(self, doc, res);
        return res.join();
    }


    fn buildResponse(&self, doc: &Document, res: &mut Vec<&str>) {
        res.push(template0)
        res.push(templateDevice)
        res.push(r#"<link rel="stylesheet" href="/"#)
        res.push(appSubfolder);
        res.push(mediaSubfolder);
        res.push(globalsSubfolder);
        res.push("core.css");

        if (doc.hasCss) {
            res.push(r#"<link rel="stylesheet" href="/"#);
            res.push(appSubfolder);
            res.push(mediaSubfolder);
            res.push(doc.pathCaseSen);
            res.push(r#".css" />"#);
        }
        for scriptDep in doc.scriptDeps.iter() {
            let scriptModule = self.docCache.getModule(scriptDep) else { return ""; };
            res.push(r#"<script type="module" src="/"#);
            res.push(appSubfolder);
            res.push(scriptsSubfolder);
            res.push(scriptModule);
            res.push("></script>\n");
        }
        res.push(r#"<script type="module" src="/"#);
        res.push(appSubfolder);
        res.push(scriptsSubfolder);
        res.push(r#"_g/core.js"></script>"#);
        let breadCrumbs = navTree.createBreadCrumbs(subUrl).joinToString(", ");
        printScriptPart(breadCrumbs, modeTemporal, navTopic, navTime, res);
        res.push(r#"<link rel="icon" type="image/x-icon" href="/"#);
        res.push(appSubfolder);
        res.puhs(mediaSubfolder);
        res.push(r#"favicon.ico"/>"#);
        res.push(templateHeadCloseBodyStart);
        res.push(docContent.cont);
        res.push(self.docCache.footer.cont);
        res.push(template4);
    }

    fn printScriptPart(breadCrumbs: &str, modeTemporal: bool,
                       navTopic: &navTree, navTime: &NavTree, Vec<&str>) {
        let strTopics = navTopic.toJson();
        let strTemp = navTime.toJson();
        res.push(r#"<script type="application/json" id="_navState">{
    "cLoc": ["#);
        res.push(breadCrumbs);
        res.push(",    modeTemp: ");
        res.push(modeTemporal);
        res.push(r#",    "navThematic": [ "#);
        res.push(strTopics);
        res.push(r#",    "navTemporal": ["#);
        res.push(strTemp);
        res.push("}<script>");
    }

    fn checkNUpdate(&self, rootPath: &str) {
        let now = Instant::now();
        let dur = self.updated - now;
        if (dur.minutes() <= 5) {
            return;
        }

        mutex {
            self.docCache.ingestAndRefresh(rootPath);
            let navTrees = buildNavTrees(self.docCache);
            self.navTopic = navTrees.0;
            self.navTime = navTrees.1;
            self.updated = now;
        }
    }

}




//}}}
//{{{ File data source
//}}}
//{{{ Rewriter



//}}}
//{{{ Templates

const template0 = r#"
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; base-uri 'self';" />"#;

const templateHeadCloseBodyStart = r#"
    </head>
    <body>
        <div class="__wrapper">
            <div class="__navbar" id="_theNavBar">
                <div class="__menuTop">
                    <div class="__svgButton" title="Temporal sorting">
                        <a id="_reorderTemporal" title="Temporal sorting">
                            <svg id="_sorterTemp" class="__swell" width="30" height="30"
                                viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30
                                         c 0 -30 -30 -30 -30 -60" />
                            </svg>
                        </a>
                    </div>
                    <div class="__svgButton">
                        <a href="http://sozonov.site" title="Home page">
                            <svg id="__homeIcon" class="__swell" width="30" height="30"
                             viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 30 45 h 40 v 25 h -40 v -25 " />
                                <path d="M 22 50 l 28 -25 l 28 25" />
                            </svg>
                        </a>
                    </div>
                    <div class="__svgButton">
                        <a id="_reorderThematic" title="Thematic sorting">
                            <svg class="__swell __sortingBorder" id="_sorterThem"
                                        width="30" height="30" viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50" />
                                <path d="M 35 80 v -60 l 30 60 v -60" />
                            </svg>
                    </a>
                    </div>
                </div>
                <div class="__menu" id="__theMenu"></div>
            </div>

            <div class="__divider" id="_divider">&lt;</div>
            <div class="__menuToggler __hidden" id="_menuToggler">
                <div class="__svgButton" title="Open menu">
                        <a id="_toggleNavBar">
                            <svg class="__swell" width="30" height="30" viewBox="0 0 100 100">
                                <circle r="48" cx="50" cy="50"></circle>
                                <path d="M 30 35 h 40" stroke-width="6"></path>
                                <path d="M 30 50 h 40" stroke-width="6"></path>
                                <path d="M 30 65 h 40" stroke-width="6"></path>
                            </svg>
                        </a>
                </div>
            </div>

            <div class="__content">
"#;

const templateDevice = r#"<meta name="viewport" content="width=device-width,initial-scale=1">"#;

const template4 = r#"</div>
        </div>
    </body>
</html>
"#;

//}}}
//{{{ Utils

//~    let start = Instant::now();
//~    expensive_function();
//~    let duration = start.elapsed();

//}}}
//{{{ Web handlers

#[tokio::main]
async fn main() {
    // initialize tracing
    tracing_subscriber::fmt::init();

    // build our application with a route
    let app = Router::new()
        .route("/", get(root))
        .route("/foo/*wild", get(wildH))
        .route("/users", post(createUser));

    // run our app with hyper
    // `axum::Server` is a re-export of `hyper::Server`
    let addr = SocketAddr::from(([127, 0, 0, 1], 3000));
    tracing::debug!("listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

//~async fn wildH(Path(wild): Path<String>) -> String {
//~    wild
//~}
//~
//~async fn root() -> &'static str {
//~    "Hello, World!"
//~}
//~
//~async fn createUser(
//~    Json(payload): Json<CreateUser>,
//~) -> (StatusCode, Json<User>) {
//~    // this argument tells axum to parse the request body
//~    // as JSON into a `CreateUser` type
//~    let user = User {
//~        id: 1337,
//~        username: payload.username,
//~    };
//~
//~    // this will be converted into a JSON response
//~    // with a status code of `201 Created`
//~    (StatusCode::CREATED, Json(user))
//~}

//~#[derive(Deserialize)]
//~struct CreateUser {
//~    username: String,
//~}
//~
//~#[derive(Serialize)]
//~struct User {
//~    id: u64,
//~    username: String,
//~}

//}}}
