package tech.sozonov.blog.templates


object BlogTemplate {
    val template0 = """<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; base-uri 'self';" />"""


    val templateHeadCloseBodyStart = """
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
                                <path d="M 35 20 h 30 c 0 30 -30 30 -30 60 h 30 c 0 -30 -30 -30 -30 -60" />
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
                            <svg class="__swell __sortingBorder" id="_sorterThem" width="30" height="30"
                             viewBox="0 0 100 100">
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
"""

    val template4: String = """</div>
        </div>
    </body>
</html>    
"""


    val serialVersionUID : Long = 234L
}