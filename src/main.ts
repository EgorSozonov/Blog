import { Request, Response } from "express"

//{{{ Constants

const appSubfolder = "blog/"
const ingestSubfolder: String = "_ingest/"
const ingestCoreSubfolder: String = "_ingestCore/"
const docsSubfolder = "_d/"
const mediaSubfolder = "_m/"
const scriptsSubfolder = "_s/"
const globalsSubfolder = "_g/"
const coreSubfolder = "_core/"
const updateFreq = 300 // seconds before the cache gets rescanned

//}}}
//{{{ Blog core

class Blog {
    constructor(private docCache: DocCache, 
                private navTopic: NavTree,
                private navTime: NavTree,
                private updated: Date
               ){}

    checkNUpdate() {
        const now = new Date()
        if (now - updated < updateFreq) {
            return
        }
        mutex {
            docCache.ingestAndRefresh()
            const navTrees = buildNavTrees(docCache)
            navTopic = navTrees.f1
            navTime = navTrees.f2
            updated = new Date()
        }
    }
}

//}}}
//{{{ Document cache
//}}}
//{{{ Web app

const express = require( "express" );
const app = express();
const port = 8000; // default port to listen

// define a route handler for the default home page
app.get( "/", ( req: Request, res: Response ) => {
    res.send( "Hello world!" );
});

// start the Express server
app.listen( port, () => {
    console.log( `server started at http://localhost:${ port }` );
});

//}}}
//{{{ Utils
//}}}
